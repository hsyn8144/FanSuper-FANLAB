# -*- coding: utf-8 -*-
"""H · OVERLAY"""
from common import *
from ovl import *

SEC = 'H · OVERLAY'
BG_LABEL = 'Başka bir uygulama · canlı yayın alanı (örnek arka plan)'

def pos(html, left=None, right=None, top=14, bottom=None, z=5):
    s = f'position:absolute;top:{top}px;z-index:{z};' if bottom is None else f'position:absolute;bottom:{bottom}px;z-index:{z};'
    if left is not None: s += f'left:{left}px;'
    if right is not None: s += f'right:{right}px;'
    return f'<div style="{s}">{html}</div>'

def ov_phone(inner, min_h=660, label=BG_LABEL, lines=5, align='flex-end'):
    return phone(f'{fakebg(label=label, lines=lines, align=align)}{inner}', nav=None, flush=True, min_h=min_h)

FULL_ANN = {'card': 10, 'head': 1, 'next': 2, 'table': 3, 'recent': 4, 'rbox': 5, 'digits': 6, 'del': 7, 'zero': 8, 'enter': 9}

@sheet('overlay_dikey', SEC, 'Overlay · dikey (standart)',
       'Başka bir uygulamanın üzerinde yüzen mavi kart: NEXT, TABLE, SON 8, sonuç kutusu ve 0–36 klavye. FanSuper 1.4 overlay’inin yeni uygulamaya uyarlanmış hâli.',
       legend=[
           (1, 'Başlık çubuğu', 'Tutamaç (sürükle) · ⚠ uyarı · 🔒 kilit · ▾ küçült · ✕ kapat. Her simgenin ayrıntısı bir sonraki sayfada (Ekran 59).', '§12'),
           (2, 'NEXT', '3–5 aday, “X-kN” biçiminde: büyük sayı = merkez, altındaki k = komşu aralığı (29-k2, 8-k2, 21-k2, 17-k3, 14-k1). Altın çerçeve = rank 1. Kilitli tahminden gelir.', '§4 · §12'),
           (3, 'TABLE', 'SİYAH · TEK · BÜYÜK · 2. DOZEN · 2. COLUMN: modelin her kategoride seçtiği taraf ve kalibre yüzdesi (turuncu). Seçilen taraf değişirse etiket de değişir (ör. KIRMIZI).', '§5 · §12'),
           (4, 'SON 8 SONUÇ', 'En yeni sonuç solda: 17 | 2 | 31 | 0 | 14 | 8 | 21 | 4. Rulet renkleriyle.', '§12'),
           (5, 'Sonuç kutusu', 'Tek kutu; yalnızca 0–36 tamsayı kabul eder. Geçersizse kırmızı olur (Ekran 66).', '§13'),
           (6, 'Rakam tuşları', '3×4 klavye: 1 2 3 / 4 5 6 / 7 8 9 / DEL 0 ENTER. Tuşlara basınca kısa titreşim (ayarlanabilir).', '§13'),
           (7, 'DEL', 'Yazılan son rakamı siler. Kutu boşken ikinci kez basılırsa son kayıtlı spin silinir (onaylı).', '§13 · §60'),
           (8, '0', 'Sıfır rakamı.', '§13'),
           (9, 'ENTER', 'Kaydeder: önceki kilitli tahmin değerlendirilir, sistem öğrenir, yeni tahmin kilitlenir. Çift ENTER ikinci kayıt oluşturmaz.', '§13 · §14 · §69'),
           (10, 'Kart', 'Yarı saydam mavi kart. Kartın dışına dokunuşlar arkadaki uygulamaya geçer. Boyut ve saydamlık Ayarlar › Overlay’de.', '§12'),
       ], refs='§4 · §5 · §12 · §13 · §14 · §60 · §69')
def s_ov_dikey():
    return ov_phone(pos(ov('full', ann=FULL_ANN), left=14, top=18))

