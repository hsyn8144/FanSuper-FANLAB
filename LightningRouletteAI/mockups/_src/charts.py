# -*- coding: utf-8 -*-
"""SVG çizimleri: wheel, masa, grafikler. Tüm renkler koyu tema ile uyumlu."""
import math
from data import *

RANKC = ['#FFC531', '#4FC3F7', '#81C784', '#E1BEE7', '#FF8A65']
FILL = {'r': '#C62828', 'b': '#1B1D26', 'g': '#2E7D32'}

def pol(cx, cy, r, deg):
    a = math.radians(deg - 90)
    return cx + r * math.cos(a), cy + r * math.sin(a)

def wedge(cx, cy, r0, r1, a0, a1):
    x0, y0 = pol(cx, cy, r1, a0); x1, y1 = pol(cx, cy, r1, a1)
    x2, y2 = pol(cx, cy, r0, a1); x3, y3 = pol(cx, cy, r0, a0)
    big = 1 if (a1 - a0) % 360 > 180 else 0
    return (f'M{x0:.2f},{y0:.2f} A{r1:.2f},{r1:.2f} 0 {big} 1 {x1:.2f},{y1:.2f} '
            f'L{x2:.2f},{y2:.2f} A{r0:.2f},{r0:.2f} 0 {big} 0 {x3:.2f},{y3:.2f} Z')

# ───────────────────────── Fiziksel wheel ─────────────────────────
def svg_wheel(size=340, cands=None, last=None, recent=None, sectors=False, regions=False,
              hub=None, margin=34, ring=38, labels=True, label_fs=None, hilite=None):
    """cands: [(n,k,rank)]; last: son sonuç (beyaz halka); recent: [n...] (1=en yeni);
    hub: (satır1, satır2, renk) merkez metni; hilite: [n] ek vurgu."""
    cx = cy = size / 2
    R = size / 2 - margin
    r_in = R - ring
    step = 360 / N
    fs = label_fs or max(8.5, ring * 0.27)
    o = [f'<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}" xmlns="http://www.w3.org/2000/svg">']
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{R + 2}" fill="#0B1220" stroke="#2A3B5C" stroke-width="1.5"/>')
    for i, n in enumerate(WHEEL):
        a0, a1 = i * step - step / 2, i * step + step / 2
        o.append(f'<path d="{wedge(cx, cy, r_in, R, a0, a1)}" fill="{FILL[col(n)]}" stroke="#0F1726" stroke-width="1"/>')
        tx, ty = pol(cx, cy, (r_in + R) / 2 + 0.5, i * step)
        o.append(f'<text x="{tx:.1f}" y="{ty + fs * 0.35:.1f}" font-size="{fs:.1f}" font-weight="700" fill="#fff" text-anchor="middle">{n}</text>')
    if regions:
        for i, n in enumerate(WHEEL):
            a0, a1 = i * step - step / 2, i * step + step / 2
            o.append(f'<path d="{wedge(cx, cy, r_in - 5, r_in - 1, a0, a1)}" fill="{REGION_COL[region(n)]}"/>')
    if sectors:
        for si, (a, b) in enumerate(SECTOR_SPLIT):
            ang = a * step - step / 2
            x0, y0 = pol(cx, cy, r_in - 8, ang); x1, y1 = pol(cx, cy, R + 3, ang)
            o.append(f'<line x1="{x0:.1f}" y1="{y0:.1f}" x2="{x1:.1f}" y2="{y1:.1f}" stroke="#E6EDF6" stroke-opacity=".75" stroke-width="1.6"/>')
            mid = ((a + b - 1) / 2) * step
            tx, ty = pol(cx, cy, r_in - 24, mid)
            o.append(f'<text x="{tx:.1f}" y="{ty + 3.5:.1f}" font-size="10" font-weight="700" fill="#8AA3C4" text-anchor="middle">S{si + 1}</text>')
    # komşu aralık bantları + aday halkaları
    for (n, k, rank) in (cands or []):
        i = idx(n); c = RANKC[(rank - 1) % 5]
        off = 4 + (rank - 1) * 3.6
        a0, a1 = (i - k) * step - step / 2, (i + k) * step + step / 2
        o.append(f'<path d="{wedge(cx, cy, R + off, R + off + 2.8, a0, a1)}" fill="{c}" fill-opacity=".95"/>')
    for (n, k, rank) in (cands or []):
        i = idx(n); c = RANKC[(rank - 1) % 5]
        a0, a1 = i * step - step / 2, i * step + step / 2
        o.append(f'<path d="{wedge(cx, cy, r_in, R, a0, a1)}" fill="none" stroke="{c}" stroke-width="2.6"/>')
        bx, by = pol(cx, cy, R + 24, i * step)
        o.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="8.5" fill="{c}" stroke="#0F1726" stroke-width="1.5"/>'
                 f'<text x="{bx:.1f}" y="{by + 3.6:.1f}" font-size="10.5" font-weight="900" fill="#1A1400" text-anchor="middle">{rank}</text>')
    for n in (hilite or []):
        i = idx(n); a0, a1 = i * step - step / 2, i * step + step / 2
        o.append(f'<path d="{wedge(cx, cy, r_in, R, a0, a1)}" fill="none" stroke="#fff" stroke-width="2" stroke-dasharray="3 2"/>')
    if last is not None:
        i = idx(last); a0, a1 = i * step - step / 2, i * step + step / 2
        o.append(f'<path d="{wedge(cx, cy, r_in - 1, R + 1, a0, a1)}" fill="none" stroke="#FFFFFF" stroke-width="2.4"/>')
    if recent:
        for j, n in enumerate(recent):
            x, y = pol(cx, cy, r_in - 12, idx(n) * step)
            o.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="7" fill="#E6EDF6" fill-opacity="{1 - j * 0.09:.2f}" stroke="#0F1726" stroke-width="1"/>'
                     f'<text x="{x:.1f}" y="{y + 3:.1f}" font-size="8.5" font-weight="700" fill="#0F1726" text-anchor="middle">{j + 1}</text>')
    hr = r_in - (34 if (recent or sectors) else 10)
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{hr:.1f}" fill="#111B2E" stroke="#22324F" stroke-width="1.5"/>')
    if hub:
        l1, l2, c = hub
        o.append(f'<text x="{cx}" y="{cy - 3}" font-size="{max(14, hr * 0.42):.0f}" font-weight="900" fill="{c}" text-anchor="middle">{l1}</text>'
                 f'<text x="{cx}" y="{cy + 14}" font-size="9.5" fill="#8AA3C4" text-anchor="middle">{l2}</text>')
    o.append('</svg>')
    return ''.join(o)

