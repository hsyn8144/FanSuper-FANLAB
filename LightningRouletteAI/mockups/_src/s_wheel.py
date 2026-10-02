# -*- coding: utf-8 -*-
"""C · WHEEL & MASA"""
from common import *

SEC = 'C · WHEEL & MASA'
WTABS = ['Wheel', 'Masa', 'Sektörler']

def wm_head(sel, n=None):
    return head_row('🎡 Wheel / Masa', pill('Ayrı koordinat sistemleri', 'line xs')) + seg(WTABS, sel, n=n, np='TL')

def layer(txt, on, n=None, np='tl'):
    return pill(('✓ ' if on else '') + txt, 'blue' if on else 'line', n=n, np=np, style='font-size:11.5px;padding:5px 10px')

@sheet('wheel', SEC, 'Wheel · fiziksel çark',
       'Gerçek Avrupa wheel sırası (saat yönü). Adaylar, komşu aralıkları, sektörler, bölgeler ve SON 8 aynı çark üzerinde.',
       legend=[
           (1, 'Wheel / Masa / Sektörler', 'Aynı sekmenin üç görünümü. Wheel fiziksel çarktır; Masa ayrı bir koordinat sistemidir; ikisi birbirine karıştırılmaz.', '§2 · §63'),
           (2, 'Fiziksel çark', 'Sıra: 0 · 32 · 15 · 19 · 4 · 21 · 2 · 25 · 17 · 34 … (saat yönü). Kırmızı/siyah/yeşil gerçek renkler.', '§2'),
           (3, 'Aday işaretleri', 'Renkli halka = aday merkezi, dış renkli yay = komşu aralığı (k), yuvarlak rozet = rank 1–5. Örn. 29-k2: 22·18·29·7·28.', '§2 · §4'),
           (4, 'SON 8', 'İç kenardaki beyaz noktalar; 1 = en yeni (17). Beyaz çerçeve = son sonuç.', '§12'),
           (5, 'Sektör ve bölge', 'S1…S9 sektör çizgileri/etiketleri; renkli iç yay: VOISINS · TIERS · ORPHELINS. Sektör tanımı konfigüre edilebilir (Sektörler sekmesi).', '§3'),
           (6, 'Katman anahtarları', 'Aday · Sektör · Bölge · SON 8 katmanlarını aç/kapat.'),
           (7, 'k ve yön', 'k1/k2/k3 = her yana kaç komşu. ↔ iki yön · L saat yönünün tersi · R saat yönü (17-L2, 17-R1 gibi).', '§2'),
           (8, 'Seçili cep', 'Çarkta bir numaraya dokununca: indeks, renk, sektör, bölge, komşular ve son sonuca mesafe.', '§1 · §2'),
           (9, 'Fiziksel sıra', '37 numaranın saat yönündeki dizilişi (masa sırası değil).', '§2'),
       ], refs='§1 · §2 · §3 · §4 · §12 · §63')
def s_wheel():
    w = ch.svg_wheel(366, cands=[(c['n'], c['k'], c['rank']) for c in CANDS], last=17, recent=LAST8, sectors=True, regions=True, margin=40, ring=36, label_fs=9.5, hub=('17', 'son sonuç', '#E6EDF6'))
    leg = ('<div class="row" style="gap:10px;flex-wrap:wrap;justify-content:center;font-size:10.5px;color:#9FB3CC">'
           + ''.join(f'<span><i style="display:inline-block;width:9px;height:9px;border-radius:50%;background:{ch.RANKC[i]};margin-right:4px"></i>{i + 1}. {c["n"]}-k{c["k"]}</span>' for i, c in enumerate(CANDS)) + '</div>'
           '<div class="row" style="gap:12px;justify-content:center;font-size:10.5px;color:#9FB3CC;margin-top:5px">'
           + ''.join(f'<span><i style="display:inline-block;width:14px;height:5px;border-radius:2px;background:{ch.REGION_COL[k]};margin-right:4px"></i>{REGION_NAMES[k]}</span>' for k in 'VTO') + '</div>')
    layers = f'<div class="row" style="gap:6px;flex-wrap:wrap"{A(6, "L")}>{layer("Aday", True)}{layer("Sektör", True)}{layer("Bölge", True)}{layer("SON 8", True)}</div>'
    kd = (f'<div class="row mt8" style="gap:6px;flex-wrap:wrap"{A(7, "L")}>{pill("k1", "line")}{pill("k2", "blue")}{pill("k3", "line")}<span class="dim" style="margin:0 4px">|</span>'
          f'{pill("↔ iki yön", "blue")}{pill("L ↺", "line")}{pill("R ↻", "line")}</div>').replace('↺', '←').replace('↻', '→')
    sel = (kv('Wheel indeksi · renk', '30 · siyah', mono=True) + kv('Sektör · bölge', f'S{sector(29)} · VOISINS')
           + kv('k2 aralığı (↔)', chips(span(29, 2), 'xs', n=None), ) + kv('L2 · R2', f'{" ".join(str(x) for x in span(29, 2, "L"))}  ·  {" ".join(str(x) for x in span(29, 2, "R"))}', mono=True)
           + kv('17 → 29 mesafe', 'saat 22 · ters 15 · kısa 15', mono=True))
    seq = ''
    for r in range(0, 37, 13):
        seq += '<div class="row" style="gap:3px;margin-bottom:3px">' + ''.join(chip(x, 'xs') for x in WHEEL[r:r + 13]) + '</div>'
    c = wm_head(0, n=1) \
        + card(None, '<div style="display:flex;justify-content:center">' + marks(w, [(2, 4, 4, 'tl'), (3, 7.7, 33, 'TL'), (4, 75.4, 44.5, 'c'), (5, 57.6, 28.6, 'c')]) + f'</div>{leg}', style='padding:8px 4px 10px') \
        + card('KATMANLAR VE AYAR', layers + kd, n=None) \
        + card('SEÇİLİ CEP · 29', sel, n=8, right=pill('dokun: başka cep', 'line xs')) \
        + card('FİZİKSEL SIRA (saat yönü)', f'<div{A(9, "L")}>{seq}</div><div class="mini">0 → 32 → 15 → … → 26 → 0 · sektör sınırları: 0–4 | 5–8 | 9–12 | 13–16 | 17–20 | 21–24 | 25–28 | 29–32 | 33–36 (indeks)</div>')
    return phone(c, nav=2)