@sheet('overlay_baslik', SEC, 'Overlay · başlık çubuğu ve tuşlar (yakın plan)',
       'Başlık çubuğundaki her simge ve klavye tuşlarının durumları, 1.85× büyütülmüş.',
       legend=[
           (1, 'Ad', '“⚡ LR·AI” — Lightning Roulette AI overlay’i. Dokununca uygulamayı açar.'),
           (2, 'Tutamaç', 'Basılı tutup sürükle; bırakınca en yakın kenara yapışır (ayarlanabilir) ve konum hatırlanır.', '§12'),
           (3, 'Uyarı simgesi', 'Uyarı varsa amber ⚠ görünür; dokununca kısa uyarı listesi açılır (Düşük sample, OOS zayıf, Calibration zayıf, Parameter stability yok, Baseline’dan anlamlı ayrışma yok, Leakage şüphesi).', '§55'),
           (4, 'Kilit', '🔒 tahmin kilitli (sonuç bekleniyor). ⏳ tahmin yok / öğreniyor. Kilitliyken NEXT ve TABLE değişmez.', '§14'),
           (5, 'Küçült', '▾ kompakt görünüme geçirir; kompaktta ▴ geri genişletir.', '§12'),
           (6, 'Kapat', '✕ overlay servisini durdurur. Yeniden açmak için Ana’daki anahtar veya bildirim.', '§12'),
           (7, 'Normal tuş', 'Koyu mavi rakam tuşu.', '§13'),
           (8, 'Basılı tuş', 'Açık mavi + çerçeve ve kısa titreşim.', '§13'),
           (9, 'ENTER pasif', 'Kutu boşken veya değer geçersizken pasif (gri).', '§13'),
           (10, 'ENTER aktif', 'Geçerli bir sayı yazılınca yeşil olur.', '§13'),
       ], refs='§12 · §13 · §14 · §55')
def s_ov_baslik():
    ann = {'logo': 1, 'grip': 2, 'warn': 3, 'lock': 4, 'min': 5, 'close': 6}
    a = lambda k, np='tl': A(ann.get(k), np)
    head = f'<div class="ov" style="width:190px;background:rgba(21,101,192,.92);box-shadow:none;padding:9px 9px 3px">{ov_head(a)}</div>'
    z = f'<div style="zoom:1.85;display:flex;justify-content:center;padding:14px 0 4px">{head}</div>'
    rows = [['<span class="oi" style="font-size:15px">🔒</span>', 'Kilitli', 'Tahmin kilitli; sonuç bekleniyor.'], ['<span style="font-size:15px">⏳</span>', 'Tahmin yok', 'Öğreniyor; NEXT/TABLE “--”.'],
            ['<span class="warn" style="font-size:15px">⚠</span>', 'Uyarı var', 'Dokununca uyarı listesi.'], [pill('Py ERROR', 'red xs'), 'Python hatası', 'Kotlin-only fallback; Python sonucu değil.'],
            ['<b class="info" style="font-size:14px">▾ ▴</b>', 'Küçült / genişlet', 'Tam ↔ kompakt.'], ['<b style="font-size:14px">✕</b>', 'Kapat', 'Servisi durdurur.']]
    keys = ('<div class="ov" style="width:auto;padding:10px;background:rgba(21,101,192,.92)"><div class="kpo" style="grid-template-columns:repeat(4,1fr);margin:0;gap:8px">'
            f'<div{A(7, "T")}>7</div><div class="pr"{A(8, "T")}>7</div><div class="e dis"{A(9, "T")} style="font-size:10.5px">ENTER</div><div class="e"{A(10, "T")} style="font-size:10.5px">ENTER</div></div>'
            '<div class="row" style="justify-content:space-around;font-size:10px;color:#BBDEFB;margin-top:5px"><span>normal</span><span>basılı</span><span>pasif</span><span>aktif</span></div></div>')
    c = card('BAŞLIK ÇUBUĞU · 1,85× yakın plan', z) + card('SİMGE DURUMLARI', tbl(['Simge', 'Durum', 'Anlamı'], rows, al='lll')) \
        + card('TUŞ DURUMLARI', f'<div style="zoom:1.1">{keys}</div>')
    return f'<div class="board">{c}</div>'