# ───────────────────────── Masa (table) ─────────────────────────
def svg_table(w=340, picks=True, recent=None, last=None, probs=True):
    """Dikey masa: solda dış bahisler, dozen sütunu, 3×12 sayı ızgarası, altta 2:1 sütun bahisleri."""
    cw, ch = 60, 27
    ox, dz = 78, 34                      # dış bahis sütunu genişliği, dozen genişliği
    W = ox + dz + 3 * cw + 8
    zero_h, bot_h = 30, 28
    H = zero_h + 12 * ch + bot_h + 8
    x0 = ox + dz + 4
    o = [f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}" xmlns="http://www.w3.org/2000/svg">']
    GOLD = '#FFC531'
    def rect(x, y, w_, h_, fill, stroke='#0F1726', sw=1.2, rx=4, extra=''):
        return f'<rect x="{x}" y="{y}" width="{w_}" height="{h_}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>'
    # sıfır
    o.append(rect(x0, 2, 3 * cw, zero_h - 2, FILL['g']))
    o.append(f'<text x="{x0 + 1.5 * cw}" y="{2 + (zero_h - 2) / 2 + 5}" font-size="14" font-weight="900" fill="#fff" text-anchor="middle">0</text>')
    for r in range(12):
        for c in range(3):
            n = 3 * r + c + 1
            x, y = x0 + c * cw, zero_h + r * ch
            o.append(rect(x, y, cw, ch, FILL[col(n)], stroke='#0F1726'))
            o.append(f'<text x="{x + cw / 2}" y="{y + ch / 2 + 4.5}" font-size="13" font-weight="700" fill="#fff" text-anchor="middle">{n}</text>')
    # dozen
    dz_lab = [('1. 12', 0), ('2. 12', 1), ('3. 12', 2)]
    for lab, d in dz_lab:
        y = zero_h + d * 4 * ch
        hl = picks and d == 1
        o.append(rect(ox + 2, y, dz, 4 * ch, '#1A2A47', stroke=GOLD if hl else '#2A3B5C', sw=2.4 if hl else 1.2))
        o.append(f'<text transform="translate({ox + 2 + dz / 2 + 4},{y + 2 * ch}) rotate(-90)" font-size="11" font-weight="700" fill="{GOLD if hl else "#B7C4D8"}" text-anchor="middle">{lab}</text>')
    # dış bahisler (her biri 2 satır)
    outs = [('1–18', '#1A2A47', False), ('ÇİFT', '#1A2A47', False), ('KIRMIZI', FILL['r'], False),
            ('SİYAH', FILL['b'], True), ('TEK', '#1A2A47', True), ('19–36', '#1A2A47', True)]
    for j, (lab, fillc, hl) in enumerate(outs):
        y = zero_h + j * 2 * ch
        hl = picks and hl
        o.append(rect(0, y, ox - 4, 2 * ch, fillc, stroke=GOLD if hl else '#2A3B5C', sw=2.4 if hl else 1.2))
        o.append(f'<text x="{(ox - 4) / 2}" y="{y + ch + 4}" font-size="11" font-weight="700" fill="{GOLD if hl and fillc.startswith("#1A") else "#fff"}" text-anchor="middle">{lab}</text>')
    # 2:1 sütunlar
    for c in range(3):
        hl = picks and c == 1
        y = zero_h + 12 * ch + 4
        o.append(rect(x0 + c * cw, y, cw, bot_h - 4, '#1A2A47', stroke=GOLD if hl else '#2A3B5C', sw=2.4 if hl else 1.2))
        o.append(f'<text x="{x0 + c * cw + cw / 2}" y="{y + 15}" font-size="11" font-weight="700" fill="{GOLD if hl else "#B7C4D8"}" text-anchor="middle">Sütun {c + 1}</text>')
    # son sonuçlar
    for j, n in enumerate(recent or []):
        if n == 0:
            x, y = x0 + 1.5 * cw + 24, 2 + (zero_h - 2) / 2
        else:
            r, c = (n - 1) // 3, (n - 1) % 3
            x, y = x0 + c * cw + cw - 11, zero_h + r * ch + 9
        o.append(f'<circle cx="{x}" cy="{y}" r="7.5" fill="#E6EDF6" fill-opacity="{1 - j * 0.09:.2f}" stroke="#0F1726"/>'
                 f'<text x="{x}" y="{y + 3}" font-size="9" font-weight="900" fill="#0F1726" text-anchor="middle">{j + 1}</text>')
    if last is not None and last != 0:
        r, c = (last - 1) // 3, (last - 1) % 3
        o.append(rect(x0 + c * cw + 1, zero_h + r * ch + 1, cw - 2, ch - 2, 'none', stroke='#fff', sw=2.4, rx=3))
    o.append('</svg>')
    return ''.join(o)

