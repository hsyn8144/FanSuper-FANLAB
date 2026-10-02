# -*- coding: utf-8 -*-
"""HTML bileşenleri + sayfa kaydı. Her ekran bir fonksiyondur; @sheet(...) ile kaydedilir."""
from data import *
import charts as ch

# ───────────────────────── kayıt ─────────────────────────
SHEETS = []

def sheet(slug, section, title, sub, legend=(), refs='', wide=False, legend_title='Ekrandaki öğeler'):
    def deco(fn):
        SHEETS.append(dict(slug=slug, section=section, title=title, sub=sub, legend=list(legend), refs=refs,
                           wide=wide, legend_title=legend_title, fn=fn))
        return fn
    return deco

# ───────────────────────── küçük yardımcılar ─────────────────────────
def A(n=None, np='tl'):
    return f' data-n="{n}" data-np="{np}"' if n else ''

def ann(n, inner, np='L', block=False, style=''):
    """Metni/ögeyi sıkı bir kutuya sarar ve rozeti ona bağlar (tam genişlikli kapsayıcıda rozet uzağa düşmesin)."""
    d = 'block' if block else 'inline-block'
    return f'<span style="display:{d};{style}"{A(n, np)}>{inner}</span>'

def card(title, body, n=None, np='tl', cls='', style='', right=''):
    t = f'<div class="ct"><span>{title or ""}</span><span class="sp"></span>{right}</div>' if (title or right) else ''
    return f'<div class="card {cls}" style="{style}"{A(n, np)}>{t}{body}</div>'

def kv(k, v, c='', mono=False, n=None, np='tl', sub=None):
    s = f'<small>{sub}</small>' if sub else ''
    return f'<div class="kv"{A(n, np)}><div class="k">{k}{s}</div><div class="v {c} {"mono" if mono else ""}">{v}</div></div>'

def muted(t, cls='', style=''): return f'<div class="muted {cls}" style="{style}">{t}</div>'

def pill(t, kind='', n=None, np='tl', style=''):
    return f'<span class="pill {kind}" style="{style}"{A(n, np)}>{t}</span>'

def seg(items, sel, n=None, np='tl'):
    return f'<div class="tabs"{A(n, np)}>' + ''.join(f'<span class="{"on" if i == sel else ""}">{t}</span>' for i, t in enumerate(items)) + '</div>'

def hs(items, sel, n=None, np='tl'):
    return f'<div class="hs"{A(n, np)}>' + ''.join(f'<span class="{"on" if i == sel else ""}">{t}</span>' for i, t in enumerate(items)) + '</div>'

def btn(label, kind='', n=None, np='tl', style='', cls=''):
    return f'<div class="btn {kind} {cls}" style="{style}"{A(n, np)}>{label}</div>'

def btns(*items, gap=8):
    return f'<div class="btns" style="gap:{gap}px">' + ''.join(items) + '</div>'

def sw(on=True, n=None, np='tl'): return f'<span class="sw {"" if on else "off"}"{A(n, np)}></span>'
def val(t, act=False, n=None, np='tl'): return f'<span class="val {"act" if act else ""}"{A(n, np)}>{t}</span>'
def choice(t, n=None, np='tl'): return val(t + ' ▾', n=n, np=np)

def setrow(title, sub=None, trailing='', n=None, np='tl', tc=''):
    s = f'<small>{sub}</small>' if sub else ''
    return f'<div class="set"{A(n, np)}><div class="t {tc}">{title}{s}</div>{trailing}</div>'

def bar(p, color='#1976D2', base=None, h=8, n=None, np='tl', mx=100):
    b = f'<b style="left:{base / mx * 100:.1f}%"></b>' if base is not None else ''
    return f'<div class="bar" style="height:{h}px"{A(n, np)}><i style="width:{min(100, p / mx * 100):.1f}%;background:{color}"></i>{b}</div>'

def prog(p, n=None, np='tl', style=''):
    return f'<div class="prog" style="{style}"{A(n, np)}><i style="width:{p}%"></i></div>'

