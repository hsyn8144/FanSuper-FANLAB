# -*- coding: utf-8 -*-
"""Glif denetimi: üretilen HTML'deki ASCII dışı her karakter, gömülü yazı tiplerinden en az birinde var mı?
Kutucuk (tofu) hatasını görsele bakmadan yakalar.  Çalıştır:  /home/user/render-tools/venv/bin/python glyphcheck.py
"""
import os, re, sys, glob, html
from html.parser import HTMLParser
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.environ.get('LRA_FONTS', '/home/user/render-tools/node_modules/@fontsource')
sys.path.insert(0, HERE)
import build as B

def parse_ranges(spec):
    out = []
    for part in spec.split(','):
        part = part.strip().replace('U+', '')
        if '-' in part:
            a, b = part.split('-'); out.append((int(a, 16), int(b, 16)))
        else:
            out.append((int(part, 16), int(part, 16)))
    return out

def in_ranges(cp, rngs): return any(a <= cp <= b for a, b in rngs)

def cmap_of(path):
    f = TTFont(path); c = set(f.getBestCmap().keys()); f.close(); return c

def covered_sets():
    sets = {}
    rob = set()
    for p in glob.glob(f'{FONTS}/roboto/files/roboto-*-{{400,500,700,900}}-normal.woff2'.replace('{400,500,700,900}', '*')):
        if re.search(r'-(400|500|700|900)-normal\.woff2$', p): rob |= cmap_of(p)
    sets['Roboto'] = rob
    mono = set()
    for p in glob.glob(f'{FONTS}/roboto-mono/files/*-normal.woff2'):
        if re.search(r'-(400|500|700)-normal\.woff2$', p): mono |= cmap_of(p)
    sets['RobotoMono'] = mono
    a = cmap_of(f'{FONTS}/noto-sans-symbols/files/noto-sans-symbols-symbols-400-normal.woff2')
    ra = parse_ranges(B.LRSYM_ARROWS)
    sets['LRSym-oklar'] = {c for c in a if in_ranges(c, ra)}
    b = cmap_of(f'{FONTS}/noto-sans-symbols-2/files/noto-sans-symbols-2-symbols-400-normal.woff2')
    rb = parse_ranges(B.LRSYM_SHAPES)
    sets['LRSym-şekiller'] = {c for c in b if in_ranges(c, rb)}
    emo = set()
    for p in glob.glob(f'{FONTS}/noto-color-emoji/files/*emoji-400-normal.woff2'):
        emo |= cmap_of(p)
    sets['Emoji'] = emo
    return sets

class T(HTMLParser):
    def __init__(self): super().__init__(); self.buf = []; self.skip = 0
    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style'): self.skip += 1
    def handle_endtag(self, tag):
        if tag in ('script', 'style') and self.skip: self.skip -= 1
    def handle_data(self, d):
        if not self.skip: self.buf.append(d)

def main():
    sets = covered_sets()
    allcov = set().union(*sets.values())
    bad = {}
    for p in sorted(glob.glob(os.path.join(B.OUT, '*.html'))):
        name = os.path.basename(p)
        if name.startswith('_'): continue
        t = T(); t.feed(open(p, encoding='utf-8').read())
        text = html.unescape(''.join(t.buf))
        for ch in set(text):
            cp = ord(ch)
            if cp < 128 or cp in (0xFE0F, 0xFE0E, 0x200D, 0xA0, 0x2009, 0x200A, 0x202F): continue
            if cp not in allcov:
                bad.setdefault(ch, []).append(name[:2])
    # emoji + simge birlikte kapsanıyor mu — ama emoji ile "çalınan" ASCII-dışı metin karakteri var mı?
    if bad:
        print('KAPSANMAYAN GLİFLER (kutucuk çıkar):')
        for ch, files in sorted(bad.items(), key=lambda x: ord(x[0])):
            print(f'  U+{ord(ch):04X} {ch!r}  → sayfalar: {sorted(set(files))}')
        return 1
    print(f'glif denetimi temiz ({len(allcov)} kod noktası kapsanıyor)')
    return 0

if __name__ == '__main__':
    sys.exit(main())