# ───────────────────────── Grafik yardımcıları ─────────────────────────
def _scale(v, lo, hi, a, b):
    return a + (v - lo) / (hi - lo) * (b - a) if hi != lo else (a + b) / 2

def svg_line(series, w=340, h=130, ymin=None, ymax=None, band=None, hlines=(), xlabels=None, pad=(30, 8, 8, 20), ylab=None,
             fmt=lambda v: f'{v:g}', markers=None):
    """series: [dict(ys, color, dash, width, name)]; band: (lo[], hi[], color); hlines: [(v,label,color,dash)]"""
    L, T, Rr, B = pad
    n = max(len(s['ys']) for s in series)
    allv = [v for s in series for v in s['ys']] + ([x for x in band[0]] + [x for x in band[1]] if band else []) + [v for v, *_ in hlines]
    lo = ymin if ymin is not None else min(allv); hi = ymax if ymax is not None else max(allv)
    if hi == lo: hi = lo + 1
    pw, ph = w - L - Rr, h - T - B
    X = lambda i: L + (i / max(1, n - 1)) * pw
    Y = lambda v: T + ph - _scale(v, lo, hi, 0, ph)
    o = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg">']
    for t in range(5):
        v = lo + (hi - lo) * t / 4
        y = Y(v)
        o.append(f'<line x1="{L}" x2="{w - Rr}" y1="{y:.1f}" y2="{y:.1f}" stroke="#1E2A3D" stroke-width="1"/>'
                 f'<text x="{L - 4}" y="{y + 3:.1f}" font-size="9" fill="#6F7F96" text-anchor="end">{fmt(v)}</text>')
    if band:
        blo, bhi, bc = band
        pts = [f'{X(i):.1f},{Y(bhi[i]):.1f}' for i in range(len(bhi))] + [f'{X(i):.1f},{Y(blo[i]):.1f}' for i in range(len(blo) - 1, -1, -1)]
        o.append(f'<polygon points="{" ".join(pts)}" fill="{bc}" fill-opacity=".22"/>')
    for v, lab, c, dash in hlines:
        y = Y(v)
        o.append(f'<line x1="{L}" x2="{w - Rr}" y1="{y:.1f}" y2="{y:.1f}" stroke="{c}" stroke-width="1.2" {"stroke-dasharray=%s4 3%s" % (chr(34), chr(34)) if dash else ""}/>')
        if lab: o.append(f'<text x="{w - Rr - 2}" y="{y - 3:.1f}" font-size="9" fill="{c}" text-anchor="end">{lab}</text>')
    for s in series:
        pts = ' '.join(f'{X(i):.1f},{Y(v):.1f}' for i, v in enumerate(s['ys']))
        o.append(f'<polyline points="{pts}" fill="none" stroke="{s["color"]}" stroke-width="{s.get("width", 1.7)}" stroke-linejoin="round" {"stroke-dasharray=%s5 3%s" % (chr(34), chr(34)) if s.get("dash") else ""}/>')
    for (i, lab, c) in (markers or []):
        x = X(i)
        o.append(f'<line x1="{x:.1f}" x2="{x:.1f}" y1="{T}" y2="{T + ph}" stroke="{c}" stroke-width="1.2" stroke-dasharray="3 3"/>'
                 f'<text x="{x + 3:.1f}" y="{T + 9}" font-size="9" fill="{c}">{lab}</text>')
    for (i, lab) in (xlabels or []):
        o.append(f'<text x="{X(i):.1f}" y="{h - 5}" font-size="9" fill="#6F7F96" text-anchor="middle">{lab}</text>')
    o.append('</svg>')
    return ''.join(o)