def banner(kind, text, n=None, np='tl', icon=None, style=''):
    ic = icon or {'warn': '⚠️', 'bad': '⛔', 'ok': '✅', 'info': 'ℹ️', 'neutral': '📌'}[kind]
    return f'<div class="bn {kind}" style="{style}"{A(n, np)}><span class="ic">{ic}</span><div>{text}</div></div>'

def cls_b(c, n=None, np='tl'): return f'<span class="cls {c}"{A(n, np)}>{c}</span>'

def tbl(headers, rows, al=None, hl=(), dm=(), widths=None, n=None, np='tl', cls=''):
    al = al or 'l' * len(headers)
    def tdc(i): return {'l': '', 'r': ' r', 'c': ' c'}[al[i]]
    w = (lambda i: f' style="width:{widths[i]}"' if widths and widths[i] else '')
    h = ''.join(f'<th class="{tdc(i).strip()}"{w(i)}>{x}</th>' for i, x in enumerate(headers))
    body = ''
    for ri, r in enumerate(rows):
        c = 'hl' if ri in hl else ('dm' if ri in dm else '')
        body += f'<tr class="{c}">' + ''.join(f'<td class="{tdc(i).strip()}">{x}</td>' for i, x in enumerate(r)) + '</tr>'
    return f'<table class="tb {cls}"{A(n, np)}><tr>{h}</tr>{body}</table>'

def chip(nn, size=''):
    c = {'r': '', 'b': 'b', 'g': 'g'}[col(nn)]
    return f'<span class="nc {c} {size}">{nn}</span>'

def chips(ns, size='s', gap=3, n=None, np='tl'):
    return f'<span class="row" style="gap:{gap}px;display:inline-flex"{A(n, np)}>' + ''.join(chip(x, size) for x in ns) + '</span>'

def tile(label, value, sub='', c='', n=None, np='tl'):
    return f'<div class="tile"{A(n, np)}><div class="l">{label}</div><div class="v {c}">{value}</div><div class="s">{sub}</div></div>'

def tiles(*items, cols=2):
    return f'<div class="tiles" style="grid-template-columns:repeat({cols},1fr)">' + ''.join(items) + '</div>'

def delta(x, d=1, unit=' pp'):
    c = 'ok' if x > 0.05 else ('bad' if x < -0.05 else 'dim')
    return f'<span class="{c}">{sg(x, d, unit)}</span>'

# ───────────────────────── klavye / veri girişi ─────────────────────────
def keypad(typed=None, state='', sm=False, nd=None, nz=None, ndel=None, nent=None, nbox=None, dis_enter=False):
    box_cls = {'': '', 'err': 'err', 'ok': 'ok'}[state]
    if typed is None:
        inner = '<span class="dim" style="font-size:22px;font-weight:400">—</span>'
    else:
        inner = f'{typed}<span class="cur"></span>' if state != 'ok' else f'{typed}'
    box = f'<div class="rbox {box_cls}"{A(nbox)}><small>SONUÇ (0–36)</small>{inner}</div>'
    k = lambda t, c='', a='': f'<div class="{c}"{a}>{t}</div>'
    cells = ''.join([
        k(1), k(2), k(3), k(4), k(5, a=A(nd, 'tr')), k(6), k(7), k(8), k(9),
        k('DEL', 'd', A(ndel)), k(0, 'z', A(nz)), k('ENTER', 'e dis' if dis_enter else 'e', A(nent, 'tr')),
    ])
    return box + f'<div class="kp {"sm" if sm else ""}">{cells}</div>'