@sheet('overlay_giris', SEC, 'Overlay · giriş sırasında',
       'Sonuç yazılırken: “21” yazıldı, “1” tuşuna basıldı, ENTER aktif. NEXT ve TABLE ENTER’a kadar değişmez.',
       legend=[
           (1, 'Sonuç kutusu', 'Yazılan sayı (21) büyük görünür. Geçerliyse kutu normal; 0–36 dışıysa kırmızı olur.', '§13'),
           (2, 'Basılı tuş', 'Basılan tuş açık mavi ve çerçeveli; kısa titreşim verir (Ayarlar › Overlay).', '§13'),
           (3, 'DEL', 'Son rakamı siler (“21” → “2”). Kutu boşken ikinci kez DEL: son kayıtlı spini silmek için onay (bkz. Ekran 09).', '§13 · §60'),
           (4, 'ENTER aktif', 'Geçerli değerde yeşil. Basılınca kayıt + değerlendirme + öğrenme + yeni kilit tek işlemde yapılır; çift ENTER yoksayılır.', '§13 · §14 · §69'),
           (5, 'NEXT ve TABLE', 'Kilitli tahminin parçalarıdır; sonuç kaydedilene kadar değişmez. Böylece gelecek veri tahmine sızamaz.', '§14'),
           (6, 'Kilit simgesi', '🔒 görünüyorsa tahmin kilitli.', '§14')],
       refs='§13 · §14 · §60 · §69')
def s_ov_giris():
    o = ov('full', typed='21', pressed='1', ann={'rbox': 1, 'pressed': 2, 'del': 3, 'enter': 4, 'next': 5, 'lock': 6})
    note = f'<div class="toast" style="position:static;width:120px;background:#16223A;color:#B7C4D8;border:1px dashed #3A4B6B;font-size:11px">ENTER’a kadar NEXT ve TABLE <b class="gold">değişmez</b> 🔒</div>'
    return ov_phone(pos(o, left=14, top=18) + pos(note, right=12, top=210))

@sheet('overlay_enter_sonrasi', SEC, 'Overlay · ENTER sonrası',
       '21 kaydedildi: önceki tahminin kısa değerlendirmesi görünür, yeni NEXT/TABLE ve güncel SON 8 gelir, kilit yeniden kapanır.',
       legend=[
           (1, 'Değerlendirme şeridi', 'Birkaç saniye görünür: 21 → Candidate ✓ (rank 3) · Region ✓ · Table 3/5. Dokununca uygulamadaki ayrıntı açılır; sonra kendiliğinden kaybolur.', '§47'),
           (2, 'Yeni NEXT', 'Yeni kilitli tahmin (PRED-0005216): 12-k2 | 33-k2 | 4-k2 | 20-k3 | 9-k1. Önceki tahminin üzerine yazılmaz; ikisi de kayıtlıdır.', '§14 · §47'),
           (3, 'Yeni TABLE', 'Seçilen taraflar değişebilir: KIRMIZI · ÇİFT · KÜÇÜK · 3. DOZEN · 1. COLUMN.', '§5'),
           (4, 'SON 8 güncellendi', 'Yeni sonuç (21) en solda; en eski (4) düştü.', '§12'),
           (5, 'Kilit', 'Yeni tahminle birlikte 🔒 yeniden kapandı.', '§14')],
       refs='§5 · §12 · §14 · §47')
def s_ov_enter():
    evs = '<b>21</b> → Candidate ✓ (rank 3) · Region ✓ · Table 3/5<br><span style="opacity:.8">PRED-0005216 kilitlendi · 4 sn</span>'
    o = ov('full', evs=evs, cands=NEXT2, table=TABLE2, recent=LAST8_2, ann={'evs': 1, 'next': 2, 'table': 3, 'recent': 4, 'lock': 5})
    return ov_phone(pos(o, left=14, top=18))