def svg_bars(items, w=340, h=120, base=None, ymax=None, colors=None, pad=(26, 8, 6, 22), fmt=lambda v: f'{v:g}', hl=None, labfs=9.5):
    """items: [(etiket, değer)]; base: yatay taban çizgisi (çizgili sarı)"""
    L, T, Rr, B = pad
    vals = [v for _, v in items]
    hi = ymax or max(vals + ([base] if base else [])) * 1.18
    pw, ph = w - L - Rr, h - T - B
    bw = pw / len(items)
    o = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg">']
    for t in range(4):
        v = hi * t / 3; y = T + ph - v / hi * ph
        o.append(f'<line x1="{L}" x2="{w - Rr}" y1="{y:.1f}" y2="{y:.1f}" stroke="#1E2A3D"/>'
                 f'<text x="{L - 4}" y="{y + 3:.1f}" font-size="9" fill="#6F7F96" text-anchor="end">{fmt(v)}</text>')
    for i, (lab, v) in enumerate(items):
        x = L + i * bw + bw * 0.14; bh = v / hi * ph
        c = (colors[i] if colors else '#1976D2')
        if hl and i in hl: c = '#FFC531'
        o.append(f'<rect x="{x:.1f}" y="{T + ph - bh:.1f}" width="{bw * 0.72:.1f}" height="{bh:.1f}" rx="2.5" fill="{c}"/>')
        o.append(f'<text x="{x + bw * 0.36:.1f}" y="{h - 7}" font-size="{labfs}" fill="#8A97AA" text-anchor="middle">{lab}</text>')
    if base is not None:
        y = T + ph - base / hi * ph
        o.append(f'<line x1="{L}" x2="{w - Rr}" y1="{y:.1f}" y2="{y:.1f}" stroke="#FFC531" stroke-width="1.4" stroke-dasharray="5 3"/>')
    o.append('</svg>')
    return ''.join(o)