# ───────────────────────── telefon ─────────────────────────
NAV = [('🏠', 'Ana'), ('🏛️', 'Meclisler'), ('🎡', 'Wheel'), ('🧪', 'LAB'), ('💾', 'Veri'), ('⚙️', 'Ayarlar')]
_STATUS_R = ('<svg width="15" height="11" viewBox="0 0 15 11"><rect x="0" y="7" width="2.6" height="4" rx=".7" fill="#C9D3E2"/>'
             '<rect x="4" y="4.5" width="2.6" height="6.5" rx=".7" fill="#C9D3E2"/><rect x="8" y="2" width="2.6" height="9" rx=".7" fill="#C9D3E2"/>'
             '<rect x="12" y="0" width="2.6" height="11" rx=".7" fill="#6F7F96"/></svg>'
             '<svg width="15" height="11" viewBox="0 0 15 11"><path d="M7.5 10.3 5.7 8.2a2.6 2.6 0 0 1 3.6 0z" fill="#C9D3E2"/>'
             '<path d="M3.4 6.4a5.9 5.9 0 0 1 8.2 0" fill="none" stroke="#C9D3E2" stroke-width="1.3" stroke-linecap="round"/>'
             '<path d="M1 3.9a9.3 9.3 0 0 1 13 0" fill="none" stroke="#C9D3E2" stroke-width="1.3" stroke-linecap="round"/></svg>'
             '<svg width="24" height="11" viewBox="0 0 24 11"><rect x=".5" y=".5" width="20" height="10" rx="2.6" fill="none" stroke="#C9D3E2"/>'
             '<rect x="2" y="2" width="14" height="7" rx="1.4" fill="#81C784"/><rect x="21.3" y="3.6" width="2" height="3.8" rx="1" fill="#C9D3E2"/></svg>')

def phone(content, nav=0, modal='', flush=False, min_h=None, style=''):
    nv = ''
    if nav is not None:
        nv = '<div class="nav">' + ''.join(f'<div class="{"on" if i == nav else ""}"><b>{ic}</b>{lb}</div>' for i, (ic, lb) in enumerate(NAV)) + '</div>'
    mh = f'min-height:{min_h}px;' if min_h else ''
    return (f'<div class="phone"><div class="status"><span>14:32</span><span class="r">{_STATUS_R}</span></div>'
            f'<div class="scr{" flush" if flush else ""}" style="{mh}{style}">{content}{modal}</div>{nv}'
            f'<div class="gest"><i></i></div></div>')

def phone_land(content, w=700, h=330, style=''):
    return (f'<div class="phone land" style="width:{w}px;border-radius:30px"><div class="status" style="height:22px"><span>14:32</span><span class="r">{_STATUS_R}</span></div>'
            f'<div class="scr flush" style="height:{h}px;min-height:0;{style}">{content}</div></div>')

def modal(inner, top=120):
    return f'<div class="scrim"><div class="dlg" style="margin-top:{top}px">{inner}</div></div>'

def dialog(title, body, acts, n=None, np='tl'):
    a = ''.join(f'<span class="{c}">{t}</span>' for t, c in acts)
    return f'<h4>{title}</h4><p>{body}</p><div class="acts">{a}</div>'

def appbar(title, back=False, right='', sub=None):
    b = '<span class="info" style="font-size:20px;margin-right:6px">‹</span>' if back else ''
    s = f'<div class="muted" style="margin-top:1px">{sub}</div>' if sub else ''
    return f'<div class="row mb10" style="align-items:center">{b}<div class="grow"><div style="font-size:17px;font-weight:700">{title}</div>{s}</div>{right}</div>'

def head_row(title, right=''):
    return f'<div class="row mb10"><div class="b7" style="font-size:19px">{title}</div><div class="grow"></div>{right}</div>'

def hdr_pills(items):
    return '<div class="row" style="flex-wrap:wrap;gap:6px;margin-bottom:10px">' + ''.join(items) + '</div>'

def warn_line(t='Yüksek hit oranı tek başına avantaj kanıtı değildir.', n=None, np='tl'):
    return banner('warn', t, n=n, np=np)

def sec_title(t, right=''):
    return f'<div class="row" style="margin:12px 0 6px"><div class="hd fs11 b7" style="letter-spacing:.8px;text-transform:uppercase">{t}</div><div class="grow"></div>{right}</div>'

