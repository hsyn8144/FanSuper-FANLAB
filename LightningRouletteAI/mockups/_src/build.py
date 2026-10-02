# -*- coding: utf-8 -*-
"""Lightning Roulette AI · ekran tasarımı üretici.
Kullanım:
  python3 build.py html [slug,slug]   → _build/*.html (+ manifest.json)
  python3 build.py readme             → ../README.md (galeri) + ../thumbs
  python3 build.py zip                → ../LightningRouletteAI_Gorseller.zip
  (PNG + PDF üretimi: node render.mjs — bkz. _src/README.md)
"""
import sys, os, json, glob, zipfile, importlib, subprocess, re

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
OUT = os.path.join(HERE, '_build')
ROOT = os.path.abspath(os.path.join(HERE, '..'))                 # .../LightningRouletteAI/mockups
FONTS = os.environ.get('LRA_FONTS', '/home/user/render-tools/node_modules/@fontsource')

LRSYM_ARROWS = 'U+2190,U+2192,U+2194-2199'
LRSYM_SHAPES = 'U+232B,U+23CE,U+25A0-25FF,U+2605-2606,U+2610-2612,U+2630,U+2713-2717,U+2759-275B,U+276E-2771'
MODULES = ['s_intro', 's_launch', 's_home', 's_wheel', 's_council', 's_lab', 's_lab2', 's_data', 's_settings', 's_overlay']

def load():
    import lib
    for m in MODULES:
        if os.path.exists(os.path.join(HERE, m + '.py')):
            importlib.import_module(m)
    return lib

def fonts_html():
    links = []
    for pkg, ws in (('roboto', (400, 500, 700, 900)), ('roboto-mono', (400, 500, 700))):
        for w in ws:
            links.append(f'<link rel="stylesheet" href="file://{FONTS}/{pkg}/{w}.css">')
    links.append(f'<link rel="stylesheet" href="file://{FONTS}/noto-color-emoji/emoji.css">')
    # LRSym: ok + işaret karakterleri için sınırlı simge yazı tipi (emoji kod noktalarını ÇALMAZ)
    links.append('<style>'
        f"@font-face{{font-family:'LRSym';font-style:normal;font-weight:400;src:url(file://{FONTS}/noto-sans-symbols/files/noto-sans-symbols-symbols-400-normal.woff2) format('woff2');unicode-range:{LRSYM_ARROWS}}}"
        f"@font-face{{font-family:'LRSym';font-style:normal;font-weight:400;src:url(file://{FONTS}/noto-sans-symbols-2/files/noto-sans-symbols-2-symbols-400-normal.woff2) format('woff2');unicode-range:{LRSYM_SHAPES}}}"
        '</style>')
    return ''.join(links)

def numbered(lib):
    out = []
    for i, m in enumerate(lib.SHEETS):
        out.append((i, m))
    return out

def cmd_html(only=None):
    lib = load()
    css = open(os.path.join(HERE, 'style.css'), encoding='utf-8').read() + '\n' + open(os.path.join(HERE, 'style_extra.css'), encoding='utf-8').read()
    js = open(os.path.join(HERE, 'sheet.js'), encoding='utf-8').read()
    fonts = fonts_html()
    os.makedirs(OUT, exist_ok=True)
    total = len(lib.SHEETS)
    manifest = []
    for i, m in numbered(lib):
        slug = f'{i:02d}_{m["slug"]}'
        manifest.append(dict(file=slug, no=i, section=m['section'], title=m['title'], sub=m['sub'], slug=m['slug'], width=m.get('width') or 480))
        if only and not any(o in slug for o in only):
            continue
        body = m['fn']()
        page = lib.build_page(m, body, i, total - 1, css, js, fonts)
        with open(os.path.join(OUT, slug + '.html'), 'w', encoding='utf-8') as fh:
            fh.write(page)
    with open(os.path.join(OUT, 'manifest.json'), 'w', encoding='utf-8') as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=1)
    print(f'{total} sayfa tanımlı; HTML yazıldı → {OUT}')

SECTION_TITLES = {}

