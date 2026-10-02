# -*- coding: utf-8 -*-
"""Birden çok sayfada kullanılan ortak parçalar (Ana başlığı, NEXT/TABLE blokları, LAB üst alanı ...)"""
from lib import *
import charts as ch

COVER = set()
for _c in CANDS: COVER |= set(span(_c['n'], _c['k'], _c.get('d', 'bi')))
COVER_N = len(COVER)                       # 22
COVER_PCT = COVER_N / 37 * 100             # 59,46
P5 = sum(c['p'] for c in CANDS)            # 14,2 (kalibre P(≥1 merkez))
BASE5 = 5 / 37 * 100

def title_bar(title='⚡ Lightning Roulette AI', ov='KAPALI', n_title=None, n_ov=None, sub=None):
    ovp = pill('🪟 Overlay: ' + ov, 'blue' if ov == 'AÇIK' else 'line', n=n_ov, np='TR')
    t = f'<span style="font-size:18px;font-weight:900;letter-spacing:.2px">{title}</span>'
    if n_title: t = ann(n_title, t, 'L')
    s = sub or f'{VER} · {NOW}'
    return f'<div class="row mb10"><div class="grow">{t}<div class="mini">{s}</div></div>{ovp}</div>'

def status_pills(n=None, py='ok'):
    pp = {'ok': pill('🐍 Python ✓', 'green'), 'err': pill('🐍 Python ERROR', 'red'), 'off': pill('🐍 Python yok', 'amber')}[py]
    row = hdr_pills([pill(f'{DATASET} · {th(SPINS)} spin', 'line'), pill(CHAMP, 'gold'), pp, pill('OFFLINE', 'line')])
    return ann(n, row, 'L', block=True) if n else row

def lock_card(n=None, pid=PRED_ID, ref=SPINS, refv=17, t='14:32:07', state='lock'):
    if state == 'lock':
        top = f'<span style="font-size:22px">🔒</span><div class="grow"><div class="b7 gold" style="font-size:14px">KİLİTLİ · {pid}</div>' \
              f'<div class="mini">Referans: spin #{th(ref)} ({refv}) · {t} · {CHAMP} · {DATASET}</div></div>{pill("DEĞİŞMEZ", "amber xs")}'
    else:
        top = f'<span style="font-size:22px">⏳</span><div class="grow"><div class="b7 warn" style="font-size:14px">Tahmin üretilmedi</div>' \
              f'<div class="mini">Yeterli veri yok · en az 50 spin gerekli</div></div>'
    return card(None, f'<div class="row">{top}</div>', n=n, cls='warnb' if state == 'lock' else '')

def next_block(cands=None, n_card=None, n_row=None, n_cov=None, title='NEXT · 5 aday', right=None):
    cands = cands or CANDS
    rows = ''.join(cand_row(c, n=n_row if c['rank'] == 1 else None, np='L') for c in cands)
    cov = set()
    for c in cands: cov |= set(span(c['n'], c['k'], c.get('d', 'bi')))
    p5 = sum(c['p'] for c in cands); b5 = len(cands) / 37 * 100
    cv = (f'<div class="sep"></div><div class="row sb" style="font-size:11.5px"{A(n_cov, "L")}><span class="muted">Birleşik kapsama</span><b class="mono">{len(cov)}/37 · {pc(len(cov) / 37 * 100, 1)}</b></div>'
          f'<div class="row sb" style="font-size:11.5px;margin-top:3px"><span class="muted">P(≥1 merkez isabeti) kalibre</span><b class="mono">{pc(p5, 1)} <span class="dim">taban {pc(b5, 1)}</span></b></div>')
    return card(title, rows + cv, n=n_card, right=right or pill('kalibre P(exact)', 'line xs'))

def table_block(n_card=None, rows=None, title='TABLE · 5 kategori'):
    rows = rows or TABLE
    body = ''.join(table_row(a, b, c, d) for a, b, c, d in rows)
    return card(title, body, n=n_card, right=pill('Sayıdan bağımsız', 'line xs'))

