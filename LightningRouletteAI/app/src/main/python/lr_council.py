# -*- coding: utf-8 -*-
"""
LIGHTNING ROULETTE AI — Python Meclisi (8 üye, yalnızca numpy).

Tüm değerler 0..36 (Avrupa ruleti). Her üye:
    predict(h) -> np.ndarray(37)   h.n kayıt bilinirken SONRAKİ spin için dağılım
    update(h)                      h.n kayıt; son kayıt yeni gelen gerçek sonuç (bağlam = h.v[:n-1])

SIZINTI GÜVENCESİ (Prompt §33): üyeler yalnızca `Hist` görünümünü alır; `Hist.v` = values[:n] bir DİLİMDİR,
yani n'den sonrasını indekslemek IndexError verir — gelecek fiziksel olarak yoktur.
Zaman damgası kullanılmaz (yalnızca sıra), böylece tahmin zamanı ≤ gerçek zaman kuralı kendiliğinden sağlanır.

Meclis durumu geçmişten TÜRETİLİR: `live_predict(values)` önce eksik gözlemleri sırayla öğrenir (catch-up),
sonra tahmin eder. Geri alma (son spin silindi) tek seviye snapshot ile ya da yeniden kurulumla çözülür.
"""
import json
import math
import pickle

import numpy as np

K = 37
WARM = 1500          # yeniden kurulumda en fazla bu kadar son gözlem öğrenilir
FLOOR = 1e-4

ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
POS = np.zeros(K, dtype=int)
POS[ORDER] = np.arange(K)
RED = np.zeros(K, dtype=bool)
RED[[1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]] = True
ORDER_A = np.array(ORDER, dtype=int)

# sektörler: wheel sırasına göre ardışık dilimler (Kotlin tarafı configure() ile gönderir)
_BOUNDS = [0, 5, 9, 13, 17, 21, 25, 29, 33, 37]
SEC = np.zeros(K, dtype=int)
NS = 9
SEC_SIZE = np.ones(9)


def set_sectors(bounds):
    global _BOUNDS, SEC, NS, SEC_SIZE
    b = [int(x) for x in bounds]
    if len(b) < 3 or b[0] != 0 or b[-1] != K or any(b[i] >= b[i + 1] for i in range(len(b) - 1)):
        raise ValueError("sektör sınırları geçersiz")
    _BOUNDS = b
    NS = len(b) - 1
    SEC = np.zeros(K, dtype=int)
    for s in range(NS):
        for i in range(b[s], b[s + 1]):
            SEC[ORDER[i]] = s
    SEC_SIZE = np.array([b[s + 1] - b[s] for s in range(NS)], dtype=float)


set_sectors(_BOUNDS)


def norm(p, floor=FLOOR):
    p = np.asarray(p, dtype=float)
    p = np.where(np.isfinite(p), p, floor)
    p = np.maximum(p, floor)
    return p / p.sum()


def softmax(z):
    z = z - np.max(z)
    e = np.exp(z)
    return e / e.sum()


def sig(x):
    return 1.0 / (1.0 + np.exp(-np.clip(x, -30, 30)))


def sec_to_num(psec):
    """Sektör dağılımı → sayı dağılımı (sektör içinde düzgün)."""
    return psec[SEC] / SEC_SIZE[SEC]


class Hist:
    """Geçmiş görünümü: v[:n]. Dilim olduğu için gelecek erişilemez."""
    __slots__ = ("v", "n")

    def __init__(self, values, n):
        self.v = values[:n]
        self.n = n


# ---------------------------------------------------------------- token özellikleri
TOKF = np.zeros((K, K + 6))
for _n in range(K):
    TOKF[_n, _n] = 1.0
    ang = 2 * math.pi * POS[_n] / K
    TOKF[_n, K] = math.sin(ang)
    TOKF[_n, K + 1] = math.cos(ang)
    TOKF[_n, K + 2] = 0.0 if _n == 0 else (1.0 if RED[_n] else -1.0)
    TOKF[_n, K + 3] = 0.0 if _n == 0 else (1.0 if _n % 2 == 0 else -1.0)
    TOKF[_n, K + 4] = 0.0 if _n == 0 else (1.0 if _n >= 19 else -1.0)
    TOKF[_n, K + 5] = 1.0