def svg_reliability(pts, w=340, h=210, lim=(0, 100)):
    """pts: [(tahmin %, gözlenen %, n)] ; köşegen = mükemmel kalibrasyon"""
    L, T, Rr, B = 34, 10, 10, 28
    pw, ph = w - L - Rr, h - T - B
    lo, hi = lim
    X = lambda v: L + (v - lo) / (hi - lo) * pw
    Y = lambda v: T + ph - (v - lo) / (hi - lo) * ph
    o = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg">']
    for t in range(0, 6):
        v = lo + (hi - lo) * t / 5
        o.append(f'<line x1="{L}" x2="{w - Rr}" y1="{Y(v):.1f}" y2="{Y(v):.1f}" stroke="#1E2A3D"/>'
                 f'<line y1="{T}" y2="{T + ph}" x1="{X(v):.1f}" x2="{X(v):.1f}" stroke="#1E2A3D"/>'
                 f'<text x="{L - 5}" y="{Y(v) + 3:.1f}" font-size="9" fill="#6F7F96" text-anchor="end">{v:g}</text>'
                 f'<text x="{X(v):.1f}" y="{h - 14}" font-size="9" fill="#6F7F96" text-anchor="middle">{v:g}</text>')
    o.append(f'<line x1="{X(lo):.1f}" y1="{Y(lo):.1f}" x2="{X(hi):.1f}" y2="{Y(hi):.1f}" stroke="#FFC531" stroke-width="1.3" stroke-dasharray="5 3"/>')
    if len(pts) > 1:
        o.append(f'<polyline points="{" ".join(f"{X(a):.1f},{Y(b):.1f}" for a, b, _ in pts)}" fill="none" stroke="#64B5F6" stroke-width="1.6"/>')
    for a, b, n in pts:
        r = 3 + min(7, n ** 0.5 / 5)
        o.append(f'<circle cx="{X(a):.1f}" cy="{Y(b):.1f}" r="{r:.1f}" fill="#1976D2" stroke="#BBDEFB" stroke-width="1.2"/>')
    o.append(f'<text x="{L + pw / 2}" y="{h - 2}" font-size="9.5" fill="#8A97AA" text-anchor="middle">tahmin edilen olasılık %</text>')
    o.append(f'<text transform="translate(9,{T + ph / 2}) rotate(-90)" font-size="9.5" fill="#8A97AA" text-anchor="middle">gözlenen %</text>')
    o.append('</svg>')
    return ''.join(o)

def _heat_col(v, vmin, vmax, mode):
    t = 0 if vmax == vmin else (v - vmin) / (vmax - vmin)
    t = max(0, min(1, t))
    if mode == 'div':  # kırmızı – nötr – yeşil
        m = (v - (vmin + vmax) / 2) / ((vmax - vmin) / 2 or 1)
        m = max(-1, min(1, m))
        if m >= 0:
            a = (30, 42, 61); b = (46, 125, 50)
        else:
            a = (30, 42, 61); b = (198, 40, 40); m = -m
        c = tuple(int(a[i] + (b[i] - a[i]) * m) for i in range(3))
    else:  # seq: koyu mavi → açık mavi
        a = (24, 38, 64); b = (66, 165, 245)
        c = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return '#%02x%02x%02x' % c

