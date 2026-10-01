# -*- coding: utf-8 -*-
"""
FAN SUPER — Python Meclisi (Chaquopy ile uygulamaya gömülü).
Kotlin tarafı yalnızca bu modüldeki fonksiyonları çağırır; tüm giriş/çıkış JSON metnidir.

HIZLI GERİ ALMA
---------------
Eski sürüm her adımda meclisin TAM derin kopyasını (copy.deepcopy) alıyordu:
bu hem sayı düğmelerini yavaşlatıyor hem de yalnızca 8 adım geriye izin veriyordu
(9. geri almada tüm geçmiş baştan öğreniliyordu → dakikalarca bekleme).

Yeni düzen:
  * Üyeler artık paylaşılan dizileri yerinde değiştirmez, bu yüzden "anlık görüntü"
    yalnızca referans + birkaç küçük kopya tutar (~60 µs, deepcopy'nin ~1/1000'i).
  * Her adımın görüntüsü `_journal` halkasına, her CK_EVERY adımda bir de
    `_checkpoints` (dayanak) listesine yazılır.
  * `undo_to(n)`: hedef günlükte varsa O(1); değilse en yakın dayanağa dönülüp
    en çok CK_EVERY adım ileri sarılır. Tam yeniden öğrenme yalnızca dayanak
    kapsamının dışına çıkılırsa gerekir (Kotlin tarafı bunu yapar).
"""
import json
import math
import time
from collections import deque

import numpy as np

import fan_members as fm
import fan_side as fsd

K = 4

JOURNAL = 32      # adım adım geri alma halkası (canlı girişler için anında undo)
CK_EVERY = 8      # kaç adımda bir dayanak (checkpoint) alınır
CK_MAX = 96       # tutulacak dayanak sayısı (CK_EVERY * CK_MAX kayıt geriye kadar)
ANCHORS = 4       # diske yazılan dayanak sayısı (açılıştan sonra da hızlı undo)


class Rolling:
    def __init__(self, cap):
        self.cap = cap
        self.buf = deque(maxlen=cap)

    def add(self, b):
        self.buf.append(1 if b else 0)

    @property
    def n(self):
        return len(self.buf)

    def rate(self):
        return (sum(self.buf) / len(self.buf)) if self.buf else 0.0

    # ---- geri alma: 100 baytlık kopya, O(cap) ----
    def snap(self):
        return bytes(self.buf)

    def restore(self, b):
        self.buf = deque(bytearray(b), maxlen=self.cap)