TD = K + 6


def ctx_features(h, end):
    """end kayıt bilinirken bağlam özellikleri (GBoost ve Bağlam modeli): yalnızca v[:end]."""
    v = h.v
    f = []
    for j in range(3):
        i = end - 1 - j
        x = int(v[i]) if i >= 0 else 0
        f += [x / 36.0, POS[x] / 36.0, float(SEC[x]), TOKF[x, K + 2], TOKF[x, K + 3], TOKF[x, K + 4]]
    s = np.asarray(v[max(0, end - 200):end], dtype=int)
    secs = SEC[s] if len(s) else np.zeros(0, dtype=int)
    # sektör boşlukları
    for q in range(NS):
        idx = np.nonzero(secs == q)[0]
        g = 40 if len(idx) == 0 else min(40, len(secs) - 1 - idx[-1])
        f.append(min(g, 40) / 10.0)
    r = secs[-30:]
    c = np.bincount(r, minlength=NS) / float(max(1, len(r)))
    f += list(c)
    t = np.asarray(v[max(0, end - 30):end], dtype=int)
    if len(t):
        f += [float(np.mean(TOKF[t, K + 2])), float(np.mean(TOKF[t, K + 3])), float(np.mean(TOKF[t, K + 4]))]
    else:
        f += [0.0, 0.0, 0.0]
    run = 0
    if end >= 1:
        last = SEC[int(v[end - 1])]
        run = 1
        i = end - 2
        while i >= 0 and SEC[int(v[i])] == last and run < 8:
            run += 1
            i -= 1
    f.append(run / 4.0)
    return np.array(f, dtype=float)


FEAT_DIM = 18 + 9 + 9 + 3 + 1   # 40 (NS=9 varsayımıyla); ctx_features boyutu NS'e göre değişebilir


# ---------------------------------------------------------------- 1) LSTM (BPTT)
class LSTM:
    id = "lstm"
    name = "LSTM (BPTT)"

    def __init__(self, H=24, T=10, lr=0.05, seed=3):
        rng = np.random.RandomState(seed)
        self.H, self.T, self.lr = H, T, lr
        D = TD + H
        self.W = rng.uniform(-1, 1, (4 * H, D)) / math.sqrt(D)
        self.b = np.zeros(4 * H)
        self.b[H:2 * H] = 1.0
        self.Wy = rng.uniform(-1, 1, (K, H)) / math.sqrt(H)
        self.by = np.zeros(K)

    def _fwd(self, h, end):
        H = self.H
        hp = np.zeros(H)
        cp = np.zeros(H)
        cache = []
        for i in range(max(0, end - self.T), end):
            x = np.concatenate([TOKF[int(h.v[i])], hp])
            a = self.W @ x + self.b
            ig = sig(a[:H])
            fg = sig(a[H:2 * H])
            og = sig(a[2 * H:3 * H])
            gg = np.tanh(a[3 * H:])
            c = fg * cp + ig * gg
            tc = np.tanh(c)
            hn = og * tc
            cache.append((x, cp, ig, fg, og, gg, c, tc))
            hp, cp = hn, c
        return hp, cache

    def predict(self, h):
        if h.n < 2:
            return np.full(K, 1.0 / K)
        hT, _ = self._fwd(h, h.n)
        return norm(softmax(self.Wy @ hT + self.by))

    def update(self, h):
        end = h.n - 1
        if end < 2:
            return
        H = self.H
        hT, cache = self._fwd(h, end)
        y = softmax(self.Wy @ hT + self.by)
        dy = y.copy()
        dy[int(h.v[end])] -= 1
        dh = self.Wy.T @ dy
        self.Wy = self.Wy - self.lr * np.outer(dy, hT)
        self.by = self.by - self.lr * dy
        dW = np.zeros_like(self.W)
        db = np.zeros_like(self.b)
        dc = np.zeros(H)
        for (x, cp, ig, fg, og, gg, c, tc) in reversed(cache):
            do = dh * tc
            dc = dc + dh * og * (1 - tc * tc)
            di = dc * gg
            df = dc * cp
            dg = dc * ig
            da = np.concatenate([di * ig * (1 - ig), df * fg * (1 - fg), do * og * (1 - og), dg * (1 - gg * gg)])
            dW += np.outer(da, x)
            db += da
            dx = self.W.T @ da
            dh = dx[TD:]
            dc = dc * fg
        np.clip(dW, -1, 1, out=dW)
        np.clip(db, -1, 1, out=db)
        self.W = self.W - self.lr * dW
        self.b = self.b - self.lr * db