@sheet('masa', SEC, 'Masa · bahis düzeni',
       'Masa görünümü wheel’den bağımsız bir koordinat sistemidir. TABLE kategorileri burada işaretlenir.',
       legend=[
           (1, 'Masa sekmesi', 'Masa, fiziksel wheel sırasından bağımsızdır: 1–36 üç sütun × on iki satır, 0 üstte. Wheel sırası burada kullanılmaz.', '§2 · §63'),
           (2, 'Sayı ızgarası', 'Gerçek masa düzeni ve renkleri. Son 8 sonuç beyaz noktalarla (1 = en yeni) gösterilir.', '§5 · §63'),
           (3, 'Dış bahisler', '1–18 · ÇİFT · KIRMIZI · SİYAH · TEK · 19–36 (sol), dozenlar (1./2./3. 12) ve sütunlar (alt). Altın çerçeve = modelin o kategorideki seçimi.', '§5'),
           (4, 'Table olasılıkları', 'Her kategori için kalibre olasılık, tesadüf tabanı ve fark. Table modelleri kendi feature’larıyla öğrenir; sayı tahmininin türevi değildir.', '§5 · §64'),
           (5, 'Wheel ↔ Table', 'Wheel rank-1 adayının Table seçimleriyle uyumu (agreement). Table yalnızca Wheel’i tekrar ediyorsa bağımsız teyit sayılmaz; hata korelasyonu burada izlenir.', '§6 · §45'),
           (6, 'Kategori anahtarları', 'Hangi Table kategorilerinin hesaplanıp gösterileceği (varsayılan: beşi de).'),
       ], refs='§2 · §5 · §6 · §45 · §63 · §64')
def s_masa():
    tb = ch.svg_table(recent=LAST8)
    probs = ''.join(table_row(a, b, c_, d) for a, b, c_, d in TABLE)
    agree = (kv('29 → SİYAH · TEK · BÜYÜK', '<span class="ok b7">✓ ✓ ✓</span>') + kv('29 → 2. DOZEN · 2. COLUMN', '<span class="bad b7">✗</span> <span class="ok b7">✓</span>')
             + kv('Uyum (agreement)', '<b>4/5</b>') + kv('Wheel–Table hata korelasyonu', 'φ = 0,03 · bağımsız', 'ok', mono=True))
    sw_ = ''.join(setrow(t, None, sw(True)) for t in ['COLOR · kırmızı/siyah', 'PARITY · tek/çift', 'HIGH/LOW · 1–18 / 19–36', 'DOZEN · 1/2/3', 'COLUMN · 1/2/3'])
    c = wm_head(1, n=1) + card(None, '<div style="display:flex;justify-content:center">' + marks(tb, [(2, 96, 3, 'TR'), (3, 2, 56, 'L')]) + '</div>', style='padding:10px 6px') \
        + card('TABLE OLASILIKLARI', probs, n=4, right=pill('kalibre', 'line xs')) + card('WHEEL ↔ TABLE UYUMU', agree, n=5) \
        + card('KATEGORİLER', sw_, n=6)
    return phone(c, nav=2)

