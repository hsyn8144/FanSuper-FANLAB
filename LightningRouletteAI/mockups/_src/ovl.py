# -*- coding: utf-8 -*-
"""Overlay kartı üreticisi. Tüm overlay görselleri (ve Ayarlar önizlemesi) buradan çıkar."""
from lib import *

def _k(c):  # aday etiketi: k2 / L2 / R2
    d = c.get('d', 'bi')
    return f'k{c["k"]}' if d == 'bi' else f'{d}{c["k"]}'

# ENTER sonrası örnek (sonuç 21 girildikten sonra yeni tahmin)
NEXT2 = [dict(n=12, k=2, d='bi', rank=1, p=2.9), dict(n=33, k=2, d='bi', rank=2, p=2.8), dict(n=4, k=2, d='bi', rank=3, p=2.8), dict(n=20, k=3, d='bi', rank=4, p=2.7), dict(n=9, k=1, d='bi', rank=5, p=2.7)]
TABLE2 = [('KIRMIZI', 'Kırmızı', 50.4, 18 / 37 * 100), ('ÇİFT', 'Çift', 50.1, 18 / 37 * 100), ('KÜÇÜK', '1–18', 50.0, 18 / 37 * 100), ('3. DOZEN', '25–36', 34.0, 12 / 37 * 100), ('1. COLUMN', 'Sütun 1', 33.2, 12 / 37 * 100)]
LAST8_2 = [21] + LAST8[:7]

def ov_head(a, lock=True, warn=True, pyerr=False, title='⚡ LR·AI'):
    if pyerr: title = 'LR·AI'
    lk = f'<span class="oi"{a("lock", "T")}>{"🔒" if lock else "⏳"}</span>'
    wr = f'<span class="oi w"{a("warn", "T")}>⚠</span>' if warn else ''
    pe = '<span class="tag err" style="font-size:8.5px;padding:1px 4px;white-space:nowrap">Py ERROR</span>' if pyerr else ''
    return (f'<div class="oh"{a("head", "L")}><span{a("logo", "T")}>{title}</span><span class="sp"><i class="grip"{a("grip", "T")}></i></span>{pe}{wr}{lk}'
            f'<span class="oi"{a("min", "T")}>▾</span><span class="oi"{a("close", "T")}>✕</span></div>')

def ov_next(a, cands=None, dash=False):
    cands = CANDS if cands is None else cands
    if dash:
        chips_ = ''.join('<div class="c"><b>--</b><i>&nbsp;</i></div>' for _ in range(5))
    else:
        chips_ = ''.join(f'<div class="c{" r1" if c["rank"] == 1 else ""}"><b>{c["n"]}</b><i>{_k(c)}</i></div>' for c in cands)
    return f'<div class="ol">NEXT</div><div class="nx"{a("next", "L")}>{chips_}</div>'

def ov_table(a, rows=None, dash=False):
    rows = TABLE if rows is None else rows
    body = ''.join(f'<div class="r"><span>{lab}</span><em>{"--" if dash else pc(p)}</em></div>' for lab, _, p, _ in rows)
    return f'<div class="ol">TABLE</div><div class="tbo"{a("table", "L")}>{body}</div>'

def ov_recent(a, ns=None):
    ns = LAST8 if ns is None else ns
    return f'<div class="ol">SON 8</div><div class="rc"{a("recent", "L")}>' + ''.join(chip(x, 's') for x in ns) + '</div>'

def ov_rbox(a, typed=None, err=False):
    inner = '<span style="font-size:20px;font-weight:400;opacity:.55">—</span>' if typed is None else str(typed)
    return f'<div class="rbx {"err" if err else ""}"{a("rbox", "L")}><small>{"0–36 DIŞI!" if err else "SONUÇ"}</small>{inner}</div>'

def ov_kp(a, pressed=None, enter_dis=False):
    def k(t, c='', at=''):
        if pressed is not None and str(t) == str(pressed): c = (c + ' pr').strip(); at = a('pressed', 'tr')
        return f'<div class="{c}"{at}>{t}</div>'
    ent = k('ENTER', 'e dis' if enter_dis else 'e', a('enter', 'tr'))
    return ('<div class="kpo">' + ''.join([k(1), k(2), k(3), k(4), k(5, at=a('digits', 'tr')), k(6), k(7), k(8), k(9), k('DEL', 'd', a('del')), k(0, 'z', a('zero')), ent]) + '</div>')