# ---------------------------------------------------------------- 2) Mini Transformer (induction head)
class MiniTransformer:
    """Tek başlı dikkat: şu anki bağlama benzeyen geçmiş konumlara dikkat eder, ardından gelen değerleri oylar."""
    id = "transformer"
    name = "Mini Transformer"

    def __init__(self, C=96, lr=0.05, seed=5):
        rng = np.random.RandomState(seed)
        self.C, self.lr = C, lr
        self.D = K + 2 * 9 + 1
        self.M = np.eye(self.D) * 0.5 + rng.normal(0, 0.05, (self.D, self.D))
        self.pb = np.zeros(C)

    def _toks(self, v, idx):
        idx = np.asarray(idx, dtype=int)
        X = np.zeros((len(idx), self.D))
        r = np.arange(len(idx))
        X[r, v[idx]] = 1.0
        m1 = idx - 1 >= 0
        X[r[m1], K + (SEC[v[idx[m1] - 1]] % 9)] = 1.0
        m2 = idx - 2 >= 0
        X[r[m2], K + 9 + (SEC[v[idx[m2] - 2]] % 9)] = 1.0
        X[:, self.D - 1] = 1.0
        return X

    def _attn(self, h, end):
        if end < 4:
            return None
        v = h.v
        q = self._toks(v, [end - 1])[0]
        lo = max(0, end - 1 - self.C)
        idx = np.arange(lo, end - 1)
        if len(idx) == 0:
            return None
        Kx = self._toks(v, idx)
        dist = (end - 2) - idx
        s = Kx @ (self.M.T @ q) + self.pb[dist]
        a = softmax(s)
        Y = np.zeros((len(idx), K))
        Y[np.arange(len(idx)), np.asarray(v[idx + 1], dtype=int)] = 1.0
        p = a @ Y
        return q, Kx, dist, a, Y, p

    def predict(self, h):
        r = self._attn(h, h.n)
        if r is None:
            return np.full(K, 1.0 / K)
        return norm(0.85 * r[5] + 0.15 / K)

    def update(self, h):
        end = h.n - 1
        r = self._attn(h, end)
        if r is None:
            return
        q, Kx, dist, a, Y, p = r
        y = int(h.v[end])
        pa = 0.85 * p[y] + 0.15 / K
        ds = -(0.85 / pa) * a * (Y[:, y] - p[y])
        gM = np.outer(q, ds @ Kx)
        self.M = self.M - self.lr * np.clip(gM, -1, 1)
        upd = np.zeros(self.C)
        np.add.at(upd, np.asarray(dist, dtype=int), -self.lr * ds)
        self.pb = self.pb + upd