@sheet('overlay_yatay', SEC, 'Overlay · yatay',
       'Telefon yan çevrildiğinde veya “Yatay” seçildiğinde: solda tahminler, sağda klavye.',
       width=760,
       legend=[
           (1, 'Başlık', 'Aynı simgeler: tutamaç, ⚠, 🔒, ▾, ✕.', '§12'),
           (2, 'NEXT', 'Beş aday tek satırda.', '§12'),
           (3, 'TABLE', 'Beş satır iki sütunda (3 + 2) — dikeyle aynı veriler.', '§12'),
           (4, 'SON 8', 'En yeni solda.', '§12'),
           (5, 'Sonuç kutusu', 'Klavyenin üstünde, aynı doğrulama (0–36).', '§13'),
           (6, 'Klavye', '3×4 klavye daha küçük tuşlarla sağ sütunda; DEL, 0, ENTER aynı düzende.', '§13'),
           (7, 'Kart', 'Yatayda genişlik 430 dp, yükseklik ≈ 200 dp; bir oyunun/yayının alt köşesine rahat sığar.', '§12')],
       refs='§12 · §13')
def s_ov_yatay():
    o = ov('h', ann={'card': 7, 'head': 1, 'next': 2, 'table': 3, 'recent': 4, 'rbox': 5, 'digits': 6})
    inner = fakebg(label='Başka bir uygulama · yatay yayın (örnek arka plan)', lines=2) + pos(o, left=24, top=16)
    return phone_land(inner, w=700, h=330)

@sheet('overlay_kompakt', SEC, 'Overlay · kompakt',
       'Klavyesi gizli, yalnızca tahminleri gösteren küçük kart. “Klavyeyi aç” ile tam görünüme geçilir.',
       legend=[
           (1, 'Başlık', '▾ ile küçültülmüş durumda; ▴ ile genişler.', '§12'),
           (2, 'NEXT', 'Beş aday.', '§12'),
           (3, 'TABLE', 'Beş satır.', '§12'),
           (4, 'SON 8', 'Son sekiz sonuç.', '§12'),
           (5, 'Klavyeyi aç', 'Tam görünüme geçer; sonuç girmek için dokun. Çok az yer kaplar (≈ 300 dp yükseklik).', '§12 · §13')],
       refs='§12 · §13')
def s_ov_kompakt():
    o = ov('compact', ann={'head': 1, 'next': 2, 'table': 3, 'recent': 4, 'expand': 5})
    return ov_phone(pos(o, right=12, top=18), min_h=520, align='flex-start')

@sheet('overlay_metin', SEC, 'Overlay · minimal metin',
       'Prompt’taki sade biçim: NEXT, TABLE ve SON 8 yalnızca metin olarak. En az yer kaplar.',
       legend=[
           (1, 'NEXT:', 'Beş aday tek satırda: 29-k2 | 8-k2 | 21-k2 | 17-k3 | 14-k1.', '§12'),
           (2, 'Aday satırı', 'Dikeydeki çiplerle aynı veri; yalnızca metin.', '§4'),
           (3, 'TABLE:', 'Her satırda kategori ve yüzde: SİYAH %50 · TEK %50 · BÜYÜK %51 · 2. DOZEN %34 · 2. COLUMN %33.', '§5 · §12'),
           (4, 'SON 8:', '17 | 2 | 31 | 0 | 14 | 8 | 21 | 4 (en yeni solda).', '§12'),
           (5, 'Klavye simgesi', '⌨ dokununca klavye açılır (kompaktla aynı mantık); metin modunda klavye varsayılan gizlidir.', '§13'),
           (6, 'Kart', 'Saydam mavi, monospace yazı; boyut ve saydamlık ayarlanabilir.', '§12')],
       refs='§4 · §5 · §12 · §13')
def s_ov_metin():
    o = ov('text', kb=True, ann={'card': 6, 'hdr': 1, 'next': 2, 'table': 3, 'recent': 4, 'kb': 5})
    return ov_phone(pos(o, left=14, top=18), min_h=480)