class PyCouncil:
    def __init__(self, cfg):
        self.cfg = cfg
        self.members = fm.all_members()
        n = len(self.members)
        self.w = np.full(n, 1.0 / n)
        win = int(cfg.get("window", 100))
        self.r1 = [Rolling(win) for _ in range(n)]
        self.r2 = [Rolling(win) for _ in range(n)]
        self.bench = [False] * n
        self.vals = []
        self.times = []
        self.last_preds = None
        self.last_mix = np.full(K, 0.25)
        self.step_no = 0
        self.last_ms = 0.0
        self.per = []  # her kayıt için, o kayıt gelmeden önceki meclis tahmini
        self._apply_cfg()

    def _apply_cfg(self):
        dl = bool(self.cfg.get("dl", True))
        battery = bool(self.cfg.get("battery", False))
        for m in self.members:
            if hasattr(m, "slow"):
                m.slow = 2 if battery else 1
        self.dl = dl
        self.battery = battery

    def enabled(self, i):
        m = self.members[i]
        if m.id in self.cfg.get("disabled", []):
            return False
        if getattr(m, "dl", False) and not self.dl:
            return False
        return True

    def _hist(self):
        return fm.Hist(np.asarray(self.vals, dtype=np.int64), np.asarray(self.times, dtype=np.int64), len(self.vals))

    def predict(self):
        h = self._hist()
        preds = []
        for i, m in enumerate(self.members):
            if not self.enabled(i):
                preds.append(np.full(K, 0.25))
                continue
            try:
                preds.append(fm.norm(m.predict(h)))
            except Exception:
                preds.append(np.full(K, 0.25))
        self.last_preds = preds
        act = [self.enabled(i) and not self.bench[i] for i in range(len(self.members))]
        if not any(act):
            act = [self.enabled(i) for i in range(len(self.members))]
        mix = np.zeros(K)
        tw = 0.0
        for i, p in enumerate(preds):
            if act[i]:
                mix += self.w[i] * p
                tw += self.w[i]
        self.last_mix = fm.norm(mix / tw) if tw > 0 else np.full(K, 0.25)
        return self.last_mix

    def learn(self, v, t):
        """Gerçek sonucu öğren (predict() önce çağrılmış olmalı)."""
        t0 = time.time()
        preds = self.last_preds
        self.per.append([float(x) for x in self.last_mix])
        self.vals.append(int(v))
        self.times.append(int(t))
        self.step_no += 1
        win = int(self.cfg.get("window", 100))
        thr = float(self.cfg.get("bench", 0.20))
        alpha = float(self.cfg.get("alpha", 0.02))
        if preds is not None:
            nm = len(self.members)
            factor = np.ones(nm)
            bench = list(self.bench)          # yeni liste: eski görüntüler bozulmaz
            for i, p in enumerate(preds):
                o = np.argsort(-p)
                self.r1[i].add(o[0] == v)
                self.r2[i].add(o[0] == v or o[1] == v)
                bench[i] = bool(self.r1[i].n >= win and self.r1[i].rate() < thr)
                factor[i] = math.exp(0.6 * math.log(max(float(p[v]), 1e-9)))
            self.bench = bench
            w = self.w * factor               # yerinde değil: yeni dizi
            s = w.sum()
            if not np.isfinite(s) or s <= 0:
                w = np.full(nm, 1.0 / nm)
            else:
                w = w / s
            self.w = (1 - alpha) * w + alpha / nm
        h = self._hist()
        heavy_ok = (not self.battery) or (self.step_no % 5 == 0)
        for i, m in enumerate(self.members):
            if not self.enabled(i):
                continue
            if getattr(m, "dl", False):
                m.train = heavy_ok
            try:
                m.update(h)
            except Exception:
                pass
        self.last_ms = (time.time() - t0) * 1000.0

    def capture(self):
        """Şu anki durumun UCUZ anlık görüntüsü (büyük tablolar kopyalanmaz)."""
        return {
            "c": self,
            "n": len(self.vals),
            "w": self.w,
            "bench": list(self.bench),
            "rolls": [(a.snap(), b.snap()) for a, b in zip(self.r1, self.r2)],
            "mix": self.last_mix,
            "preds": self.last_preds,
            "step_no": self.step_no,
            "mem": [m.snap() if hasattr(m, "snap") else dict(m.__dict__) for m in self.members],
        }

    def stats(self):
        out = []
        n = len(self.members)
        for i, m in enumerate(self.members):
            out.append({
                "id": m.id, "name": m.name,
                "top1": self.r1[i].rate(), "top2": self.r2[i].rate(),
                "weight": float(self.w[i] * n), "benched": bool(self.bench[i]),
                "enabled": bool(self.enabled(i)), "n": self.r1[i].n,
            })
        return out


# ------------------------------------------------------------------ modül durumu
_council = None
_cfg = {}
_journal = deque(maxlen=JOURNAL)        # her adımın görüntüsü (n = o adımdan ÖNCEKİ kayıt sayısı)
_checkpoints = deque(maxlen=CK_MAX)     # seyrek dayanaklar


def _lst(p):
    return [float(x) for x in p]


def can_restore(s):
    c = s.get("c") if isinstance(s, dict) else None
    if c is None:
        return False
    try:
        for m, ms in zip(c.members, s["mem"]):
            if hasattr(m, "can_restore") and not m.can_restore(ms):
                return False
    except Exception:
        return False
    return True


def _restore(s):
    """capture() görüntüsüne geri dön. Başarı: True."""
    c = s.get("c")
    if c is None:
        return False
    global _council
    try:
        n = int(s["n"])
        del c.vals[n:]
        del c.times[n:]
        del c.per[n:]
        c.w = s["w"]
        c.bench = list(s["bench"])
        for i, (b1, b2) in enumerate(s["rolls"]):
            c.r1[i].restore(b1)
            c.r2[i].restore(b2)
        c.last_mix = s["mix"]
        c.last_preds = s["preds"]
        c.step_no = s["step_no"]
        for m, ms in zip(c.members, s["mem"]):
            if hasattr(m, "restore"):
                if not m.restore(ms):
                    return False
            else:
                m.__dict__.clear()
                m.__dict__.update(ms)
        _council = c
        return True
    except Exception:
        return False


def _log_snap(s):
    _journal.append(s)
    if s["n"] % CK_EVERY == 0:
        _checkpoints.append(s)


def _prune(n):
    """Hedeften daha yeni görüntüleri at."""
    while _journal and _journal[-1]["n"] > n:
        _journal.pop()
    while _checkpoints and _checkpoints[-1]["n"] > n:
        _checkpoints.pop()