# ---------------------------------------------------------------- 3) 1D-CNN (wheel dizisi)
class CNN1D:
    id = "cnn"
    name = "1D-CNN"

    def __init__(self, W=16, F=8, lr=0.03, seed=9):
        rng = np.random.RandomState(seed)
        self.Wn, self.F, self.lr = W, F, lr
        self.C = K + 2
        self.k = rng.normal(0, 0.3, (F, self.C, 3))
        self.kb = np.zeros(F)
        self.Wo = rng.normal(0, 0.1, (K, 2 * F + 1))

    def _x(self, h, end):
        X = np.zeros((self.C, self.Wn))
        lo = max(0, end - self.Wn)
        seg = np.asarray(h.v[lo:end], dtype=int)
        cols = np.arange(self.Wn - len(seg), self.Wn)
        X[seg, cols] = 1.0
        X[K, cols] = TOKF[seg, K]
        X[K + 1, cols] = TOKF[seg, K + 1]
        return X

    def _fwd(self, X):
        Xw = np.lib.stride_tricks.sliding_window_view(X, 3, axis=1)   # (C, L, 3)
        Z = np.einsum("fcw,clw->fl", self.k, Xw) + self.kb[:, None]
        A = np.maximum(Z, 0)
        mi = A.argmax(axis=1)
        feat = np.concatenate([A[np.arange(self.F), mi], A[:, -1], [1.0]])
        return Z, A, mi, feat

    def predict(self, h):
        if h.n < 4:
            return np.full(K, 1.0 / K)
        _, _, _, f = self._fwd(self._x(h, h.n))
        return norm(softmax(self.Wo @ f))

    def update(self, h):
        end = h.n - 1
        if end < 4:
            return
        X = self._x(h, end)
        Z, A, mi, f = self._fwd(X)
        y = softmax(self.Wo @ f)
        dy = y.copy()
        dy[int(h.v[end])] -= 1
        df = self.Wo.T @ dy
        self.Wo = self.Wo - self.lr * np.outer(dy, f)
        L = self.Wn - 2
        k = self.k.copy()
        kb = self.kb.copy()
        for fi in range(self.F):
            for pos, g in ((mi[fi], df[fi]), (L - 1, df[self.F + fi])):
                if Z[fi, pos] > 0 and g != 0:
                    k[fi] -= self.lr * np.clip(g, -1, 1) * X[:, pos:pos + 3]
                    kb[fi] -= self.lr * np.clip(g, -1, 1)
        self.k = k
        self.kb = kb


# ---------------------------------------------------------------- 4) Gradient Boosting (histogram kütükleri)
class GradBoost:
    id = "gboost"
    name = "Gradient Boosting"

    def __init__(self, every=50, rounds=12, lr=0.15, maxn=600, bins=8, lam=5.0, min_leaf=15):
        self.every, self.rounds, self.lr, self.maxn, self.B, self.lam, self.min_leaf = every, rounds, lr, maxn, bins, lam, min_leaf
        self.X = []
        self.y = []
        self.since = 0
        self.base = np.zeros(K)
        self.st = None          # (kk, ff, thr, vL, vR)

    def _fit(self):
        X = np.array(self.X)
        y = np.array(self.y, dtype=int)
        n, F = X.shape
        B = self.B
        Y = np.zeros((n, K))
        Y[np.arange(n), y] = 1.0
        cnt = Y.sum(axis=0)
        base = np.log(cnt + 1.0) - math.log(n + K)
        edges = []
        Xb = np.zeros((n, F), dtype=int)
        for f in range(F):
            e = np.unique(np.quantile(X[:, f], np.linspace(0, 1, B + 1)[1:-1]))
            edges.append(e)
            Xb[:, f] = np.searchsorted(e, X[:, f], side="left")
        flat = (np.arange(F)[None, :] * B + Xb).ravel()
        Fm = np.tile(base, (n, 1))
        kk, ff, thr, vl, vr = [], [], [], [], []
        for _ in range(self.rounds):
            P = np.exp(Fm - Fm.max(axis=1, keepdims=True))
            P /= P.sum(axis=1, keepdims=True)
            for k in range(K):
                g = P[:, k] - Y[:, k]
                hh = P[:, k] * (1 - P[:, k])
                G = np.bincount(flat, weights=np.repeat(g, F), minlength=F * B).reshape(F, B)
                Hs = np.bincount(flat, weights=np.repeat(hh, F), minlength=F * B).reshape(F, B)
                C = np.bincount(flat, minlength=F * B).reshape(F, B)
                GL, HL, CL = np.cumsum(G, 1), np.cumsum(Hs, 1), np.cumsum(C, 1)
                Gt, Ht = GL[:, -1:], HL[:, -1:]
                gain = GL ** 2 / (HL + self.lam) + (Gt - GL) ** 2 / (Ht - HL + self.lam) - Gt ** 2 / (Ht + self.lam)
                ok = (CL >= self.min_leaf) & ((n - CL) >= self.min_leaf)
                ok[:, -1] = False
                gain = np.where(ok, gain, -1.0)
                fb = int(np.argmax(gain))
                f, b = divmod(fb, B)
                if gain[f, b] <= 1e-9:
                    continue
                gl, hl = GL[f, b], HL[f, b]
                a = -gl / (hl + self.lam) * self.lr
                c = -(Gt[f, 0] - gl) / (Ht[f, 0] - hl + self.lam) * self.lr
                e = edges[f]
                t = float(e[b]) if b < len(e) else float("inf")
                Fm[:, k] += np.where(Xb[:, f] <= b, a, c)
                kk.append(k)
                ff.append(f)
                thr.append(t)
                vl.append(a)
                vr.append(c)
        self.base = base
        self.st = (np.array(kk, dtype=int), np.array(ff, dtype=int), np.array(thr), np.array(vl), np.array(vr))

    def predict(self, h):
        if self.st is None or len(self.st[0]) == 0:
            return np.full(K, 1.0 / K)
        x = ctx_features(h, h.n)
        kk, ff, thr, vl, vr = self.st
        if ff.max() >= len(x):
            return np.full(K, 1.0 / K)
        v = np.where(x[ff] <= thr, vl, vr)
        z = self.base + np.bincount(kk, weights=v, minlength=K)
        return norm(softmax(z))

    def update(self, h):
        end = h.n - 1
        if end < 5:
            return
        self.X = (self.X + [ctx_features(h, end).tolist()])[-self.maxn:]
        self.y = (self.y + [int(h.v[end])])[-self.maxn:]
        self.since += 1
        if self.since >= self.every and len(self.y) >= 120:
            self.since = 0
            self._fit()


