# -*- coding: utf-8 -*-
"""Tüm ekranlarda tutarlı kullanılan örnek veri ve rulet geometrisi.
Değerler yalnızca örnektir; gerçek uygulamada hepsi hesaplanır (Prompt §46: uydurma neden yasaktır)."""
import random

# ── Avrupa ruleti: gerçek fiziksel wheel sırası (saat yönü) — Prompt §2
WHEEL = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
RED = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
N = 37

def col(n):  # 'g' | 'r' | 'b'
    return 'g' if n == 0 else ('r' if n in RED else 'b')

def idx(n): return WHEEL.index(n)

def span(n, k, d='bi'):
    """n sayısının k komşu aralığı (wheel sırasına göre soldan sağa).
    bi: k1/k2/k3 (iki yön)  L: saat yönünün tersi (L1..L3)  R: saat yönü (R1..R3)"""
    i = idx(n)
    rng = range(-k, k + 1) if d == 'bi' else (range(-k, 1) if d == 'L' else range(0, k + 1))
    return [WHEEL[(i + j) % N] for j in rng]

def dist(a, b):
    """a→b: (saat yönü, saat yönü tersi, dairesel en kısa) mesafe — Prompt §2"""
    cw = (idx(b) - idx(a)) % N
    ccw = (idx(a) - idx(b)) % N
    return cw, ccw, min(cw, ccw)

# ── Sektörler (varsayılan: 9 eşit-yakın sektör, wheel sırasına göre; konfigüre edilebilir) — Prompt §3
SECTOR_SPLIT = [(0, 5), (5, 9), (9, 13), (13, 17), (17, 21), (21, 25), (25, 29), (29, 33), (33, 37)]
SECTORS = [WHEEL[a:b] for a, b in SECTOR_SPLIT]
def sector(n):
    i = idx(n)
    return next(s + 1 for s, (a, b) in enumerate(SECTOR_SPLIT) if a <= i < b)

# ── Bölgeler (klasik rulet bölgeleri)
VOISINS = {22, 18, 29, 7, 28, 12, 35, 3, 26, 0, 32, 15, 19, 4, 21, 2, 25}
TIERS = {27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33}
ORPH = {17, 34, 6, 1, 20, 14, 31, 9}
REGION_NAMES = {'V': 'VOISINS', 'T': 'TIERS', 'O': 'ORPHELINS'}
REGION_COL = {'V': '#7986CB', 'T': '#4DB6AC', 'O': '#FFB74D'}
def region(n):
    return 'V' if n in VOISINS else ('T' if n in TIERS else 'O')

# ── Masa (table) — wheel'den bağımsız ayrı koordinat sistemi — Prompt §5, §63
def dozen(n): return 0 if n == 0 else (n - 1) // 12 + 1
def column(n): return 0 if n == 0 else (n - 1) % 3 + 1
def high(n): return n >= 19
def odd(n): return n > 0 and n % 2 == 1

# ── Örnek canlı durum
APP = 'LIGHTNING ROULETTE AI'
VER = 'v1.0'
DATASET = 'DATASET-004'
SPINS = 5214
LAST8 = [17, 2, 31, 0, 14, 8, 21, 4]          # en yeni solda (Prompt §12 örneği)
PRED_ID = 'PRED-0005215'
CHAMP = 'CHAMP v1.0.3'
CHLG = 'CHLG v1.1.0-rc2'
NOW = '02.10.2026 14:32'
CANDS = [  # number, k, dir, rank, calibrated P(exact) %
    dict(n=29, k=2, d='bi', rank=1, p=3.0),
    dict(n=8, k=2, d='bi', rank=2, p=2.9),
    dict(n=21, k=2, d='bi', rank=3, p=2.8),
    dict(n=17, k=3, d='bi', rank=4, p=2.8),
    dict(n=14, k=1, d='bi', rank=5, p=2.7),
]
BASE_EXACT = 100 / 37
TABLE = [  # etiket, seçim, P(kalibre) %, taban %
    ('SİYAH', 'Siyah', 50.2, 18 / 37 * 100),
    ('TEK', 'Tek', 49.6, 18 / 37 * 100),
    ('BÜYÜK', '19–36', 50.9, 18 / 37 * 100),
    ('2. DOZEN', '13–24', 33.8, 12 / 37 * 100),
    ('2. COLUMN', 'Sütun 2', 33.1, 12 / 37 * 100),
]
OOS_N = 1043
SPLIT = (3128, 1043, 1043)  # train / validation / OOS (kronolojik) — Prompt §22

# ── Metrik: (ad, isabet%, taban%, CI düşük, CI yüksek) — OOS n=1043
OOS = [
    ('Exact Hit (rank-1)', 2.97, 2.70, 2.0, 4.1),
    ('Candidate-3 Hit', 8.34, 8.11, 6.8, 10.1),
    ('Candidate-4 Hit', 10.74, 10.81, 9.0, 12.7),
    ('Candidate-5 Hit', 13.81, 13.51, 11.8, 16.0),
    ('Neighbor Hit', 60.2, 59.5, 57.2, 63.2),
    ('Sector Hit', 12.2, 11.9, 10.3, 14.3),
    ('Region Hit', 36.5, 36.0, 33.6, 39.5),
    ('Color Hit', 49.1, 48.65, 46.0, 52.2),
    ('Parity Hit', 48.6, 48.65, 45.6, 51.7),
    ('High/Low Hit', 49.5, 48.65, 46.4, 52.6),
    ('Dozen Hit', 33.2, 32.43, 30.4, 36.1),
    ('Column Hit', 32.5, 32.43, 29.7, 35.4),
]