def cmd_readme():
    man = json.load(open(os.path.join(OUT, 'manifest.json'), encoding='utf-8'))
    png = os.path.join(ROOT, 'png'); thumbs = os.path.join(ROOT, 'thumbs')
    os.makedirs(thumbs, exist_ok=True)
    for m in man:
        src = os.path.join(png, m['file'] + '.png')
        if not os.path.exists(src):
            print('EKSİK PNG:', src); continue
        dst = os.path.join(thumbs, m['file'] + '.jpg')
        subprocess.run(['convert', src, '-resize', '300x', '-background', '#EEF2F7', '-alpha', 'remove', '-quality', '72', '-strip', '-interlace', 'Plane', dst], check=True)
    # bölümler
    secs = []
    for m in man:
        if not secs or secs[-1][0] != m['section']:
            secs.append((m['section'], []))
        secs[-1][1].append(m)
    L = []
    L.append('# ⚡ Lightning Roulette AI — Ekran Tasarımları\n')
    if os.environ.get('LRA_DRAFT'):
        L.append(f'> 🚧 **TASLAK:** sayfa üretimi sürüyor — şu an **{len(man)}** sayfa hazır, toplam yaklaşık 65 sayfa olacak. Bu bir ara sürümdür.\n')
    L.append('> **Durum:** Bu klasör **tasarım önizlemesidir** (referans). Uygulama bu tasarıma göre yazıldı: bkz. [`../README.md`](../README.md). Uygulamada gerçek veriye bağlı ekranlar (LAB sekmeleri, listeler) örnek sayılardan farklı değerler gösterir.\n')
    L.append('Her sayfa: telefon ekranı + **numaralı işaretler** + altında her sekme/buton/alanın ne yaptığını anlatan açıklama listesi + ilgili prompt maddeleri (§).')
    L.append('Tüm sayı ve yüzdeler **örnektir**; uygulama hiçbir sonucun garanti olduğunu iddia etmez (tahmin → LOCK → sonuç → değerlendirme → öğrenme).\n')
    L.append('## İndir\n')
    L.append('| Dosya | Ne işe yarar |\n|---|---|')
    L.append('| [`LightningRouletteAI_Tasarim_Katalogu.pdf`](LightningRouletteAI_Tasarim_Katalogu.pdf) | Tüm sayfalar tek PDF (telefonda kaydırarak gez) |')
    L.append('| [`LightningRouletteAI_Gorseller.zip`](LightningRouletteAI_Gorseller.zip) | Tüm PNG dosyaları tek ZIP (PDF ayrı dosyadır) |')
    L.append('| [`png/`](png) | Tek tek tam çözünürlüklü PNG sayfaları |\n')
    L.append(f'**Toplam {len(man)} sayfa.** Küçük resme dokununca tam boyutlu görsel açılır.\n')
    L.append('## İçindekiler\n')
    for s, items in secs:
        anchor = re.sub(r'[^a-z0-9ğüşıöç\- ]', '', s.lower()).strip().replace(' ', '-')
        L.append(f'- **{s}** — {len(items)} sayfa')
    L.append('')
    for s, items in secs:
        L.append(f'## {s}\n')
        for r in range(0, len(items), 3):
            row = items[r:r + 3]
            L.append('<table><tr>' + ''.join(
                f'<td align="center" valign="top" width="33%"><a href="png/{m["file"]}.png"><img src="thumbs/{m["file"]}.jpg" width="230" alt="{m["title"]}"></a><br><sub><b>{m["no"]:02d}</b> · {m["title"]}</sub></td>'
                for m in row) + ('<td></td>' * (3 - len(row))) + '</tr></table>')
        L.append('')
    L.append('## Onay\n')
    L.append('Beğendiğin / değiştirmek istediğin sayfaları **numarasıyla** söylemen yeterli (ör. “12 numaralı sayfada şunu değiştir”). '
             'Uygulama (Kotlin + Python, Room, overlay, LAB, test, README) bu tasarıma göre yazıldı ve CI ile derleniyor.\n')
    L.append('## Yeniden üretme\n')
    L.append('Görseller `_src/` altındaki stdlib-only Python üretici + Chromium ile oluşturulur (`_src/README.md`).\n')
    with open(os.path.join(ROOT, 'README.md'), 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(L))
    print('README.md + thumbs yazıldı')

def cmd_zip():
    zp = os.path.join(ROOT, 'LightningRouletteAI_Gorseller.zip')
    with zipfile.ZipFile(zp, 'w', zipfile.ZIP_STORED) as z:  # PNG zaten sıkışık
        for p in sorted(glob.glob(os.path.join(ROOT, 'png', '*.png'))):
            z.write(p, 'LightningRouletteAI_Gorseller/' + os.path.basename(p))
        rd = os.path.join(ROOT, 'README.md')
        if os.path.exists(rd):
            z.write(rd, 'LightningRouletteAI_Gorseller/README.md')
    print('ZIP:', zp, round(os.path.getsize(zp) / 1e6, 1), 'MB')

if __name__ == '__main__':
    c = sys.argv[1] if len(sys.argv) > 1 else 'html'
    if c == 'html':
        cmd_html(sys.argv[2].split(',') if len(sys.argv) > 2 else None)
    elif c == 'readme': cmd_readme()
    elif c == 'zip': cmd_zip()
    else: print(__doc__)