# ---------------------------------------------------------------- 5) HMM (sektör rejimleri)
class HMM:
    id = "hmm"
    name = "HMM (sektör rejimleri)"

    def __init__(self, S=3, every=50, win=400, seed=1):
        rng = np.random.RandomState(seed)
        self.S, self.every, self.win = S, every, win
        self.A = np.full((S, S), 0.1 / (S - 1)) + np.eye(S) * (0.9 - 0.1 / (S - 1))
        self.B = rng.dirichlet(np.full(NS, 6.0), S)
        self.pi = np.full(S, 1.0 / S)
        self.alpha = self.pi.copy()
        self.obs = []
        self.since = 0

    def _filter(self, obs):
        a = self.pi.copy()
        for o in obs:
            a = (a @ self.A) * self.B[:, o]
            a /= a.sum()
        return a

    def _fit(self):
        o = np.array(self.obs[-self.win:], dtype=int)
        T = len(o)
        S = self.S
        A, B, pi = self.A.copy(), self.B.copy(), self.pi.copy()
        for _ in range(8):
            al = np.zeros((T, S))
            c = np.zeros(T)
            al[0] = pi * B[:, o[0]]
            c[0] = al[0].sum()
            al[0] /= c[0]
            for t in range(1, T):
                al[t] = (al[t - 1] @ A) * B[:, o[t]]
                c[t] = al[t].sum()
                al[t] /= c[t]
            be = np.ones((T, S))
            for t in range(T - 2, -1, -1):
                be[t] = (A @ (B[:, o[t + 1]] * be[t + 1])) / c[t + 1]
            ga = al * be
            ga /= ga.sum(axis=1, keepdims=True)
            xi = np.zeros((S, S))
            for t in range(T - 1):
                m = al[t][:, None] * A * (B[:, o[t + 1]] * be[t + 1])[None, :]
                xi += m / m.sum()
            A = xi + 0.5
            A /= A.sum(axis=1, keepdims=True)
            Bn = np.full((S, NS), 1.0)
            for s in range(NS):
                Bn[:, s] += ga[o == s].sum(axis=0)
            B = Bn / Bn.sum(axis=1, keepdims=True)
            pi = ga[0]
        self.A, self.B, self.pi = A, B, pi
        self.alpha = self._filter(o)

    def predict(self, h):
        psec = (self.alpha @ self.A) @ self.B
        return norm(sec_to_num(psec / psec.sum()))

    def update(self, h):
        end = h.n - 1
        if end < 1:
            return
        o = int(SEC[int(h.v[end])])
        self.obs = (self.obs + [o])[-self.win:]
        self.alpha = (((self.alpha @ self.A) * self.B[:, o]))
        self.alpha = self.alpha / self.alpha.sum()
        self.since += 1
        if self.since >= self.every and len(self.obs) >= 200:
            self.since = 0
            self._fit()