@sheet('overlay_simge', SEC, 'Overlay · simge (bubble)',
       'Küçültülmüş en sade hâl: sürüklenebilir yuvarlak simge. Dokununca açılır, uzun basınca menü çıkar, durumu halkayla gösterir.',
       legend=[
           (1, 'Simge', 'Tek dokunuş: son kullanılan görünüme (dikey/kompakt/metin) genişler; tekrar dokunuş küçültür.', '§12'),
           (2, 'Kilit rozeti', 'Altın 🔒 tahmin kilitli; ⏳ tahmin yok.', '§14'),
           (3, 'Sürükleme', 'Herhangi bir yere taşınır; bırakınca en yakın ekran kenarına yapışır ve konum hatırlanır.', '§12'),
           (4, 'Uzun basma menüsü', 'Kompakt/dikey görünüme geç · Konumu sıfırla · Ayarlar · Overlay’i kapat.', '§12'),
           (5, 'Durum halkaları', 'Normal · amber halka = uyarı var · kırmızı halka = Python ERROR (Kotlin-only fallback) · soluk = öğreniyor.', '§55 · §56')],
       refs='§12 · §14 · §55 · §56')
def s_ov_simge():
    path = ('<svg width="390" height="260" viewBox="0 0 390 260" style="position:absolute;left:0;top:0;z-index:3;pointer-events:none">'
            '<path d="M326,128 C290,92 190,92 128,126" fill="none" stroke="#FFC531" stroke-width="2.6" stroke-dasharray="2 7" stroke-linecap="round"/>'
            '<polygon points="122,130 138,121 138,138" fill="#FFC531"/></svg>')
    ghost = pos('<div class="bubble" style="opacity:.38"></div>', left=64, top=112, z=4)
    main = pos(bubble(n=1, lock=True), right=14, top=104)
    menu = pos(f'<div class="ovmenu"{A(4, "TL")}><div>Kompakt görünüme geç</div><div>Dikey görünüm</div><div>Konumu sıfırla</div><div>Ayarlar</div><div style="color:#FF8A80">Overlay’i kapat</div></div>', right=14, top=178, z=6)
    drag = pos(f'<span{A(3, "c")} style="display:block;width:1px;height:1px"></span>', left=215, top=98, z=7)
    lockb = pos(f'<span{A(2, "c")} style="display:block;width:1px;height:1px"></span>', right=14, top=148, z=7)
    sts = ('<div class="row" style="gap:22px;justify-content:center"' + A(5, 'L') + '>' + bubble(None, True, '') + bubble(None, True, 'warn') + bubble(None, True, 'err') + bubble(None, False, 'learn') + '</div>'
           '<div class="row" style="gap:6px;justify-content:space-around;font-size:10px;color:#9FB3CC;margin-top:6px"><span>normal</span><span>uyarı</span><span>Python ERROR</span><span>öğreniyor</span></div>')
    return ov_phone(path + ghost + main + lockb + drag + menu + pos(f'<div style="background:rgba(11,18,32,.82);border-radius:14px;padding:12px 14px;width:350px">{sts}</div>', left=14, top=372), min_h=560, label='Başka bir uygulama (örnek arka plan)', align='flex-start')

@sheet('overlay_durumlar', SEC, 'Overlay · durumlar',
       'Öğreniyor, Python ERROR (açık fallback), geçersiz giriş ve çift ENTER durumlarında overlay’in davranışı.',
       width=520,
       legend=[
           (1, 'A · Öğreniyor', 'Yeterli veri (<50 spin) yokken NEXT ve TABLE “--” gösterir; tahmin uydurulmaz. Kilit yerine ⏳ görünür.', '§55 · §56'),
           (2, 'B · Python ERROR', 'Python hata verince uygulama çökmez: başlıkta “Py ERROR”, altında “Kotlin-only (fallback)”. Fallback Python sonucu gibi gösterilmez.', '§56'),
           (3, 'C · Geçersiz giriş', '37 gibi 0–36 dışı bir değer kutuyu kırmızı yapar, “0–36 DIŞI!” yazar ve ENTER pasifleşir; kayıt yapılmaz.', '§13'),
           (4, 'D · Çift ENTER', 'İkinci ENTER yoksayılır; duplicate oluşmaz. Kısa bir bilgi şeridi görünür.', '§13 · §69'),
           (5, 'Öğreniyor notu', 'İlerleme: 38 / 50 spin.', '§55'),
           (6, 'Fallback notu', 'Kotlin-only etiketi her zaman görünür olmalı.', '§56'),
           (7, 'Kırmızı kutu', 'Hata durumunun görsel işareti (renk + metin; yalnız renge bağlı değil).', '§13'),
           (8, 'Bilgi şeridi', 'Yoksayılan işlem kullanıcıya bildirilir.', '§69')],
       refs='§13 · §55 · §56 · §69')