def configure(cfg_json):
    global _cfg
    _cfg = json.loads(cfg_json) if cfg_json else {}
    return "ok"


def _r6(a):
    return [round(float(x), 6) for x in a]


def _run_replay(c, vals, times, journal, with_members):
    """Ortak replay döngüsü: her adımda önce tahmin, sonra gerçek sonuç (gelecek sızmaz)."""
    members = []
    for v, t in zip(vals, times):
        c.predict()
        if with_members:
            members.append([_r6(p) for p in c.last_preds])
        if journal:
            _log_snap(c.capture())
        c.learn(int(v), int(t))
    nxt = c.predict()
    return nxt, members


def replay(values_json, times_json, with_members=False):
    """Tüm geçmişi baştan öğren. Dönüş: {"per": [...], "next": [...]}
    with_members=True ise her adım için üye bazında dağılımlar da döner (v1.3 meta-ensemble)."""
    global _council
    vals = json.loads(values_json)
    times = json.loads(times_json)
    _council = PyCouncil(_cfg)
    _journal.clear()
    _checkpoints.clear()
    c = _council
    t0 = time.time()
    nxt, members = _run_replay(c, vals, times, True, bool(with_members))
    c.last_ms = (time.time() - t0) * 1000.0 / max(1, len(vals))
    out = {"per": c.per, "next": _lst(nxt)}
    if with_members:
        out["ids"] = [m.id for m in c.members]
        out["members"] = members
    return json.dumps(out)


def sandbox_replay(values_json, times_json, cfg_json=None):
    """ARAŞTIRMA modu: canlı Python meclisine ve günlüklerine DOKUNMAZ.
    Yeni bir meclis kurar, tüm geçmişi kronolojik oynatır ve üye dağılımlarını döndürür."""
    vals = json.loads(values_json)
    times = json.loads(times_json)
    cfg = json.loads(cfg_json) if cfg_json else dict(_cfg)
    c = PyCouncil(cfg)
    nxt, members = _run_replay(c, vals, times, False, True)
    return json.dumps({"per": c.per, "next": _lst(nxt), "ids": [m.id for m in c.members],
                       "members": members})


def member_info():
    """Canlı meclisin SON tahmin edilen (bir sonraki) üye dağılımları ve ağırlık payları."""
    if _council is None or _council.last_preds is None:
        return "{}"
    c = _council
    act = [c.enabled(i) and not c.bench[i] for i in range(len(c.members))]
    if not any(act):
        act = [c.enabled(i) for i in range(len(c.members))]
    wsum = sum(float(c.w[i]) for i in range(len(c.members)) if act[i]) or 1.0
    return json.dumps({
        "ids": [m.id for m in c.members],
        "names": [m.name for m in c.members],
        "preds": [_r6(p) for p in c.last_preds],
        "share": [float(c.w[i]) / wsum if act[i] else 0.0 for i in range(len(c.members))],
        "active": [bool(a) for a in act],
    })


def _step_core(v, t):
    """step()'in ortak mantığı: bir sonraki tahmini DÜZ PYTHON LİSTESİ olarak döndürür."""
    global _council
    if _council is None:
        _council = PyCouncil(_cfg)
        _council.predict()
    _log_snap(_council.capture())
    _council.learn(int(v), int(t))
    return _lst(_council.predict())


def step(v, t):
    """Yeni kaydı öğren; bir sonraki tahmini JSON dizesi olarak döndür (geriye dönük uyumluluk)."""
    return json.dumps(_step_core(v, t))


def step_fast(v, t):
    """step() ile aynı, ama JSON'a çevirmeden düz liste döndürür.
    Chaquopy Python listelerini otomatik dönüştürdüğü için her tuşlamada
    json.dumps + JSONArray.parse maliyetini ortadan kaldırır (bkz. PythonBridge.kt)."""
    return _step_core(v, t)


def undo():
    """Son kaydı geri al (geriye dönük uyumluluk)."""
    if _council is None:
        return ""
    return undo_to(len(_council.vals) - 1)


