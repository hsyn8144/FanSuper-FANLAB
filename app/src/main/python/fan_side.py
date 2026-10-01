# -*- coding: utf-8 -*-
"""
FAN SUPER v1.3 — Python YAN (Büyük/Küçük + Tek/Çift) analiz meclisi.

Yan tahmini rakamdan TÜRETİLMEZ: bu modül yalnızca iki ayrı yan dizisini öğrenir.

    bs  : 0 = KÜÇÜK (SMALL), 1 = BÜYÜK (BIG)
    oe  : 0 = ÇİFT  (EVEN),  1 = TEK   (ODD)
    comb: bs * 2 + oe   (0 SMALL_EVEN, 1 SMALL_ODD, 2 BIG_EVEN, 3 BIG_ODD)

Üç üye, her biri üç ekseni (bs, oe, comb) ayrı ayrı tahmin eder:
    * SideKalip  — Kalıp analiz 2.0 (en çok 1 farkla bulanık eşleşme + ayna) yan dizileri üzerinde.
                   Hem Büyük/Küçük hem Tek/Çift ekseninde ve birleşik dizide çalışır.
    * SideNgram  — geri çekilmeli (backoff) n-gram geçiş tabloları.
    * SideLogit  — seri/pencere/çapraz-eksen özellikleriyle çevrimiçi lojistik/softmax regresyon.
                   Seri (streak) yalnızca öğrenilmiş ağırlık kadar etkilidir; "artık kesin döner" yok.

Tüm durum küçüktür (tablolar + ağırlıklar); adım başına anlık görüntü ucuzdur (undo için).
Gelecek veriye erişim yoktur: üyeler yalnızca tahmin anında bilinen diziyi görür; gerçek sonuç
learn() ile tahminden SONRA eklenir.
"""
import copy
import math

import numpy as np

AXES = ("bs", "oe", "comb")
KAX = {"bs": 2, "oe": 2, "comb": 4}


def _norm(p, floor=1e-4):
    p = np.asarray(p, dtype=float)
    p = np.where(np.isfinite(p), p, floor)
    p = np.maximum(p, floor)
    return p / p.sum()


class SideSeq:
    """Bilinen yan dizileri (yalnızca geçmiş)."""

    def __init__(self):
        self.bs = []
        self.oe = []
        self.cb = []
        self.t = []

    @property
    def n(self):
        return len(self.bs)

    def seq(self, axis):
        return self.bs if axis == "bs" else self.oe if axis == "oe" else self.cb

    def append(self, bs, oe, t=0):
        self.bs.append(int(bs))
        self.oe.append(int(oe))
        self.cb.append(int(bs) * 2 + int(oe))
        self.t.append(int(t))

    def truncate(self, n):
        del self.bs[n:]
        del self.oe[n:]
        del self.cb[n:]
        del self.t[n:]


class SideKalip:
    """Kalıp analiz 2.0 — yan dizileri üzerinde bulanık (1 fark) + ayna eşleşme."""
    id = "py_side_kalip"
    name = "Kalıp 2.0 yan"

    LENS = {"bs": range(4, 15), "oe": range(4, 15), "comb": range(3, 9)}

    def predict(self, S, regime=0):
        out = {}
        for ax in AXES:
            out[ax] = self._axis(np.asarray(S.seq(ax), dtype=np.int8), KAX[ax], self.LENS[ax])
        return out

    @staticmethod
    def _axis(x, k, lens):
        n = len(x)
        if n < 6:
            return np.full(k, 1.0 / k)
        votes = np.zeros(k)
        for L in lens:
            if n <= L + 1:
                break
            s = x[n - L:]
            W = np.lib.stride_tricks.sliding_window_view(x[:n - 1], L)
            fol = x[L:n][:W.shape[0]]
            tol = 1 if L >= 6 else 0
            mis = (W != s).sum(axis=1)
            ok = mis <= tol
            if ok.any():
                w = (L ** 1.5) * np.where(mis[ok] == 0, 1.0, 0.4)
                np.add.at(votes, fol[ok], w)
            mir = (W != ((k - 1) - s)).sum(axis=1)
            ok2 = mir <= tol
            if ok2.any():
                w2 = 0.5 * (L ** 1.5) * np.where(mir[ok2] == 0, 1.0, 0.4)
                np.add.at(votes, (k - 1) - fol[ok2], w2)
        tot = votes.sum()
        if tot <= 0:
            return np.full(k, 1.0 / k)
        return _norm((votes + 0.2 * tot / k) / (1.2 * tot))

    def update(self, S, actual):
        pass

    def snap(self):
        return None

    def restore(self, s):
        return True