KOTLIN = [  # ad, kısa açıklama, ağırlık, cand5 Δ pp, log-skor kazancı, durum
    ('Wheel Engine', 'dairesel mesafe · yerel yoğunluk', .11, +0.1, -0.0009, 'on'),
    ('Sector Engine', 'sektör frekans · geçiş · entropi', .14, +0.3, -0.0004, 'on'),
    ('Region Engine', 'bölge kalıcılığı · dwell/return', .08, -0.2, -0.0016, 'bench'),
    ('Neighbor Engine', 'k1/k2/k3 · sol/sağ yön', .15, +0.2, -0.0006, 'on'),
    ('Frequency Engine', 'global · recent · rolling · decay', .10, -0.1, -0.0012, 'on'),
    ('Pattern Engine (Kalıp 2.0)', 'exact / karışık dizi', .12, +0.4, -0.0003, 'on'),
    ('Transition Engine', '1. · 2. · 3. derece', .16, +0.3, -0.0005, 'on'),
    ('Kotlin ML (GRU · ESN)', 'özellik grupları ayrı test', .14, +0.2, -0.0007, 'on'),
]
PYTHON = [
    ('LSTM (BPTT)', 'raw history + wheel', .15, +0.1, -0.0008, 'on'),
    ('Mini Transformer', 'dikkat · induction head', .13, +0.2, -0.0007, 'on'),
    ('1D-CNN', 'wheel dizisi', .12, -0.2, -0.0015, 'bench'),
    ('Gradient Boosting', 'tablo + wheel özellikleri', .17, +0.5, -0.0002, 'on'),
    ('HMM', 'rejim durumları', .12, +0.0, -0.0010, 'on'),
    ('kNN-DTW', 'dizi benzerliği', .10, -0.1, -0.0013, 'on'),
    ('Bağlam modeli', 'sektör bağlamı', .11, +0.2, -0.0006, 'on'),
    ('Motif keşfi', 'tekrarlayan motifler', .10, +0.1, -0.0009, 'on'),
]

EXPS = [  # id, hipotez, ayar, N, Δ pp, sınıf, durum
    ('LAB-2026-0001848', 'Region transition (3. derece) + recent', 'w200 · k2 · c5 · bi', '—', '—', '', 'QUEUED'),
    ('LAB-2026-0001847', 'Neighbor k3 + ML (GBoost)', 'w150 · k3 · c5 · bi', '—', '—', '', 'RUNNING'),
    ('LAB-2026-0001846', 'FULL model ablation turu', 'w100 · k2 · c5 · bi', '1 043', '+0,3', 'C', 'DONE'),
    ('LAB-2026-0001845', 'Frequency decay 0,02', 'w75 · k1 · c5 · bi', '1 043', '−0,4', 'D', 'DONE'),
    ('LAB-2026-0001844', 'Pattern (mixed, len 4)', 'w300 · k2 · c4 · bi', '1 043', '+2,1', 'O', 'DONE'),
    ('LAB-2026-0001843', 'Frequency (future bucket)', 'w50 · k2 · c5 · bi', '1 043', '+6,4', 'L', 'REJECTED'),
    ('LAB-2026-0001842', 'Sector transition + k2', 'w150 · k2 · c5 · bi', '1 043', '+0,3', 'C', 'DONE'),
    ('LAB-2026-0001841', 'Wheel+Table agreement', 'w100 · k2 · c5 · bi', '212', '+1,9', 'S', 'DONE'),
    ('LAB-2026-0001840', 'ML GBoost (wheel+neighbor)', 'w200 · k2 · c5 · bi', '1 043', '+0,8', 'B', 'DONE'),
    ('LAB-2026-0001839', 'Region transition (2. derece)', 'w100 · k1 · c3 · R', '1 043', '−1,1', 'F', 'DONE'),
]
CLS_NAMES = {'A': 'güçlü / tekrarlanabilir', 'B': 'umut verici', 'C': 'nötr', 'D': 'zayıf', 'F': 'başarısız',
             'O': 'overfit şüphesi', 'L': 'leakage şüphesi', 'S': 'yetersiz sample'}

# ── biçim yardımcıları (Türkçe ondalık virgül)
MINUS = '−'
def f(x, d=1):
    s = f'{abs(x):.{d}f}'.replace('.', ',')
    return (MINUS if x < 0 else '') + s
def pc(x, d=0): return '%' + f(x, d)
def sg(x, d=1, unit=' pp'):
    return ('+' if x > 0 else (MINUS if x < 0 else '±')) + f(abs(x), d) + unit
def th(n):  # binlik ayraç: 5 214
    return f'{n:,}'.replace(',', ' ')

def rng(seed): return random.Random(seed)

def walk(n, sd, seed, drift=0.0):
    r = rng(seed); v = 0.0; out = []
    for _ in range(n):
        v += r.gauss(drift, sd); out.append(v)
    return out