def _undo_to_core(n):
    """undo_to()'nun ortak mantığı. Dönüş None ise hedefe ulaşılamadı
    (Kotlin tarafı tam yeniden öğrenmeye düşer); aksi halde düz Python listesi."""
    global _council
    if _council is None:
        return None
    if n is None:
        n = len(_council.vals) - 1
    n = int(n)
    cur = len(_council.vals)
    if n < 0 or n > cur:
        return None
    if n == cur:
        return _lst(_council.last_mix)
    _prune(n)
    # 1) hedef tam olarak günlükte: O(1)
    if _journal and _journal[-1]["n"] == n and can_restore(_journal[-1]):
        if _restore(_journal.pop()):
            return _lst(_council.last_mix)
        return None
    # 2) en yakın dayanak + kısa ileri sarım (en çok CK_EVERY adım)
    while _checkpoints:
        s = _checkpoints.pop()
        if not can_restore(s):
            continue
        tail_v = list(_council.vals[s["n"]:n])
        tail_t = list(_council.times[s["n"]:n])
        if not _restore(s):
            return None
        c = _council
        for v, t in zip(tail_v, tail_t):
            c.predict()
            _log_snap(c.capture())
            c.learn(int(v), int(t))
        return _lst(c.predict())
    return None


def undo_to(n):
    """Kayıt sayısını tam olarak n'e döndür; n. sıradaki (bir sonraki) tahmini JSON
    dizesi olarak döndürür. Dönüş "" ise hedefe ulaşılamadı."""
    r = _undo_to_core(n)
    return "" if r is None else json.dumps(r)


def undo_to_fast(n):
    """undo_to() ile aynı, ama JSON'a çevirmeden döndürür.
    Boş liste [] dönerse hedefe ulaşılamadı demektir (Kotlin tarafı tam yeniden
    öğrenmeye düşer) — gerçek tahminler her zaman K elemanlıdır, hiçbir zaman boş
    olmaz, bu yüzden [] belirsizliğe yer bırakmayan bir "başarısız" işaretidir."""
    r = _undo_to_core(n)
    return [] if r is None else r


def undo_depth():
    """Kaç adımın anında geri alınabileceği (arayüz/teşhis için)."""
    if _council is None:
        return 0
    n = len(_council.vals)
    reach = n
    if _journal:
        reach = min(reach, _journal[0]["n"])
    if _checkpoints:
        reach = min(reach, _checkpoints[0]["n"])
    return n - reach


def stats():
    if _council is None:
        return "[]"
    return json.dumps(_council.stats())


def info():
    if _council is None:
        return json.dumps({"status": "hazırlanıyor"})
    return json.dumps({
        "status": "çalışıyor",
        "ms": round(_council.last_ms, 1),
        "n": len(_council.vals),
        "numpy": np.__version__,
        "dl": _council.dl,
        "undo": "%d adım anında · %d görüntü/%d dayanak" % (undo_depth(), len(_journal), len(_checkpoints)),
    })


def save_state(path):
    """Öğrenilmiş meclisi diske kaydeder (açılışta yeniden öğrenmemek için).

    Dayanaklar (anchors) da yazılır: uygulama yeniden açıldığında ilk geri alma
    bile tüm geçmişi baştan öğrenmek zorunda kalmaz.
    """
    import pickle
    if _council is None:
        return "yok"
    anchors = []
    for s in list(_checkpoints)[-ANCHORS:]:
        if s.get("c") is _council and can_restore(s):
            anchors.append(s)
    with open(path, "wb") as f:
        pickle.dump({"council": _council, "cfg": _cfg, "anchors": anchors}, f, protocol=pickle.HIGHEST_PROTOCOL)
    return "ok"


def load_state(path, values_json, times_json):
    """Kayıtlı durum mevcut veriyle birebir aynıysa yükler. Dönüş: replay() ile aynı biçim ya da ""."""
    global _council
    import os
    import pickle
    if not os.path.exists(path):
        return ""
    try:
        with open(path, "rb") as f:
            d = pickle.load(f)
        c = d["council"]
        vals = json.loads(values_json)
        times = json.loads(times_json)
        iv = [int(x) for x in vals]
        it = [int(x) for x in times]
        if d.get("cfg") != _cfg:
            return ""
        k = len(c.vals)
        # Kayıtlı durum mevcut verinin ÖNEKİ olmalı (v1.3: yalnızca yeni kayıtlar işlenir).
        if k > len(iv) or c.vals != iv[:k] or c.times != it[:k]:
            return ""
        _council = c
        _journal.clear()
        _checkpoints.clear()
        tail = []
        if k < len(iv):
            for v, t in zip(iv[k:], it[k:]):
                c.predict()
                tail.append([_r6(p) for p in c.last_preds])
                _log_snap(c.capture())
                c.learn(int(v), int(t))
        for a in (d.get("anchors") or []):
            try:
                if k == len(iv) and a.get("c") is c and 0 <= int(a["n"]) <= len(c.vals) and can_restore(a):
                    _checkpoints.append(a)
            except Exception:
                pass
        nxt = c.predict()
        return json.dumps({"per": c.per, "next": _lst(nxt), "cached": k, "ids": [m.id for m in c.members],
                           "tail_members": tail})
    except Exception:
        return ""