def flow(active, n=None, np='tl'):
    names = ['Geçmiş', 'Tahmin', 'LOCK', 'Gerçek sonuç', 'Değerlendirme', 'Öğrenme']
    out = ''
    for i, nm in enumerate(names):
        c = 'done' if i < active else ('act' if i == active else '')
        ic = '✓' if i < active else ('🔒' if (i == 2 and i == active) else i + 1)
        out += f'<div class="st {c}"><i>{ic}</i>{nm}</div>'
        if i < len(names) - 1: out += '<div class="ln"></div>'
    return f'<div class="flow"{A(n, np)}>{out}</div>'

def cand_row(c, show_bar=True, n=None, np='tl'):
    nn, k = c['n'], c['k']
    sec, reg = sector(nn), region(nn)
    sp = span(nn, k, c.get('d', 'bi'))
    lift = c['p'] / BASE_EXACT
    r1 = ' r1' if c['rank'] == 1 else ''
    sp_html = ' '.join(f'<span class="{"gold b7" if x == nn else ""}">{x}</span>' for x in sp)
    return (f'<div class="cand"{A(n, np)}><div class="rk{r1}">{c["rank"]}</div>'
            f'<div class="nm">{nn}<small>-k{k}</small></div>'
            f'<div class="meta"><b>S{sec}</b> · <b>{REGION_NAMES[region(nn)]}</b><br><span class="mono" style="font-size:10px">{sp_html}</span></div>'
            f'<div class="pp">{pc(c["p"], 1)}<small>×{f(lift, 2)} taban</small></div></div>')

def table_row(label, pick, p, base, n=None, np='tl'):
    d = p - base
    return (f'<div class="kv"{A(n, np)}><div class="k"><b>{label}</b><small>{pick} · taban {pc(base, 1)}</small></div>'
            f'<div style="width:96px">{bar(p, "#1976D2", base=base, mx=60)}</div>'
            f'<div class="v" style="width:78px">{pc(p, 1)} <span class="fs10 {"ok" if d > 0 else "bad"}">{sg(d, 1, "")}</span></div></div>')

def recent_strip(ns=None, size='s', n=None, np='tl'):
    ns = ns or LAST8
    return f'<div class="row" style="gap:5px"{A(n, np)}>' + ''.join(chip(x, size) for x in ns) + '</div>'

def spacer(h=8): return f'<div style="height:{h}px"></div>'

# ───────────────────────── sayfa kabuğu ─────────────────────────
def build_page(meta, body, idx, total, css, js, fonts):
    items = ''
    for it in meta['legend']:
        n, t, d = it[0], it[1], it[2]
        ref = f'<span class="ref">{it[3]}</span>' if len(it) > 3 else ''
        nb = f'<div class="n">{n}</div>' if n != '•' else '<div class="n o">•</div>'
        items += f'<div class="li">{nb}<div><b>{t}</b> — {d}{ref}</div></div>'
    refs = f'<div class="refs"><b>Prompt maddeleri:</b> {meta["refs"]}</div>' if meta['refs'] else ''
    legend = (f'<div class="legend"><h3>{meta["legend_title"]}</h3>{items}{refs}</div>') if (items or refs) else ''
    eyebrow = f'{APP} · EKRAN {idx:02d} / {total:02d} · {meta["section"]}'
    return f'''<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=480">
{fonts}<style>{css}</style></head><body>
<div class="sheet" id="sheet"><div class="eyebrow">{eyebrow}</div><h1>{meta["title"]}</h1><div class="sub">{meta["sub"]}</div>
<div class="stage{" wide" if meta["wide"] else ""}">{body}</div>{legend}
<div class="foot">Değerler yalnızca örnektir · Okunabilirlik için büyütülmüş tasarım önizlemesidir, gerçek cihaz görüntüsü değildir.<br>Hiçbir sonuç garanti değildir.</div></div>
<script>{js}</script></body></html>'''