class SideNgram:
    """Geri çekilmeli n-gram geçiş tabloları (transition probability / matrix)."""
    id = "py_side_ngram"
    name = "N-gram yan"
    MAXO = {"bs": 5, "oe": 5, "comb": 3}
    LAM = 3.0

    def __init__(self):
        self.T = {ax: [np.zeros((KAX[ax] ** o, KAX[ax])) for o in range(0, self.MAXO[ax] + 1)] for ax in AXES}

    @staticmethod
    def _ctx(x, o, k):
        idx = 0
        for j in range(len(x) - o, len(x)):
            idx = idx * k + int(x[j])
        return idx

    def predict(self, S, regime=0):
        out = {}
        for ax in AXES:
            k = KAX[ax]
            x = S.seq(ax)
            tabs = self.T[ax]
            p = np.full(k, 1.0 / k)
            for o in range(0, self.MAXO[ax] + 1):
                if len(x) < o:
                    break
                c = tabs[o][self._ctx(x, o, k) if o else 0]
                tot = c.sum()
                p = (c + self.LAM * p) / (tot + self.LAM)
            out[ax] = _norm(p)
        return out

    def update(self, S, actual):
        for ax in AXES:
            k = KAX[ax]
            x = S.seq(ax)
            a = int(actual[ax])
            for o in range(0, self.MAXO[ax] + 1):
                if len(x) < o:
                    break
                self.T[ax][o][self._ctx(x, o, k) if o else 0, a] += 1.0

    def snap(self):
        return {ax: [t.copy() for t in tabs] for ax, tabs in self.T.items()}

    def restore(self, s):
        self.T = {ax: [t.copy() for t in tabs] for ax, tabs in s.items()}
        return True


class SideLogit:
    """Çevrimiçi lojistik/softmax regresyon. Seri uzunluğu yalnızca bir özelliktir."""
    id = "py_side_logit"
    name = "Lojistik yan"
    F = 19
    LR = 0.02
    L2 = 1e-3

    def __init__(self):
        self.w = {"bs": np.zeros(self.F), "oe": np.zeros(self.F), "comb": np.zeros((4, self.F))}
        self._f = None
        self._n = -1

    @staticmethod
    def _run(x):
        if not x:
            return 0.0
        r = 1
        i = len(x) - 2
        while i >= 0 and x[i] == x[-1]:
            r += 1
            i -= 1
        return min(r, 8) / 8.0 * (1.0 if x[-1] == 1 else -1.0)

    @staticmethod
    def _flip(x, w=30):
        t = x[-w:]
        if len(t) < 2:
            return 0.0
        return sum(1 for a, b in zip(t[:-1], t[1:]) if a != b) / (len(t) - 1) - 0.5

    @staticmethod
    def _mean(x, w):
        t = x[-w:]
        return ((sum(t) / len(t)) - 0.5) * 2.0 if t else 0.0

    def feats(self, S):
        f = np.zeros(self.F)
        f[0] = 1.0
        i = 1
        for x in (S.bs, S.oe):
            for j in range(1, 6):
                f[i] = (1.0 if x[-j] == 1 else -1.0) if len(x) >= j else 0.0
                i += 1
        f[11] = self._run(S.bs)
        f[12] = self._run(S.oe)
        f[13] = self._mean(S.bs, 10)
        f[14] = self._mean(S.bs, 30)
        f[15] = self._mean(S.oe, 10)
        f[16] = self._mean(S.oe, 30)
        f[17] = self._flip(S.bs)
        f[18] = self._flip(S.oe)
        return f

    def predict(self, S, regime=0):
        f = self.feats(S)
        self._f = f
        self._n = S.n
        out = {}
        for ax in ("bs", "oe"):
            z = float(np.clip(self.w[ax] @ f, -6, 6))
            p1 = 1.0 / (1.0 + math.exp(-z))
            out[ax] = _norm([1.0 - p1, p1])
        z = self.w["comb"] @ f
        e = np.exp(z - z.max())
        out["comb"] = _norm(e / e.sum())
        return out

    def update(self, S, actual):
        f = self._f if (self._f is not None and self._n == S.n) else self.feats(S)
        for ax in ("bs", "oe"):
            z = float(np.clip(self.w[ax] @ f, -6, 6))
            p1 = 1.0 / (1.0 + math.exp(-z))
            g = (p1 - int(actual[ax])) * f + self.L2 * self.w[ax]
            self.w[ax] = self.w[ax] - self.LR * g
        z = self.w["comb"] @ f
        e = np.exp(z - z.max())
        p = e / e.sum()
        y = np.zeros(4)
        y[int(actual["comb"])] = 1.0
        self.w["comb"] = self.w["comb"] - self.LR * (np.outer(p - y, f) + self.L2 * self.w["comb"])

    def snap(self):
        return {ax: w.copy() for ax, w in self.w.items()}

    def restore(self, s):
        self.w = {ax: w.copy() for ax, w in s.items()}
        self._f = None
        self._n = -1
        return True