@sheet('sektorler', SEC, 'Sektörler ve bölgeler',
       'Sektör ve bölge tanımları fiziksel wheel sırasına göre yapılır ve ayarlanabilir. Burada varsayılan 9 sektör ve 3 klasik bölge.',
       legend=[
           (1, 'Sektörler sekmesi', 'Sektör tanımı wheel’in orijinal sırasına göre yapılır ve konfigüre edilebilir.', '§3'),
           (2, 'Mini çark', 'Sektörler renkli dilimlerle; S1 daha geniş (5 cep), diğerleri 4 cep.', '§3'),
           (3, 'Sektör listesi', 'Her sektörün cepleri ve boyutu. − / + ile komşu sektör sınırı bir cep kaydırılır.', '§3'),
           (4, 'Boyut düzeltmeli taban', 'Sektör büyüklüğü farklı olduğundan taban = cep sayısı / 37. Sektör isabetleri bu tabanla karşılaştırılır.', '§23 · §64'),
           (5, 'Bölgeler', 'VOISINS (17) · TIERS (12) · ORPHELINS (8): klasik rulet bölgeleri; boyut düzeltmeli taban kullanılır.', '§3'),
           (6, 'Uyarı', 'Tanım değişirse sektör/bölge feature’ları yeni sürümle yeniden hesaplanır; canlı model etkilenmez, LAB’da yeni feature version oluşur.', '§38 · §40'),
           (7, 'Varsayılana dön / Kaydet', 'Kaydet yeni bir feature sürümü oluşturur ve LAB’a yeniden hesaplama önerir.'),
       ], refs='§3 · §23 · §38 · §40 · §64')
def s_sektorler():
    SC = ['#5C6BC0', '#26A69A', '#FFA726', '#EC407A', '#7E57C2', '#42A5F5', '#66BB6A', '#FF7043', '#8D6E63']
    # mini çark: sektör renkli dilimler
    size = 200; cx = cy = size / 2; R = 92; r0 = 52; step = 360 / 37
    o = [f'<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}">']
    for si, (a, b) in enumerate(SECTOR_SPLIT):
        for i in range(a, b):
            o.append(f'<path d="{ch.wedge(cx, cy, r0, R, i * step - step / 2, i * step + step / 2)}" fill="{SC[si]}" stroke="#0F1726" stroke-width="1"/>')
        mid = ((a + b - 1) / 2) * step; x, y = ch.pol(cx, cy, (R + r0) / 2, mid)
        o.append(f'<text x="{x:.1f}" y="{y + 4:.1f}" font-size="12" font-weight="900" fill="#fff" text-anchor="middle">S{si + 1}</text>')
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{r0 - 4}" fill="#111B2E"/><text x="{cx}" y="{cy - 2}" font-size="11" fill="#8AA3C4" text-anchor="middle">9 sektör</text><text x="{cx}" y="{cy + 13}" font-size="9.5" fill="#6F7F96" text-anchor="middle">varsayılan</text></svg>')
    mini = ''.join(o)
    rows = ''
    for si, nums in enumerate(SECTORS):
        n_ = len(nums); base = n_ / 37 * 100
        rows += (f'<div class="row" style="padding:5px 0;border-bottom:1px solid #1E2A3D"><span class="b7" style="width:26px;color:{SC[si]}">S{si + 1}</span>'
                 f'<div class="grow">{chips(nums, "xs")}</div><span class="mono dim fs11" style="width:64px;text-align:right">{n_} cep · {pc(base, 1)}</span>'
                 f'<span class="pill line xs" style="margin-left:4px">− +</span></div>')
    reg = ''
    for k, nums in (('V', sorted(VOISINS, key=idx)), ('T', sorted(TIERS, key=idx)), ('O', sorted(ORPH, key=idx))):
        reg += (f'<div style="margin:6px 0"><div class="row sb"><b style="color:{ch.REGION_COL[k]};font-size:12px">{REGION_NAMES[k]}</b><span class="mono dim fs11">{len(nums)} cep · taban {pc(len(nums) / 37 * 100, 1)}</span></div>'
                f'<div class="mt4">{chips(nums, "xs", gap=2)}</div></div>')
    c = wm_head(2, n=1) + card(None, f'<div style="display:flex;justify-content:center"{A(2, "TL")}>{mini}</div>') \
        + card('SEKTÖRLER (wheel sırasına göre)', rows, n=3, right=pill('düzenlenebilir', 'blue xs')) \
        + card('BOYUT DÜZELTMELİ TABAN', kv('S1 (5 cep)', pc(5 / 37 * 100, 1), mono=True) + kv('S2…S9 (4 cep)', pc(4 / 37 * 100, 1), mono=True) + muted('Sektör isabeti, sektörün cep sayısına göre tabanla kıyaslanır.'), n=4) \
        + card('BÖLGELER', reg, n=5) \
        + banner('warn', 'Tanım değişirse sektör/bölge feature’ları yeni feature sürümüyle yeniden hesaplanır. Canlı model etkilenmez.', n=6) \
        + btns(btn('Varsayılana dön', 'ghost'), btn('Kaydet', n=7, np='TL'))
    return phone(c, nav=2)