def recent_card(n=None, n_all=None, ns=None):
    ns = ns or LAST8
    return card('SON 8 SONUÇ', f'<div class="row" style="gap:5px"{A(n, "L")}>' + ''.join(chip(x, 'm') for x in ns) + '</div>'
                '<div class="mini mt6">en yeni solda · 17 → #5 214</div>', right=f'<span class="lnk"{A(n_all, "tr")}>Tümü ›</span>')

# ───────────── LAB ortak ─────────────
LABTABS = ['Overview', 'Models', 'Side/Table', 'Wheel', 'Sectors', 'Neighbors', 'Patterns', 'Transitions', 'Replay', 'Calibration', 'Diversity', 'Regime', 'Counterfactual', 'Ablation', 'Robustness', 'Experiments']

def lab_screen(active, content, n_tabs=None, n_head=None, fab=None, modal='', min_h=700):
    head = (f'<div class="row mb6"><div class="grow">{ann(n_head, "<span style=" + chr(34) + "font-size:18px;font-weight:900" + chr(34) + ">🧪 LAB V2</span>", "L") if n_head else "<span style=" + chr(34) + "font-size:18px;font-weight:900" + chr(34) + ">🧪 LAB V2</span>"}'
            f'<div class="mini">araştırma motoru · canlı modeli değiştiremez</div></div>{pill("LAB ≠ LIVE", "purple")}</div>'
            f'<div class="row mb8" style="gap:6px;flex-wrap:wrap">{pill(DATASET + " ▾", "line")}{pill("Train 3 128 · Val 1 043 · OOS 1 043", "line")}</div>')
    f_ = f'<div class="fab">{fab}</div>' if fab else ''
    return phone(head + hs(LABTABS, active, n=n_tabs, np='TL') + content + f_, nav=3, modal=modal, min_h=min_h)

def forest(rows, w=340, lo=-6, hi=6):
    """rows: [(etiket, delta_pp, ci_lo, ci_hi)] — sarı çizgi = 0 (taban)"""
    return ch.svg_ci([(a, b, c, d, 0) for a, b, c, d in rows], w=w, lo=lo, hi=hi)

def oos_deltas():
    out = []
    for name, p, base, lo, hi in OOS:
        out.append((name.replace(' Hit', ''), p - base, lo - base, hi - base))
    return out

def sparkrow(label, vals, color='#64B5F6', right=''):
    return f'<div class="kv"><div class="k">{label}</div>{ch.svg_spark(vals, 90, 22, color)}<div class="v" style="width:70px">{right}</div></div>'

def slider(p, txt, n=None, np='L'):
    return (f'<div class="row" style="gap:10px;padding:6px 0"{A(n, np)}><div style="flex:1;height:5px;border-radius:3px;background:#22324F;position:relative">'
            f'<i style="position:absolute;left:0;top:0;bottom:0;width:{p}%;background:#1565C0;border-radius:3px"></i>'
            f'<b style="position:absolute;left:calc({p}% - 8px);top:-6px;width:17px;height:17px;border-radius:50%;background:#64B5F6;border:2px solid #0F1726"></b></div>'
            f'<span class="mono fs11" style="width:48px;text-align:right">{txt}</span></div>')

def radio(t, s, on, n=None, np='L'):
    b = '#1565C0' if on else '#2C3E5E'
    inner = '<i style="width:7px;height:7px;border-radius:50%;background:#64B5F6"></i>' if on else ''
    dot = '<span style="width:17px;height:17px;border-radius:50%;border:2px solid ' + b + ';flex:none;display:flex;align-items:center;justify-content:center">' + inner + '</span>'
    sub = f'<div class="mini">{s}</div>' if s else ''
    return f'<div class="row" style="padding:5px 0;gap:10px;align-items:flex-start"{A(n, np)}>{dot}<div><div style="font-size:12.5px">{t}</div>{sub}</div></div>'

def chk(t, on=True, n=None, np='L'):
    return f'<div class="row" style="padding:5px 0;gap:10px"{A(n, np)}><span class="chk {"" if on else "off"}">✓</span><span style="font-size:12.5px">{t}</span></div>'