# =================================================================== v1.3 YAN MECLİSİ
# Yan (Büyük/Küçük + Tek/Çift) tahmini rakamdan türetilmez: ayrı bir meclis, ayrı diziler.
# Kotlin tarafı sayıyı değil, hazır yan kodlarını (bs: 0 KÜÇÜK/1 BÜYÜK, oe: 0 ÇİFT/1 TEK) gönderir.
_side = None
_side_ring = deque(maxlen=JOURNAL)     # undo: adım öncesi küçük görüntüler


def _side_pack():
    per, mix = _side.last, _side.last_mix
    if per is None:
        per, mix = _side.predict()
    d = fsd.pack(_side, per, mix)
    d["ids"] = _side.ids()
    d["names"] = _side.names()
    d["n"] = _side.S.n
    return d


def _side_run(c, bs, oe, ts, collect):
    steps = []
    for i, (b, o) in enumerate(zip(bs, oe)):
        per, mix = c.predict()
        if collect:
            steps.append(fsd.pack(c, per, mix))
        c.learn(int(b), int(o), int(ts[i]) if i < len(ts) else 0)
    return steps


def side_replay(bs_json, oe_json, times_json="[]", sandbox=False):
    """Yan meclisini baştan öğren. sandbox=True ise canlı yan meclisine DOKUNMAZ.
    Dönüş: {"ids", "steps": [adım başına üye dağılımları], "next": {...}}"""
    global _side
    bs = json.loads(bs_json)
    oe = json.loads(oe_json)
    ts = json.loads(times_json) if times_json else []
    c = fsd.SideCouncil()
    steps = _side_run(c, bs, oe, ts, True)
    per, mix = c.predict()
    nxt = fsd.pack(c, per, mix)
    if not sandbox:
        _side = c
        _side_ring.clear()
    return json.dumps({"ids": c.ids(), "names": c.names(), "steps": steps, "next": nxt})


def side_next():
    """Canlı yan meclisinin bir sonraki tahmini (JSON)."""
    if _side is None:
        return ""
    return json.dumps(_side_pack())


def side_step(bs, oe, t=0):
    """Yeni kaydın yan kodlarını öğren; bir sonraki yan tahminini döndür (JSON)."""
    global _side
    if _side is None:
        _side = fsd.SideCouncil()
        _side.predict()
    if _side.last is None:
        _side.predict()
    _side_ring.append(_side.snap())
    _side.learn(int(bs), int(oe), int(t))
    _side.predict()
    return json.dumps(_side_pack())


def side_undo_to(n):
    """Yan meclisini tam n kayda döndür. Dönüş "" ise hedefe ulaşılamadı (Kotlin yeniden öğretir)."""
    global _side
    if _side is None:
        return ""
    n = int(n)
    if n > _side.S.n or n < 0:
        return ""
    if n == _side.S.n:
        return json.dumps(_side_pack())
    while _side_ring and _side_ring[-1]["n"] > n:
        _side_ring.pop()
    if _side_ring and _side_ring[-1]["n"] == n:
        s = _side_ring.pop()
        if _side.restore(s):
            _side.predict()
            return json.dumps(_side_pack())
    return ""


def side_save(path):
    import pickle
    if _side is None:
        return "yok"
    with open(path, "wb") as f:
        pickle.dump({"side": _side}, f, protocol=pickle.HIGHEST_PROTOCOL)
    return "ok"


def side_load(path, bs_json, oe_json):
    """Kayıtlı yan meclisi, mevcut verinin ÖNEKİYSE yükler ve yalnızca yeni kayıtları işler.
    Dönüş: side_next() biçimi (+ "cached") ya da ""."""
    global _side
    import os
    import pickle
    if not os.path.exists(path):
        return ""
    try:
        with open(path, "rb") as f:
            c = pickle.load(f)["side"]
        bs = [int(x) for x in json.loads(bs_json)]
        oe = [int(x) for x in json.loads(oe_json)]
        k = c.S.n
        if k > len(bs) or c.S.bs != bs[:k] or c.S.oe != oe[:k]:
            return ""
        tail = []
        for b, o in zip(bs[k:], oe[k:]):
            per, mix = c.predict()
            tail.append(fsd.pack(c, per, mix))
            c.learn(b, o)
        c.predict()
        _side = c
        _side_ring.clear()
        d = _side_pack()
        d["cached"] = k
        d["tail"] = tail
        return json.dumps(d)
    except Exception:
        return ""
