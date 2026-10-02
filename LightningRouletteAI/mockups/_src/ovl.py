# -*- coding: utf-8 -*-
"""Overlay kartı üreticisi. Tüm overlay görselleri (ve Ayarlar önizlemesi) buradan çıkar."""
from lib import *

def _k(c):  # aday etiketi: k2 / L2 / R2
    d = c.get('d', 'bi')
    return f'k{c["k"]}' if d == 'bi' else f'{d}{c["k"]}'

def ov_head(a, lock=True, warn=True, pyerr=False, title='⚡ LR·AI', mini=False):
    lk = '<span class="oi">🔒</span>' if lock else '<span class="oi">⏳</span>'
    wr = '<span class="oi w">⚠</span>' if warn else ''
    pe = '<span class="tag err" style="font-size:8.5px;padding:1px 4px">Py ERROR</span>' if pyerr else ''
    return (f'<div class="oh"{a("head")}><span>{title}</span><span class="sp"><i class="grip"></i></span>{pe}{wr}{lk}'
            f'<span class="oi">▾</span><span class="oi">✕</span></div>')

def ov_next(a, cands=None, dash=False):
    cands = CANDS if cands is None else cands
    if dash:
        chips_ = ''.join('<div class="c"><b>--</b><i>&nbsp;</i></div>' for _ in range(5))
    else:
        chips_ = ''.join(f'<div class="c{" r1" if c["rank"] == 1 else ""}"><b>{c["n"]}</b><i>{_k(c)}</i></div>' for c in cands)
    return f'<div class="ol">NEXT</div><div class="nx"{a("next")}>{chips_}</div>'

def ov_table(a, rows=None, dash=False):
    rows = TABLE if rows is None else rows
    body = ''.join(f'<div class="r"><span>{lab}</span><em>{"--" if dash else pc(p)}</em></div>' for lab, _, p, _ in rows)
    return f'<div class="ol">TABLE</div><div class="tbo"{a("table")}>{body}</div>'

def ov_recent(a, ns=None):
    ns = LAST8 if ns is None else ns
    return f'<div class="ol">SON 8</div><div class="rc"{a("recent")}>' + ''.join(chip(x, 's') for x in ns) + '</div>'

def ov_rbox(a, typed=None, err=False):
    if typed is None:
        inner = '<span style="font-size:20px;font-weight:400;opacity:.55">—</span>'
    else:
        inner = str(typed)
    return f'<div class="rbx {"err" if err else ""}"{a("rbox")}><small>{"0–36 DIŞI!" if err else "SONUÇ"}</small>{inner}</div>'

def ov_kp(a, sm=False, enter_dis=False):
    k = lambda t, c='', at='': f'<div class="{c}"{at}>{t}</div>'
    return (f'<div class="kpo">' + ''.join([k(1), k(2), k(3), k(4), k(5, at=a('digits', 'tr')), k(6), k(7), k(8), k(9),
            k('DEL', 'd', a('del')), k(0, 'z', a('zero')), k('ENTER', 'e', a('enter', 'tr'))]) + '</div>')

def ov(kind='full', ann=None, typed=None, err=False, pyerr=False, dash=False, evs=None, cands=None, recent=None,
       warn=True, lock=True, alpha=.92, width=None, style=''):
    ann = ann or {}
    a = lambda k, np='tl': A(ann.get(k), np)
    st = f'background:rgba(21,101,192,{alpha});' + (f'width:{width}px;' if width else '') + style
    if kind == 'full' or kind == 'compact':
        o = [f'<div class="ov" style="{st}"{a("card", "tr")}>', ov_head(a, lock, warn, pyerr)]
        if evs: o.append(f'<div class="evs"{a("evs")}>{evs}</div>')
        o.append(ov_next(a, cands, dash)); o.append(ov_table(a, None, dash)); o.append(ov_recent(a, recent))
        if kind == 'full':
            o.append(ov_rbox(a, typed, err)); o.append(ov_kp(a))
        else:
            o.append(f'<div class="exp"{a("expand")}>⌨ Klavyeyi aç</div>')
        o.append('</div>')
        return ''.join(o)
    if kind == 'text':
        nxt = ' | '.join(f'{c["n"]}-{_k(c)}' for c in (cands or CANDS))
        tb = ''.join(f'<div><span class="lb">{lab}</span> <span class="p">{pc(p)}</span></div>' for lab, _, p, _ in TABLE)
        rc = ' | '.join(str(x) for x in (recent or LAST8))
        return (f'<div class="ov txt" style="{st}"{a("card", "tr")}>'
                f'<div class="lb"{a("hdr")}>NEXT:</div><div class="v"{a("next")}>{nxt}</div>'
                f'<div class="lb" style="margin-top:3px">TABLE:</div><div{a("table")}>{tb}</div>'
                f'<div class="lb" style="margin-top:3px">SON 8:</div><div class="v"{a("recent")}>{rc}</div></div>')
    if kind == 'h':
        cs = ''.join(f'<div class="c{" r1" if c["rank"] == 1 else ""}"><b>{c["n"]}</b><i>{_k(c)}</i></div>' for c in (cands or CANDS))
        tb = ''.join(f'<div style="display:flex;justify-content:space-between;font-size:11.5px"><span style="font-weight:700;color:#E3F2FD">{lab}</span>'
                     f'<em style="font-style:normal;font-family:'Roboto Mono','LRSym',monospace;font-weight:700;color:#FFB74D">{pc(p)}</em></div>' for lab, _, p, _ in TABLE)
        rc = ''.join(chip(x, 's') for x in (recent or LAST8))
        return (f'<div class="ov h" style="{st}"{a("card", "tr")}>'
                f'<div class="lft">{ov_head(a, lock, warn, pyerr)}<div class="ol" style="margin-top:2px">NEXT</div><div class="nx"{a("next")}>{cs}</div>'
                f'<div class="ol">TABLE</div><div{a("table")} style="display:grid;grid-template-columns:1fr 1fr;column-gap:14px;row-gap:1px">{tb}</div>'
                f'<div class="ol">SON 8</div><div class="rc"{a("recent")}>{rc}</div></div>'
                f'<div class="rgt"><div class="rbx" style="margin-top:0;height:34px"{a("rbox")}><small>SONUÇ</small>{typed if typed is not None else "—"}</div>{ov_kp(a)}</div></div>')
    raise ValueError(kind)

def bubble(n=None, lock=True):
    return f'<div class="bubble"{A(n, "tr")}>⚡<span class="lk">{"🔒" if lock else "⏳"}</span></div>'

def fakebg(inner='', lines=5, label='Başka bir uygulama · canlı yayın alanı (örnek arka plan)'):
    ln = '<div class="ln"></div>' * lines
    return f'<div class="fakebg"><div class="vid">{label}</div><div style="height:250px"></div>{ln}</div>{inner}'