def s_ov_durumlar():
    A_ = ov('compact', dash=True, lock=False, note='<b>Öğreniyor</b> · 38 / 50 spin<br>tahmin üretilmiyor', ann={'card': 1, 'note': 5}, recent=LAST8)
    B_ = ov('compact', pyerr=True, note='<b>Python ERROR</b> → Kotlin-only (fallback)<br>Python sonucu değildir', ann={'card': 2, 'note': 6})
    C_ = ov('full', typed='37', err=True, enter_dis=True, ann={'card': 3, 'rbox': 7})
    D_ = ov('full', evs='Aynı giriş zaten işlendi<br>ikinci ENTER yoksayıldı', ann={'card': 4, 'evs': 8})
    def cell(t, o): return f'<div><div class="mini c" style="margin-bottom:6px;color:#9FB3CC;font-weight:700">{t}</div><div style="display:flex;justify-content:center">{o}</div></div>'
    g = ('<div style="display:grid;grid-template-columns:1fr 1fr;gap:18px 8px;background:linear-gradient(165deg,#1F2F4D 0%,#0E1626 60%,#1A1230 100%);border-radius:18px;padding:14px 8px 16px">'
         + cell('A · ÖĞRENİYOR', A_) + cell('B · PYTHON ERROR', B_) + cell('C · GEÇERSİZ GİRİŞ', C_) + cell('D · ÇİFT ENTER', D_) + '</div>')
    return f'<div style="width:470px">{g}</div>'

@sheet('overlay_baglam', SEC, 'Overlay · başka uygulamanın üstünde',
       'Overlay gerçek kullanımda: arkada başka bir uygulama (ör. canlı yayın) açıkken kompakt kart üstte yüzer; kartın dışındaki dokunuşlar arkadaki uygulamaya gider.',
       legend=[
           (1, 'Arkadaki uygulama', 'Tam etkileşimli; overlay onu durdurmaz. Uygulamanın kendisi arka planda çalışmaya devam eder.', '§58'),
           (2, 'Overlay kartı', 'Kompakt görünüm; yarı saydam olduğu için arkası seçilebilir.', '§12'),
           (3, 'Dokunuş geçişi', 'Kartın dışındaki her dokunuş arkadaki uygulamaya iletilir.', '§12'),
           (4, 'Sürükleme', 'Kart başlıktan sürüklenir; konum hatırlanır, kenara yapışabilir.', '§12'),
           (5, 'Klavyeyi aç', 'Sonuç girerken kart tam görünüme geçer, bitince kompakta döner.', '§13')],
       refs='§12 · §13 · §58')
def s_ov_baglam():
    chat = ''.join(f'<div style="background:rgba(255,255,255,.07);border-radius:8px;height:22px;width:{w}%;margin:0 14px 8px"></div>' for w in (72, 48, 85, 60, 78, 40))
    o = ov('compact', ann={'card': 2, 'head': 4, 'expand': 5})
    hand = pos(f'<div style="font-size:30px;line-height:1"{A(3, "BR")}>👆</div>', left=40, top=420)
    ring = pos('<div style="width:56px;height:56px;border-radius:50%;border:2px dashed #FFC531;opacity:.8"></div>', left=24, top=408, z=4)
    mark1 = pos(f'<span{A(1, "c")} style="display:block;width:1px;height:1px"></span>', left=60, top=150, z=7)
    return phone(fakebg(label='Başka bir uygulama · canlı yayın', lines=1, align='flex-start') + f'<div style="position:absolute;left:0;right:0;top:262px">{chat}</div>' + mark1 + pos(o, right=12, top=18) + ring + hand, nav=None, flush=True, min_h=620)