# ---------------------------------------------------------------- 6) kNN-DTW (ofset dizisi benzerliği)
class KnnDtw:
    id = "knn_dtw"
    name = "kNN-DTW"

    def __init__(self, w=8, k=25, maxm=1500):
        self.w, self.k, self.maxm = w, k, maxm
        self.kern = np.exp(-0.5 * (np.minimum(np.arange(K), K - np.arange(K)) / 1.2) ** 2)

    @staticmethod
    def _cd(a, b):
        d = np.abs(a - b)
        return np.minimum(d, K - d) / 18.5

    def predict(self, h):
        n = h.n
        w = self.w
        if n < 60:
            return np.full(K, 1.0 / K)
        p = POS[np.asarray(h.v[max(0, n - self.maxm - w - 2):n], dtype=int)]
        off = (p[1:] - p[:-1]) % K                  # off[t] = p[t+1]-p[t]
        m = len(off)
        q = off[m - w:]
        ends = np.arange(w, m)                       # aday pencere: off[e-w:e], sonraki = off[e]
        if len(ends) < self.k:
            return np.full(K, 1.0 / K)
        idx = ends[:, None] - w + np.arange(w)[None, :]
        Cw = off[idx]                                # (M, w)
        cost = self._cd(q[None, :, None], Cw[:, None, :])   # (M, w, w)
        D = np.full((len(ends), w + 1, w + 1), np.inf)
        D[:, 0, 0] = 0.0
        for i in range(1, w + 1):
            for j in range(1, w + 1):
                D[:, i, j] = cost[:, i - 1, j - 1] + np.minimum(np.minimum(D[:, i - 1, j], D[:, i, j - 1]), D[:, i - 1, j - 1])
        dist = D[:, w, w] / (2 * w)
        kk = min(self.k, len(dist))
        nn = np.argpartition(dist, kk - 1)[:kk]
        scale = float(np.median(dist[nn])) + 1e-6
        wt = np.exp(-dist[nn] / scale)
        hist = np.bincount(off[ends[nn]], weights=wt, minlength=K)
        sm = np.zeros(K)
        for s in range(K):
            sm += hist[s] * np.roll(self.kern, s)
        sm = 0.75 * sm / sm.sum() + 0.25 / K
        poff = sm
        last = int(POS[int(h.v[n - 1])])
        out = np.zeros(K)
        out[ORDER_A[(last + np.arange(K)) % K]] = poff
        return norm(out)

    def update(self, h):
        pass            # sınıfsız: tahmin doğrudan geçmişten (yalnızca h.v[:n]) hesaplanır


# ---------------------------------------------------------------- 7) Bağlam modeli (sektör softmax regresyonu)
class ContextModel:
    id = "context"
    name = "Bağlam modeli"

    def __init__(self, lr=0.03, l2=1e-4, seed=11):
        rng = np.random.RandomState(seed)
        self.lr, self.l2 = lr, l2
        self.d = None
        self.W = None
        self.rng = rng

    def _f(self, h, end):
        base = ctx_features(h, end)
        oh = np.zeros(3 * NS)
        for j in range(3):
            i = end - 1 - j
            if i >= 0:
                oh[j * NS + SEC[int(h.v[i])]] = 1.0
        return np.concatenate([oh, base[18:], [1.0]])

    def _ensure(self, d):
        if self.W is None or self.d != d:
            self.d = d
            self.W = np.zeros((NS, d))

    def predict(self, h):
        if h.n < 5:
            return np.full(K, 1.0 / K)
        x = self._f(h, h.n)
        self._ensure(len(x))
        return norm(sec_to_num(softmax(self.W @ x)))

    def update(self, h):
        end = h.n - 1
        if end < 5:
            return
        x = self._f(h, end)
        self._ensure(len(x))
        y = softmax(self.W @ x)
        y[int(SEC[int(h.v[end])])] -= 1.0
        self.W = self.W - self.lr * (np.outer(y, x) + self.l2 * self.W)