def svg_heat(mat, rl, cl, cw=34, ch=22, vmin=None, vmax=None, mode='seq', fmt=lambda v: f'{v:g}', left=44, top=18, fs=9.5, mark=None, show=True):
    rows, cols = len(mat), len(mat[0])
    flat = [v for r in mat for v in r]
    vmin = min(flat) if vmin is None else vmin; vmax = max(flat) if vmax is None else vmax
    W = left + cols * cw + 4; H = top + rows * ch + 4
    o = [f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}" xmlns="http://www.w3.org/2000/svg">']
    for j, c in enumerate(cl):
        o.append(f'<text x="{left + j * cw + cw / 2}" y="{top - 5}" font-size="9" fill="#8AA3C4" text-anchor="middle">{c}</text>')
    for i, r in enumerate(rl):
        o.append(f'<text x="{left - 5}" y="{top + i * ch + ch / 2 + 3}" font-size="9" fill="#8AA3C4" text-anchor="end">{r}</text>')
    for i in range(rows):
        for j in range(cols):
            v = mat[i][j]
            o.append(f'<rect x="{left + j * cw + 1}" y="{top + i * ch + 1}" width="{cw - 2}" height="{ch - 2}" rx="3" fill="{_heat_col(v, vmin, vmax, mode)}"/>')
            if show:
                o.append(f'<text x="{left + j * cw + cw / 2}" y="{top + i * ch + ch / 2 + 3.2}" font-size="{fs}" fill="#E6EDF6" text-anchor="middle">{fmt(v)}</text>')
    for (i, j, c) in (mark or []):
        o.append(f'<rect x="{left + j * cw + .5}" y="{top + i * ch + .5}" width="{cw - 1}" height="{ch - 1}" rx="3.5" fill="none" stroke="{c}" stroke-width="2"/>')
    o.append('</svg>')
    return ''.join(o)

def svg_hist(counts, w=340, h=110, marker=None, color='#3F6FB5', labels=None, mlabel='gözlenen', xt=None):
    L, T, Rr, B = 8, 14, 8, 20
    pw, ph = w - L - Rr, h - T - B
    mx = max(counts) * 1.1
    bw = pw / len(counts)
    o = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg">']
    for i, c in enumerate(counts):
        bh = c / mx * ph
        o.append(f'<rect x="{L + i * bw + 0.6:.1f}" y="{T + ph - bh:.1f}" width="{bw - 1.2:.1f}" height="{bh:.1f}" fill="{color}"/>')
    if marker is not None:
        x = L + marker * bw
        o.append(f'<line x1="{x:.1f}" x2="{x:.1f}" y1="{T - 4}" y2="{T + ph}" stroke="#FFC531" stroke-width="2"/>'
                 f'<text x="{x:.1f}" y="{T - 6}" font-size="9.5" font-weight="700" fill="#FFC531" text-anchor="middle">{mlabel}</text>')
    for (i, lab) in (xt or []):
        o.append(f'<text x="{L + i * bw:.1f}" y="{h - 6}" font-size="9" fill="#6F7F96" text-anchor="middle">{lab}</text>')
    o.append('</svg>')
    return ''.join(o)

def svg_gauge(value, w=220, label='ROBUSTNESS', cls='C'):
    cx, cy, r = w / 2, w * 0.50, w * 0.4
    H = int(w * 0.86)
    o = [f'<svg width="{w}" height="{H}" viewBox="0 0 {w} {H}" xmlns="http://www.w3.org/2000/svg">']
    zones = [(0, 20, '#C62828'), (20, 40, '#EF8F00'), (40, 60, '#78909C'), (60, 80, '#7CB342'), (80, 100, '#2E7D32')]
    for a, b, c in zones:
        a0 = -90 + a * 1.8; a1 = -90 + b * 1.8
        o.append(f'<path d="{wedge(cx, cy, r - 11, r, a0 + 0.8, a1 - 0.8)}" fill="{c}" fill-opacity=".9"/>')
    for t in (0, 20, 40, 60, 80, 100):
        x, y = pol(cx, cy, r + 9, -90 + t * 1.8)
        o.append(f'<text x="{x:.1f}" y="{y + 3:.1f}" font-size="8.5" fill="#6F7F96" text-anchor="middle">{t}</text>')
    ang = -90 + value * 1.8
    x, y = pol(cx, cy, r - 16, ang)
    o.append(f'<line x1="{cx}" y1="{cy}" x2="{x:.1f}" y2="{y:.1f}" stroke="#fff" stroke-width="3" stroke-linecap="round"/><circle cx="{cx}" cy="{cy}" r="6" fill="#fff"/>')
    o.append(f'<text x="{cx}" y="{cy + 40}" font-size="32" font-weight="900" fill="#E6EDF6" text-anchor="middle">{value}<tspan font-size="14" font-weight="500" fill="#8AA3C4"> / 100</tspan></text>')
    o.append(f'<text x="{cx}" y="{cy + 58}" font-size="10" fill="#8AA3C4" text-anchor="middle" letter-spacing="1.2">{label}</text>')
    o.append('</svg>')
    return ''.join(o)