class SideCouncil:
    """Üç yan üyesi + eksen başına Fixed-Share Hedge (Python Yan Analizi)."""

    ETA = 0.5
    ALPHA = 0.02

    def __init__(self):
        self.S = SideSeq()
        self.members = [SideKalip(), SideNgram(), SideLogit()]
        n = len(self.members)
        self.w = {ax: np.full(n, 1.0 / n) for ax in AXES}
        self.last = None       # son predict() çıktısı (üye bazında)
        self.last_mix = None

    def ids(self):
        return [m.id for m in self.members]

    def names(self):
        return [m.name for m in self.members]

    def predict(self, regime=0):
        per = []
        for m in self.members:
            try:
                d = m.predict(self.S, regime)
            except Exception:
                d = {ax: np.full(KAX[ax], 1.0 / KAX[ax]) for ax in AXES}
            per.append(d)
        mix = {}
        for ax in AXES:
            acc = np.zeros(KAX[ax])
            for i, d in enumerate(per):
                acc += self.w[ax][i] * d[ax]
            mix[ax] = _norm(acc / self.w[ax].sum())
        self.last = per
        self.last_mix = mix
        return per, mix

    def learn(self, bs, oe, t=0):
        """Gerçek sonucu öğren (önce predict() çağrılmış olmalı)."""
        if self.last is None:
            self.predict()
        actual = {"bs": int(bs), "oe": int(oe), "comb": int(bs) * 2 + int(oe)}
        for ax in AXES:
            n = len(self.members)
            fac = np.array([max(float(self.last[i][ax][actual[ax]]), 1e-9) for i in range(n)]) ** self.ETA
            w = self.w[ax] * fac
            s = w.sum()
            w = np.full(n, 1.0 / n) if (not np.isfinite(s) or s <= 0) else w / s
            self.w[ax] = (1 - self.ALPHA) * w + self.ALPHA / n
        for m in self.members:
            try:
                m.update(self.S, actual)
            except Exception:
                pass
        self.S.append(bs, oe, t)
        self.last = None

    def snap(self):
        return {"n": self.S.n, "w": {ax: w.copy() for ax, w in self.w.items()},
                "m": [m.snap() for m in self.members]}

    def restore(self, s):
        self.S.truncate(int(s["n"]))
        self.w = {ax: w.copy() for ax, w in s["w"].items()}
        for m, ms in zip(self.members, s["m"]):
            if ms is not None and not m.restore(ms):
                return False
        self.last = None
        return True


def pack(council, per, mix, digits=6):
    """JSON'a uygun, küçük çıktı biçimi."""
    def r(a):
        return [round(float(x), digits) for x in a]
    return {
        "members": [{"bs": r(d["bs"]), "oe": r(d["oe"]), "comb": r(d["comb"])} for d in per],
        "mix": {"bs": r(mix["bs"]), "oe": r(mix["oe"]), "comb": r(mix["comb"])},
    }