# ---------------------------------------------------------------- 8) Motif keşfi (tekrarlayan sektör motifleri)
class MotifDiscovery:
    id = "motif"
    name = "Motif keşfi"

    def __init__(self, kmax=6, support=8):
        self.kmax, self.support = kmax, support
        self.T = {k: {} for k in range(1, kmax + 1)}
        self.last_len = 0

    def predict(self, h):
        n = h.n
        if n < 3:
            return np.full(K, 1.0 / K)
        s = SEC[np.asarray(h.v[max(0, n - self.kmax):n], dtype=int)]
        for k in range(min(self.kmax, len(s)), 0, -1):
            key = tuple(int(x) for x in s[len(s) - k:])
            c = self.T[k].get(key)
            if c is not None and c.sum() >= self.support:
                self.last_len = k
                p = (c + 0.5) / (c.sum() + 0.5 * NS)
                return norm(0.8 * sec_to_num(p) + 0.2 / K)
        self.last_len = 0
        return np.full(K, 1.0 / K)

    def update(self, h):
        end = h.n - 1
        if end < 1:
            return
        sec = SEC[np.asarray(h.v[max(0, end - self.kmax):end + 1], dtype=int)]
        nxt = int(sec[-1])
        ctxs = sec[:-1]
        for k in range(1, min(self.kmax, len(ctxs)) + 1):
            key = tuple(int(x) for x in ctxs[len(ctxs) - k:])
            d = self.T[k]
            c = d.get(key)
            if c is None:
                c = np.zeros(NS)
            c = c.copy()
            c[nxt] += 1.0
            d[key] = c


def all_members():
    return [LSTM(), MiniTransformer(), CNN1D(), GradBoost(), HMM(), KnnDtw(), ContextModel(), MotifDiscovery()]


# ================================================================= Meclis (durum + senkron + geri alma)
LAM0, LAM_LR, LAM_MIN, LAM_MAX = 0.5, 0.01, 0.02, 0.98


class Council:
    """8 üye + her üye için ÇEVRİMİÇİ öğrenilen shrinkage λ (p = (1-λ)·ham + λ/37). λ yalnızca gerçek sonuç
    açıklandıktan SONRA güncellenir; işe yaramayan üye düzgün dağılıma yaklaşır, aşırı güvenli kalamaz."""

    def __init__(self):
        self.members = all_members()
        self.lam = np.full(len(self.members), LAM0)
        self.pending = None       # (n, ham (8,37)) — son predict'in ham çıktısı
        self.n = 0
        self.tail = ()
        self.snap = None          # (n, tail, blob) — son öğrenmeden ÖNCEKİ durum (tek seviye geri alma)

    @property
    def ids(self):
        return [m.id for m in self.members]

    @property
    def names(self):
        return [m.name for m in self.members]

    def _learn_one(self, values, i):
        pend = self.pending
        if pend is not None and pend[0] == i:
            a = int(values[i])
            raw = pend[1][:, a]
            q = (1 - self.lam) * raw + self.lam / K
            g = (raw - 1.0 / K) / np.maximum(q, 1e-9)           # dL/dλ, L = -ln q
            self.lam = np.clip(self.lam - LAM_LR * np.clip(g, -3.0, 3.0), LAM_MIN, LAM_MAX)
        self.pending = None
        h = Hist(values, i + 1)
        for m in self.members:
            m.update(h)
        self.n = i + 1
        self.tail = tuple(int(x) for x in values[max(0, i + 1 - 8):i + 1])

    def learn_to(self, values, target, keep_snap=True):
        for i in range(self.n, target):
            if keep_snap and i == target - 1:
                self.snap = (self.n, self.tail, self._blob())
            self._learn_one(values, i)

    def predict(self, values, n):
        h = Hist(values, n)
        raw = np.stack([m.predict(h) for m in self.members])
        self.pending = (n, raw)
        lam = self.lam[:, None]
        return (1 - lam) * raw + lam / K

    def _blob(self):
        return pickle.dumps({"m": [m.__dict__ for m in self.members], "lam": self.lam, "pending": self.pending}, protocol=4)

    def _load_blob(self, blob):
        d = pickle.loads(blob)
        for m, dd in zip(self.members, d["m"]):
            m.__dict__.clear()
            m.__dict__.update(dd)
        self.lam = d["lam"]
        self.pending = d["pending"]

    def save(self):
        return pickle.dumps({"v": 2, "n": self.n, "tail": self.tail, "blob": self._blob(), "snap": self.snap}, protocol=4)

    def load(self, data):
        try:
            d = pickle.loads(data)
            if d.get("v") != 2:
                return False
            self._load_blob(d["blob"])
            self.n, self.tail, self.snap = d["n"], tuple(d["tail"]), d.get("snap")
            return True
        except Exception:
            return False

    def reset(self):
        self.members = all_members()
        self.lam = np.full(len(self.members), LAM0)
        self.pending = None
        self.n = 0
        self.tail = ()
        self.snap = None

    def sync(self, values):
        """Durumu `values` ile tutarlı yap: eksik gözlemleri sırayla öğren; tutarsızsa geri al/yeniden kur."""
        n = len(values)
        tail_ok = self.n <= n and self.tail == tuple(int(x) for x in values[max(0, self.n - 8):self.n])
        if self.n > n or not tail_ok:
            sn = self.snap
            if sn is not None and sn[0] == n and sn[1] == tuple(int(x) for x in values[max(0, n - 8):n]):
                self._load_blob(sn[2])
                self.n, self.tail, self.snap = sn[0], sn[1], None
            else:
                self.reset()
                start = max(0, n - WARM)
                self.n = start          # yalnızca son WARM gözlem öğrenilir; üyeler bağlamı v[:i]'den okur
                self.tail = tuple(int(x) for x in values[max(0, start - 8):start])
        self.learn_to(values, n)