def ov(kind='full', ann=None, typed=None, err=False, pyerr=False, dash=False, evs=None, note=None, cands=None, table=None, recent=None,
       warn=True, lock=True, alpha=.92, width=None, style='', pressed=None, enter_dis=False, kb=False):
    ann = ann or {}
    a = lambda k, np='tl': A(ann.get(k), np)
    st = f'background:rgba(21,101,192,{alpha});' + (f'width:{width}px;' if width else '') + style
    nt = f'<div class="note"{a("note", "L")}>{note}</div>' if note else ''
    if kind in ('full', 'compact'):
        o = [f'<div class="ov" style="{st}"{a("card", "tr")}>', ov_head(a, lock, warn, pyerr)]
        if evs: o.append(f'<div class="evs"{a("evs", "L")}>{evs}</div>')
        o.append(ov_next(a, cands, dash)); o.append(nt); o.append(ov_table(a, table, dash)); o.append(ov_recent(a, recent))
        if kind == 'full':
            o.append(ov_rbox(a, typed, err)); o.append(ov_kp(a, pressed, enter_dis))
        else:
            o.append(f'<div class="exp"{a("expand", "L")}>⌨ Klavyeyi aç</div>')
        o.append('</div>')
        return ''.join(o)
    if kind == 'text':
        nxt = ' | '.join(f'{c["n"]}-{_k(c)}' for c in (cands or CANDS))
        tb = ''.join(f'<div><span class="lb">{lab}</span> <span class="p">{pc(p)}</span></div>' for lab, _, p, _ in (table or TABLE))
        rc = ' | '.join(str(x) for x in (recent or LAST8))
        kbi = f'<span class="kbi"{a("kb", "T")}>⌨</span>' if kb else ''
        return (f'<div class="ov txt" style="{st}"{a("card", "tr")}>{kbi}'
                f'<div class="lb"{a("hdr", "L")}>NEXT:</div><div class="v"{a("next", "L")}>{nxt}</div>'
                f'<div class="lb" style="margin-top:3px">TABLE:</div><div{a("table", "L")}>{tb}</div>'
                f'<div class="lb" style="margin-top:3px">SON 8:</div><div class="v"{a("recent", "L")}>{rc}</div></div>')
    if kind == 'h':
        cs = ''.join(f'<div class="c{" r1" if c["rank"] == 1 else ""}"><b>{c["n"]}</b><i>{_k(c)}</i></div>' for c in (cands or CANDS))
        tb = ''.join(f'<div style="display:flex;justify-content:space-between;font-size:11.5px"><span style="font-weight:700;color:#E3F2FD">{lab}</span>'
                     f'<em style="font-style:normal;font-family:Roboto Mono,LRSym,monospace;font-weight:700;color:#FFB74D">{pc(p)}</em></div>' for lab, _, p, _ in (table or TABLE))
        rc = ''.join(chip(x, 's') for x in (recent or LAST8))
        rb = f'<div class="rbx" style="margin-top:0;height:34px"{a("rbox")}><small>SONUÇ</small>{typed if typed is not None else "—"}</div>'
        return (f'<div class="ov h" style="{st}"{a("card", "tr")}>'
                f'<div class="lft">{ov_head(a, lock, warn, pyerr)}<div class="ol" style="margin-top:2px">NEXT</div><div class="nx"{a("next", "L")}>{cs}</div>'
                f'<div class="ol">TABLE</div><div{a("table", "L")} style="display:grid;grid-template-columns:1fr 1fr;column-gap:14px;row-gap:1px">{tb}</div>'
                f'<div class="ol">SON 8</div><div class="rc"{a("recent", "L")}>{rc}</div></div>'
                f'<div class="rgt">{rb}{ov_kp(a, pressed, enter_dis)}</div></div>')
    raise ValueError(kind)

def bubble(n=None, lock=True, state=''):
    return f'<div class="bubble {state}"{A(n, "tr")}>⚡<span class="lk">{"🔒" if lock else "⏳"}</span></div>'

def fakebg(inner='', lines=5, label='Başka bir uygulama · canlı yayın alanı (örnek arka plan)', align='flex-end'):
    ln = '<div class="ln"></div>' * lines
    return f'<div class="fakebg"><div class="vid" style="justify-content:{align};padding:0 14px">{label}</div><div style="height:250px"></div>{ln}</div>{inner}'