def svg_timeline(w=340, past=0.62, cut=0.62, h=44):
    """PAST | CUT | FUTURE — her replay adımında geçmiş fiziksel olarak kesilir (Prompt §33)"""
    px = 6 + (w - 12) * past
    o = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg">']
    o.append(f'<rect x="6" y="14" width="{px - 6:.1f}" height="16" rx="4" fill="#1F4D2C"/>')
    o.append(f'<rect x="{px:.1f}" y="14" width="{w - 6 - px:.1f}" height="16" rx="4" fill="#3A1A1C" stroke="#7A3A3A" stroke-dasharray="3 3"/>')
    o.append(f'<line x1="{px:.1f}" x2="{px:.1f}" y1="8" y2="36" stroke="#FFC531" stroke-width="2.4"/>')
    o.append(f'<text x="{(6 + px) / 2:.1f}" y="26" font-size="10" font-weight="700" fill="#A5D6A7" text-anchor="middle">PAST (görülebilir)</text>')
    o.append(f'<text x="{px:.1f}" y="7" font-size="9.5" font-weight="900" fill="#FFC531" text-anchor="middle">CUT</text>')
    o.append(f'<text x="{(px + w - 6) / 2:.1f}" y="26" font-size="10" font-weight="700" fill="#FF8A80" text-anchor="middle">FUTURE (erişilemez)</text>')
    o.append('</svg>')
    return ''.join(o)

def svg_strip(labels, colors, w=340, h=26):
    """rejim şeridi: labels = [(oran, ad)] ; colors liste"""
    tot = sum(a for a, _ in labels); x = 4
    o = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg">']
    for (a, name), c in zip(labels, colors):
        ww = (w - 8) * a / tot
        o.append(f'<rect x="{x:.1f}" y="3" width="{ww - 1.5:.1f}" height="{h - 6}" rx="3" fill="{c}"/>')
        if ww > 28:
            o.append(f'<text x="{x + ww / 2:.1f}" y="{h / 2 + 3.2}" font-size="9" font-weight="700" fill="#fff" text-anchor="middle">{name}</text>')
        x += ww
    o.append('</svg>')
    return ''.join(o)

def svg_spark(vals, w=70, h=22, color='#64B5F6'):
    lo, hi = min(vals), max(vals)
    pts = ' '.join(f'{2 + i / (len(vals) - 1) * (w - 4):.1f},{h - 3 - _scale(v, lo, hi, 0, h - 6):.1f}' for i, v in enumerate(vals))
    return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}"><polyline points="{pts}" fill="none" stroke="{color}" stroke-width="1.5" stroke-linejoin="round"/></svg>'

def svg_ci(rows, w=340, rowh=26, lo=None, hi=None):
    """rows: [(etiket, nokta, ci_lo, ci_hi, taban)] — nokta tahmin + %95 CI + taban çizgisi"""
    L, Rr = 112, 10
    lo = lo if lo is not None else min(r[2] for r in rows) - 1
    hi = hi if hi is not None else max(r[3] for r in rows) + 1
    pw = w - L - Rr; H = rowh * len(rows) + 6
    X = lambda v: L + (v - lo) / (hi - lo) * pw
    o = [f'<svg width="{w}" height="{H}" viewBox="0 0 {w} {H}" xmlns="http://www.w3.org/2000/svg">']
    for i, (lab, p, a, b, base) in enumerate(rows):
        y = 3 + i * rowh + rowh / 2
        o.append(f'<text x="{L - 8}" y="{y + 3.5}" font-size="10.5" fill="#CFE0F5" text-anchor="end">{lab}</text>')
        o.append(f'<line x1="{X(a):.1f}" x2="{X(b):.1f}" y1="{y}" y2="{y}" stroke="#64B5F6" stroke-width="2.2" stroke-linecap="round"/>')
        o.append(f'<circle cx="{X(p):.1f}" cy="{y}" r="4" fill="#1976D2" stroke="#E3F2FD" stroke-width="1.4"/>')
        o.append(f'<line x1="{X(base):.1f}" x2="{X(base):.1f}" y1="{y - 8}" y2="{y + 8}" stroke="#FFC531" stroke-width="2"/>')
    o.append('</svg>')
    return ''.join(o)