# ================================================================= Modül API'si (Kotlin/Chaquopy buradan çağırır)
_C = Council()
_R = {"values": None, "council": None, "i": 0, "end": 0}


def configure(cfg_json):
    cfg = json.loads(cfg_json) if cfg_json else {}
    if "bounds" in cfg:
        set_sectors(cfg["bounds"])
    _C.reset()
    return "OK"


def info():
    return json.dumps({"ids": _C.ids, "names": _C.names, "k": K})


def _arr(values_json):
    return np.asarray(json.loads(values_json), dtype=int)


def live_predict(values_json):
    """Önce eksik gözlemleri öğrenir (sıralı, sızıntısız), sonra SONRAKİ spin için 8 üyenin dağılımını döndürür."""
    v = _arr(values_json)
    _C.sync(v)
    P = _C.predict(v, len(v))
    return json.dumps({"ids": _C.ids, "names": _C.names, "probs": np.round(P, 8).tolist(), "lam": np.round(_C.lam, 4).tolist(), "n": int(len(v))})


def undo_hint():
    return "OK"


def state_save():
    return _C.save()


def state_load(data):
    return bool(_C.load(bytes(data)))


def state_n():
    return int(_C.n)


# ---- LAB replay oturumu: parça parça (iptal/ilerleme için); her adımda yalnızca values[:i] görülür
def replay_begin(values_json, warm_from):
    v = _arr(values_json)
    c = Council()
    w = max(0, int(warm_from))
    c.n = w
    c.tail = tuple(int(x) for x in v[max(0, w - 8):w])
    _R.update({"values": v, "council": c, "i": w, "end": len(v)})
    return int(len(v))


def replay_chunk(record_from, n_steps):
    """i'den n_steps adım ilerler; i >= record_from olanların tahminini float32 bayt olarak döndürür (steps, 8, 37)."""
    v, c, i = _R["values"], _R["council"], _R["i"]
    out = []
    stop = min(_R["end"], i + int(n_steps))
    while i < stop:
        if i >= int(record_from):
            out.append(c.predict(v, i).astype(np.float32))      # v[:i] → i. spin için TAHMİN
        c.learn_to(v, i + 1, keep_snap=False)                    # sonra gerçek i. spin ile ÖĞREN
        i += 1
    _R["i"] = i
    return np.stack(out).astype(np.float32).tobytes() if out else b""


def replay_pos():
    return int(_R["i"])


def selftest():
    rng = np.random.RandomState(0)
    v = rng.randint(0, 37, 300)
    c = Council()
    c.sync(v)
    P = c.predict(v, len(v))
    assert P.shape == (8, 37) and np.all(np.isfinite(P)) and np.allclose(P.sum(axis=1), 1.0)
    return "OK"