@sheet('overlay_bildirim_izin', SEC, 'Overlay · izin ve bildirim akışı',
       'İlk kullanımda izin isteği, Android özel erişim ayarı ve overlay/LAB çalışırken görünen ön plan bildirimleri.',
       legend=[
           (1, 'Uygulama içi istek', 'Neden gerektiğini söyleyen onay diyaloğu (Ekran 57). “Ayarı aç” Android’in ilgili sayfasına götürür.', '§12'),
           (2, 'Android ayarı', 'Sistem sayfası (stilize gösterim): “Diğer uygulamaların üzerinde göster” anahtarı Lightning Roulette AI için açılır. Uygulama bu izin olmadan da çalışır; yalnızca overlay kullanılamaz.', '§12'),
           (3, 'Overlay bildirimi', 'Servis çalışırken zorunlu ön plan bildirimi: durum + kilitli tahmin kimliği; [Gizle] kartı küçültür, [Kapat] servisi durdurur.', '§12 · §49'),
           (4, 'LAB bildirimi', 'LAB arka planda çalışırken ilerleme ve [İptal]. Uygulama kapansa da checkpoint’ten devam eder.', '§49 · §71'),
           (5, 'Bildirim izni yoksa', 'Android 13+’ta bildirim izni verilmezse ilerleme yalnızca uygulama içinde görünür; işlev bozulmaz.', '§58')],
       refs='§12 · §49 · §58 · §71')
def s_ov_izin():
    st1 = card('1 · UYGULAMA İÇİ İSTEK', '<div class="dlg" style="width:auto;margin:0;position:relative;box-shadow:none;background:#1A2A47"><h4 style="font-size:15px">Overlay izni gerekli</h4><p style="font-size:12px">Overlay’in diğer uygulamaların üzerinde görünebilmesi için “Diğer uygulamaların üzerinde göster” izni verilmeli.</p><div class="acts"><span>Şimdi değil</span><span class="fl">Ayarı aç</span></div></div>', n=1)
    st2 = card('2 · ANDROID › ÖZEL UYGULAMA ERİŞİMİ', '<div class="andr"><div style="font-weight:700;margin-bottom:4px">Diğer uygulamaların üzerinde göster</div>'
               '<div class="ar"><div class="ai">🧭</div><div>Harita</div><div class="sw2"></div></div>'
               '<div class="ar"' + A(2, 'L') + '><div class="ai" style="background:#1565C0;color:#fff">⚡</div><div><b>Lightning Roulette AI</b><div style="font-size:10.5px;color:#5C6F87">İzin verildi</div></div><div class="sw2 on"></div></div>'
               '<div class="ar"><div class="ai">💬</div><div>Mesajlar</div><div class="sw2"></div></div></div>', n=None)
    st3 = card('3 · ÖN PLAN BİLDİRİMLERİ', '<div class="notif"' + A(3, 'L') + '><div class="nh"><span>⚡ Lightning Roulette AI</span><span>· şimdi</span></div><b>Overlay çalışıyor</b>Kilitli tahmin: PRED-0005215<div class="na"><span>GİZLE</span><span>KAPAT</span></div></div>'
               '<div style="height:8px"></div><div class="notif"' + A(4, 'L') + '><div class="nh"><span>🧪 LAB</span><span>· 14:05</span></div><b>LAB çalışıyor · %63</b>LAB-2026-0001847 · 657 / 1 043'
               '<div style="height:6px;border-radius:3px;background:#D7DEE8;margin-top:7px;overflow:hidden"><i style="display:block;height:100%;width:63%;background:#1565C0"></i></div><div class="na"><span>İPTAL</span></div></div>', n=None)
    st4 = banner('neutral', 'Bildirim izni verilmezse LAB ilerlemesi yalnızca uygulama içinde görünür; işlev bozulmaz.', n=5, icon='🔔')
    return f'<div class="board">{st1}{st2}{st3}{st4}</div>'
