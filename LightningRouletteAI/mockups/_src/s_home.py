# -*- coding: utf-8 -*-
"""B · ANA"""
from common import *

SEC = 'B · ANA'

@sheet('ana_tahmin', SEC, 'Ana · kilitli tahmin (NEXT + TABLE)',
       'Ana sekmenin üst bölümü: o an kilitli duran tahmin. Tahmin gerçek sonuç girilmeden önce üretilir ve değişmez.',
       legend=[
           (1, 'Başlık', 'Uygulama adı, sürüm ve saat.', '§1'),
           (2, 'Overlay anahtarı', 'Dokununca overlay servisi açılır/kapanır. İzin yoksa İzinler sayfasına gider (Ekran 05).', '§12'),
           (3, 'Durum çipleri', 'Aktif dataset sürümü ve spin sayısı · aktif Champion modeli · Python durumu (ERROR olursa kırmızı olur ve “Kotlin-only” fallback açıkça yazılır) · OFFLINE.', '§56 · §58'),
           (4, 'Yaşam döngüsü şeridi', 'Geçmiş → Tahmin → LOCK → Gerçek sonuç → Değerlendirme → Öğrenme. Şu an LOCK’tayız: gerçek sonuç bekleniyor.', '§14 · §47'),
           (5, 'Kilit kartı', 'Tahmin kimliği, referans spin, kilit saati, model ve dataset sürümü. Kilit atomiktir: aynı spin için ikinci tahmin üretilmez, kilitli tahmin sonradan değiştirilemez.', '§14 · §69'),
           (6, 'NEXT kartı', 'En az 3, en çok 5 aday, güven sırasıyla. Adaylar aynı bölgeden olmak zorunda değildir; mümkünse en az 2 farklı sector/region kaynağı bulunur. Karta dokununca Tahmin detayı açılır (Ekran 11).', '§4'),
           (7, 'Aday satırı', 'Sıra · numara-k (ör. 29-k2: 29 merkezli, her yana 2 komşu) · sektör ve bölge · komşu aralığı · kalibre P(exact) ve tesadüf tabanına oranı (×lift).', '§2 · §4 · §66'),
           (8, 'Kapsama satırı', 'Adayların birleşik kapsaması (22/37 = %59,5). Daha çok aday/komşu = daha yüksek kapsama; bu yüzden isabet, kapsama tabanıyla karşılaştırılır.', '§64 · §65'),
           (9, 'TABLE kartı', 'COLOR · PARITY · HIGH/LOW · DOZEN · COLUMN. Model her kategoride en olası tarafı seçer; sarı çizgi tesadüf tabanı. Table, sayı tahmininin türevi değildir.', '§5'),
           (10, 'Uyarı', 'Prompt’taki sabit uyarı metni her zaman görünür.', '§55'),
       ], refs='§1 · §4 · §5 · §12 · §14 · §55 · §64–§66 · §69')
def s_ana_tahmin():
    c = title_bar(n_title=1, n_ov=2) + status_pills(3) + card(None, flow(2, n=4), cls='') \
        + lock_card(5) + next_block(n_card=6, n_row=7, n_cov=8) + table_block(9) \
        + banner('warn', 'Yüksek hit oranı tek başına avantaj kanıtı değildir.', n=10)
    return phone(c, nav=0)

@sheet('ana_giris', SEC, 'Ana · canlı veri girişi ve özet',
       'Ana sekmenin alt bölümü: son 8 sonuç, tek sonuç kutusu + 0–36 klavye ve taban çizgisiyle karşılaştırmalı özet.',
       legend=[
           (1, 'SON 8 SONUÇ', 'En yeni solda: 17 | 2 | 31 | 0 | 14 | 8 | 21 | 4. “Tümü ›” Veri › Spinler sayfasını açar.', '§12'),
           (2, 'Tek sonuç kutusu', 'Yalnızca 0–36 tamsayı. Geçersiz değer kırmızı olur ve ENTER pasifleşir (Ekran 09).', '§13'),
           (3, 'Rakam tuşları', '1–9 arası tuşlar, 3×4 klavye: 1 2 3 / 4 5 6 / 7 8 9 / DEL 0 ENTER.', '§13'),
           (4, 'DEL', 'Yazılan son rakamı siler. Kutu boşken DEL, son kayıtlı spini silmek için onay ister (yanlış girilen son sonucu düzeltir).', '§13 · §60'),
           (5, '0', 'Sıfır; zero ayrıca özel analiz edilir (zero sonrası 1/2/3 spin, zero↔sector …).', '§1'),
           (6, 'ENTER', 'Kaydeder. Akış: kayıt → değerlendirme → öğrenme → yeni tahmin + LOCK. Çift ENTER ikinci kayıt oluşturmaz (idempotent).', '§13 · §14 · §69'),
           (7, 'Son spini geri al', 'FanSuper 1.4’teki O(1) geri alma: önceki anlık görüntüye döner; aynı kilitli tahmin geri gelir.', '§60'),
           (8, 'Özet tablosu', 'Son OOS penceresi: gözlenen oran, tesadüf tabanı, fark ve %95 güven aralığı. “Exact” ile “Candidate” kesinlikle ayrı satırlardır.', '§25 · §64'),
           (9, 'Sınıf rozeti', 'A–F, O, L, S sınıfı ve uyarı. Burada C: nötr; “Baseline’dan anlamlı ayrışma yok.”', '§44 · §55'),
           (10, 'Kısayollar', 'Tahmin detayı ve LAB Overview’a hızlı geçiş.'),
       ], refs='§12 · §13 · §14 · §25 · §44 · §55 · §60 · §64 · §69')
def s_ana_giris():
    rows = [('Exact (rank-1)', 2.97, 2.70, 2.0, 4.1), ('Candidate-5', 13.81, 13.51, 11.8, 16.0), ('Neighbor', 60.2, 59.46, 57.2, 63.2), ('Region', 36.5, 36.0, 33.6, 39.5), ('Color', 49.1, 48.65, 46.0, 52.2)]
    tb = tbl(['Ölçüt', 'Gözlenen', 'Taban', 'Fark (CI)'], [[a, f'<b>{pc(p, 1)}</b>', f'<span class="dim">{pc(b, 1)}</span>', f'{delta(p - b)} <span class="dim fs10">±{f((hi - lo) / 2, 1)}</span>'] for a, p, b, lo, hi in rows],
             al='lrrr', n=8, np='TL')
    c = recent_card(1, n_all=None) \
        + card('VERİ GİRİŞİ', keypad(None, nd=3, nz=5, ndel=4, nent=6, nbox=2, dis_enter=True)
               + f'<div class="btns mt8">{btn("Son spini geri al (17)", "ghost sm", n=7, np="TL")}</div>', right=pill('ENTER bekliyor', 'line xs')) \
        + card('SON 100 TAHMİN · TABANLA KARŞILAŞTIR', tb + f'<div class="row mt8" style="gap:8px"{A(9, "L")}>{cls_b("C")}<div style="font-size:12px;line-height:1.35"><b>Nötr.</b> Baseline’dan anlamlı ayrışma yok.<div class="dim fs11">n = 100 · düşük sample</div></div></div>', n=None, right=pill('OOS değil · canlı', 'line xs')) \
        + f'<div class="rowcard" {A(10, "L")}><div class="ic">🔎</div><div class="tt"><b>Tahmin detayı</b><span>Neden bu adaylar? Hesaplanan skorlar</span></div><span class="lnk">›</span></div>' \
        + '<div class="rowcard"><div class="ic">🧪</div><div class="tt"><b>LAB Overview</b><span>Walk-forward, OOS, robustness</span></div><span class="lnk">›</span></div>'
    return phone(c, nav=0)

@sheet('giris_durumlari', SEC, 'Canlı veri girişi · durumlar',
       'Sonuç kutusunun alabileceği tüm durumlar: boş, yazılıyor, geçersiz, kaydedildi, çift ENTER, DEL.',
       legend=[
           (1, 'Boş', 'ENTER pasif. Kutuda “—” görünür.', '§13'),
           (2, 'Yazılıyor', 'İmleç yanıp söner; ENTER aktif (değer 0–36 arasındaysa).', '§13'),
           (3, 'Geçersiz', '0–36 dışı (37, 99 …) kırmızı olur, ENTER pasif kalır; hiçbir şey kaydedilmez. Baştaki sıfır normalize edilir (07 → 7).', '§13 · §62'),
           (4, 'Kaydedildi', 'ENTER sonrası: spin #5 215 eklendi, önceki tahmin değerlendirildi, yeni tahmin kilitlendi. İşlem tek transaction’dır.', '§14 · §69'),
           (5, 'Çift ENTER', 'Aynı girişin ikinci ENTER’ı yoksayılır; ikinci spin oluşmaz.', '§13 · §69'),
           (6, 'DEL', 'Yazılı rakam varsa onu siler. Kutu boşsa son kayıtlı spini silmek için DEL’e ikinci kez basılması istenir (3 sn).', '§13'),
       ], refs='§13 · §14 · §62 · §69')
def s_giris_durumlari():
    def st(n, title, box, helper, tag, kind=''):
        return card(title, f'{box}<div class="mt8" style="font-size:12px;line-height:1.4">{helper}</div>', n=n, right=tag)
    c = st(1, 'BOŞ', keypad(None, dis_enter=True).split('<div class="kp')[0], 'Bir sayı gir (0–36).', pill('ENTER pasif', 'line xs')) \
        + st(2, 'YAZILIYOR', keypad('17').split('<div class="kp')[0], '17 geçerli. ENTER ile kaydedebilirsin.', pill('ENTER aktif', 'blue xs')) \
        + st(3, 'GEÇERSİZ', keypad('37', 'err').split('<div class="kp')[0], '<span class="bad">37 → 0–36 aralığı dışında.</span> ENTER pasif; kayıt yapılmaz.', pill('Geçersiz', 'red xs')) \
        + st(4, 'KAYDEDİLDİ (ENTER sonrası)', keypad('21', 'ok').split('<div class="kp')[0] + f'<div class="toast" style="position:static;margin-top:8px">Spin #5 215 kaydedildi · PRED-0005215 değerlendirildi · PRED-0005216 kilitlendi</div>', 'Tek transaction: kayıt + değerlendirme + öğrenme + yeni kilit.', pill('✓ tamam', 'green xs')) \
        + st(5, 'ÇİFT ENTER', f'<div class="rbox"><small>SONUÇ (0–36)</small>—</div><div class="toast" style="position:static;margin-top:8px">Aynı giriş zaten işlendi — ikinci ENTER yoksayıldı.</div>', 'Duplicate oluşmaz (idempotent).', pill('Yoksayıldı', 'amber xs')) \
        + st(6, 'DEL', keypad('1').split('<div class="kp')[0], 'Kutu boşken DEL: <b>“21 silinsin mi? DEL’e tekrar bas”</b> (3 sn içinde).', pill('Onaylı silme', 'amber xs'))
    return phone(c, nav=0)

@sheet('ana_degerlendirme', SEC, 'Ana · sonuç sonrası değerlendirme ve öğrenme',
       'ENTER’dan sonra gösterilen özet. Sonuç 21 girildi → önceki kilitli tahmin açıldı, değerlendirildi, sistem öğrendi, yeni tahmin kilitlendi.',
       legend=[
           (1, 'Gerçek sonuç', '21 · KIRMIZI · TEK · 19–36 · 2. dozen · 3. column · sektör S2 · VOISINS. Sonuç, tahmin kilitlendikten sonra açılır.', '§14'),
           (2, 'Exact ve Candidate ayrı', 'Exact: rank-1 merkez (29) ≠ 21 → ✗. Candidate: 5 merkezden biri 21 → ✓ (rank 3, 21-k2). İkisi kesinlikle ayrı raporlanır.', '§64'),
           (3, 'Sector · Region · Neighbor · Table', 'Sector ✗ (S8 ≠ S2) · Region ✓ (VOISINS) · Neighbor ✓ · Table 3/5 (SİYAH ✗, TEK ✓, BÜYÜK ✓, 2. DOZEN ✓, 2. COLUMN ✗).', '§47 · §64'),
           (4, 'Öğrenme zinciri', 'evaluation → exact/candidate → sector/region/table → model hatası → feature sonucu → rejim → veritabanı → artımlı güncelleme → yeni tahmin.', '§47'),
           (5, 'Model hatası ve feature sonucu', 'Hangi üyeler haklıydı/yanıldı ve hangi feature gruplarının işe yaradığı kaydedilir; ağırlıklar körlemesine değil, sınırlı adımlarla güncellenir.', '§67'),
           (6, 'Yeni tahmin', 'PRED-0005216 kilitlendi. NEXT ve TABLE yeni sonuçla yeniden hesaplanır; TABLE’da seçilen taraflar değişebilir (KIRMIZI, ÇİFT …).', '§14'),
           (7, 'Kapat', 'Özeti kapatır ve Ana’ya döner. Özet daha sonra Veri › Spinler’den de açılabilir.'),
       ], refs='§14 · §47 · §64 · §67')
def s_ana_degerlendirme():
    hit = lambda ok: '<span class="ok b7">✓</span>' if ok else '<span class="bad b7">✗</span>'
    res = (f'<div class="row" style="gap:12px">{chip(21, "xl")}<div class="grow"><div class="b7" style="font-size:15px">Sonuç: 21</div>'
           f'<div class="mini">KIRMIZI · TEK · 19–36 · 2. dozen · 3. column</div><div class="mini">S2 · VOISINS · spin #5 215</div></div>{pill("PRED-0005215", "gold xs")}</div>')
    ev1 = kv('Exact (rank-1 = 29)', hit(False)) + kv('Candidate (5 merkez)', hit(True) + ' <span class="dim fs11">rank 3 · 21-k2</span>')
    ev2 = (kv('Sector (S8 → S2)', hit(False)) + kv('Region (VOISINS)', hit(True)) + kv('Neighbor (kapsama 22/37)', hit(True))
           + kv('Table', '<b>3/5</b> <span class="dim fs11">TEK ✓ BÜYÜK ✓ 2.DOZEN ✓</span>'))
    ev = f'<div{A(2, "L")}>{ev1}</div><div class="sep"></div><div{A(3, "L")}>{ev2}</div>'
    steps = [('Değerlendirme', 'PRED-0005215 → gerçek 21'), ('Exact / Candidate', '✗ / ✓ (rank 3)'), ('Sector · Region · Table', '✗ · ✓ · 3/5'),
             ('Model hatası', 'Kotlin top-1 29 ✗ · Python top-1 8 ✗ · aşırı güven yok'), ('Feature sonucu', 'neighbor ✓ · transition ✗ · pattern –'),
             ('Rejim', 'R-B (değişmedi) · geçiş entropisi 0,97'), ('Veritabanı', 'SPINS #5 215 · EVALUATIONS satırı · tek transaction ✓'),
             ('Artımlı güncelleme', '16 üye · en fazla ±%1,1 · model durumu u5215'), ('Yeni tahmin', 'PRED-0005216 kilitlendi 🔒')]
    rows_ = []
    for i, (a, b) in enumerate(steps):
        rows_.append(f'<div class="row" style="align-items:flex-start;padding:5px 0;border-bottom:1px solid #1E2A3D"><span class="cls" style="width:19px;height:19px;font-size:10px;background:{"#1F4D2C" if i < 8 else "#FFC531"};color:{"#A5D6A7" if i < 8 else "#2B2100"}">{i + 1}</span>'
                     f'<div class="grow"><div style="font-size:12.5px;font-weight:500">{a}</div><div class="mini">{b}</div></div></div>')
    chain = ''.join(rows_[:3]) + f'<div{A(5, "L")}>' + ''.join(rows_[3:5]) + '</div>' + ''.join(rows_[5:])
    newc = [dict(n=12, k=2, rank=1, p=2.9), dict(n=33, k=2, rank=2, p=2.8), dict(n=4, k=2, rank=3, p=2.8), dict(n=20, k=3, rank=4, p=2.7), dict(n=9, k=1, rank=5, p=2.7)]
    nx = ''.join(f'<div class="row" style="gap:6px;display:inline-flex">{chip(c["n"], "m")}<span class="gold b7" style="font-size:11px;margin-right:6px">k{c["k"]}</span></div>' for c in newc)
    c = card('SONUÇ', res, n=1, cls='blueb') + card('DEĞERLENDİRME · PRED-0005215', ev, n=None, right=pill('Exact ≠ Candidate', 'line xs')) \
        + card('ÖĞRENME ZİNCİRİ', chain, n=4) \
        + card('YENİ TAHMİN · PRED-0005216', f'<div class="row" style="flex-wrap:wrap;gap:2px"{A(6, "L")}>{nx}</div><div class="mini mt6">TABLE: KIRMIZI %50 · ÇİFT %50 · KÜÇÜK %50 · 3. DOZEN %34 · 1. COLUMN %33</div>', cls='warnb') \
        + btn('Tamam', n=7, np='TL')
    return phone(c, nav=0)

@sheet('tahmin_detayi', SEC, 'Tahmin detayı · açıklama',
       'NEXT’te bir adaya dokununca açılır. Açıklama yalnızca gerçekten hesaplanan skorlardan üretilir; uydurma neden yoktur.',
       legend=[
           (1, 'Aday seçici', '1–5 arası adaylar arasında geçiş. Seçili: rank-1 (29-k2).', '§4'),
           (2, 'Aday özeti', 'Numara-k, kalibre P(exact), tesadüf tabanı (%2,70) ve ×lift. Fark küçükse açıkça “anlamlı değil” yazılır.', '§66'),
           (3, 'Wheel görünümü', 'Gerçek fiziksel wheel sırası. Altın halka merkez numarayı, renkli yay komşu aralığını (k2) gösterir; SON 8 beyaz noktalarla (1 = en yeni).', '§2 · §63'),
           (4, 'Geometri', 'Wheel indeksi, sektör, bölge, saat yönü / ters yön / dairesel mesafe (son sonuca göre).', '§1 · §2'),
           (5, 'Skor katkıları', 'Sector · neighbor · transition · pattern · frequency · ML skorları ve Table uyumu — modelin gerçekten hesapladığı değerler. Pozitif = adayı yukarı, negatif = aşağı iter.', '§46'),
           (6, 'Table uyumu', 'Adayın renk/tek-çift/yüksek-düşük/dozen/column özelliklerinin Table seçimleriyle örtüşmesi. Table yalnızca Wheel’i tekrar ediyorsa bağımsız teyit sayılmaz.', '§6 · §46'),
           (7, 'Meclis uyuşmazlığı', 'Kotlin ve Python top-1 farklıysa gösterilir; aynı hatayı yapan modeller bağımsız kanıt sayılmaz.', '§10 · §32'),
           (8, 'Dürüstlük notu', 'Skorlar nedensel açıklama değil, modelin sinyalleridir; sonuç garanti değildir.', '§74'),
           (9, 'LAB’da incele', 'Bu adayın Counterfactual / Ablation sonuçlarını açar.'),
       ], refs='§1 · §2 · §4 · §6 · §10 · §46 · §63 · §66 · §74')
def s_tahmin_detayi():
    sel = ann(1, '<div class="row" style="gap:6px">' + ''.join(f'<span class="pill {"gold" if i == 0 else "line"}" style="font-size:12px;padding:5px 11px">{i + 1}</span>' for i in range(5)) + '</div>', 'L', block=True)
    c0 = CANDS[0]
    cw, ccw, circ = dist(17, 29)
    head = (f'<div class="row" style="gap:12px">{chip(29, "xl")}<div class="grow"><div class="b7" style="font-size:19px">29-k2 <span class="gold fs12">rank 1</span></div>'
            f'<div class="mini">P(exact) <b class="tx">{pc(c0["p"], 1)}</b> · taban {pc(BASE_EXACT, 2)} · <b class="gold">×{f(c0["p"] / BASE_EXACT, 2)}</b></div>'
            f'<div class="mini warn">Fark anlamlı değil · %95 CI [%2,0 – %4,1]</div></div></div>')
    wheel = ch.svg_wheel(300, cands=[(29, 2, 1)], last=17, recent=LAST8, sectors=True, margin=36, ring=36)
    geo = (kv('Wheel indeksi', f'{idx(29)}', mono=True) + kv('Sektör · Bölge', f'S{sector(29)} · {REGION_NAMES[region(29)]}') + kv('Komşu aralığı (k2)', ' '.join(str(x) for x in span(29, 2)), mono=True)
           + kv('17 → 29 mesafe', f'saat {cw} · ters {ccw} · kısa {circ}', mono=True))
    contrib = [('Sector skoru', +0.42), ('Neighbor skoru', +0.18), ('Transition skoru', +0.55), ('Pattern skoru', +0.12), ('Frequency skoru', -0.09), ('ML skoru', +0.21), ('Table uyumu', +0.05)]
    bars = ''
    for nme, v in contrib:
        w = abs(v) / 0.6 * 50
        side = f'<div style="position:absolute;left:50%;width:{w:.0f}%;height:100%;background:#4CAF50;border-radius:0 3px 3px 0"></div>' if v > 0 else f'<div style="position:absolute;right:50%;width:{w:.0f}%;height:100%;background:#E57373;border-radius:3px 0 0 3px"></div>'
        bars += (f'<div class="row" style="padding:4px 0"><div style="width:106px;font-size:12px">{nme}</div><div style="flex:1;position:relative;height:12px;background:#1E2A3D;border-radius:3px">'
                 f'<div style="position:absolute;left:50%;top:-2px;bottom:-2px;width:1px;background:#6F7F96"></div>{side}</div><div class="mono b7" style="width:52px;text-align:right;font-size:11.5px;color:{"#81C784" if v > 0 else "#FF8A80"}">{sg(v, 2, "")}</div></div>')
    tal = (f'<div class="row" style="gap:6px;flex-wrap:wrap">{pill("SİYAH ✓", "green xs")}{pill("TEK ✓", "green xs")}{pill("BÜYÜK ✓", "green xs")}{pill("2. DOZEN ✗", "red xs")}{pill("2. COLUMN ✓", "green xs")}</div>'
           '<div class="mini mt6">29 = siyah · tek · 19–36 · 3. dozen · 2. column → <b class="tx">4/5 uyum</b></div>')
    c = appbar('Tahmin detayı', back=True, sub=f'{PRED_ID} · kilitli') + sel + card(None, head, cls='warnb', n=2) \
        + card('WHEEL · FİZİKSEL SIRA', f'<div style="display:flex;justify-content:center">{wheel}</div>', n=3) \
        + card('GEOMETRİ', geo, n=4) + card('SKOR KATKILARI (log-odds, ×10⁻²)', bars, n=5, right=pill('hesaplanan', 'green xs')) \
        + card('TABLE UYUMU', tal, n=6) \
        + card('MECLİS UYUŞMAZLIĞI', kv('Kotlin top-1', '29-k2') + kv('Python top-1', '8-k2', 'warn') + kv('Jensen–Shannon', '0,18 · uyuşmuyor', 'dim'), n=7) \
        + banner('neutral', 'Bu skorlar modelin sinyalleridir, nedensel açıklama değildir. Sonuç garanti değildir.', n=8, icon='📌') \
        + btn('LAB’da incele ›', 'ghost', n=9, np='TL')
    return phone(c, nav=0)

@sheet('ana_durumlar', SEC, 'Ana · durum ve hata kartları',
       'Ana ekranda görünebilen özel durumlar: öğreniyor, Python ERROR (açık fallback), kilit/leakage hataları, hafıza yakalama.',
       legend=[
           (1, 'Öğreniyor', 'Yeterli veri yoksa (<50 spin) tahmin uydurulmaz; NEXT ve TABLE “--” gösterir ve ilerleme çubuğu çıkar.', '§55 · §56'),
           (2, 'Python ERROR', 'Python hata verirse uygulama çökmez; status=ERROR yazılır, Kotlin-only fallback açıkça belirtilir. Fallback hiçbir zaman Python sonucu gibi gösterilmez.', '§56'),
           (3, 'Kilit hatası', 'Zaten kilitli tahmin varken ikinci tahmin üretilmesi engellenir (LR-E-LOCK-002).', '§69'),
           (4, 'Leakage engellendi', 'Gelecek veriye erişim denemesi replay kesiminde durdurulur (LR-E-LEAK-001); işlem iptal edilir, canlı durum değişmez.', '§33'),
           (5, 'Hafıza yakalama', 'Uygulama açılışında kayıtlı durum geride ise yalnızca yeni kayıtlar işlenir; bozuksa tam replay önerilir.', '§69 · §70'),
           (6, 'Sınıf ve uyarılar', 'Yetersiz sample, OOS zayıf, calibration zayıf, parameter stability yok, leakage şüphesi — prompt’taki sabit metinlerle.', '§55'),
       ], refs='§33 · §55 · §56 · §69 · §70')
def s_ana_durumlar():
    c1 = card('NEXT', '<div class="row" style="gap:6px;justify-content:space-between">' + ''.join('<div style="flex:1;text-align:center;background:#1B2A47;border:1px dashed #2C3E5E;border-radius:10px;padding:8px 0;font-size:18px;font-weight:900;color:#6F7F96">--</div>' for _ in range(5)) + '</div>'
              '<div class="row sb mini mt8"><span>Öğreniyor</span><b class="tx">38 / 50 spin</b></div>' + prog(76, style='margin-top:4px') + '<div class="mini mt6">En az 50 spin gerekli; tahmin üretilmiyor.</div>', n=1, right=pill('Öğreniyor', 'amber xs'))
    c2 = banner('bad', '<b>Python motoru yanıt vermedi</b> · status=ERROR · <span class="mono">LR-E-PY-001</span><br>Kotlin-only fallback ile çalışıyor. Bu tahminler <b>Python sonucu değildir</b>.'
                       f'<div class="btns mt8">{btn("Yeniden dene", "sm")}{btn("Logu gör", "ghost sm")}</div>', n=2)
    c3 = banner('warn', '<b>İkinci tahmin üretilmedi</b> · <span class="mono">LR-E-LOCK-002</span><br>PRED-0005215 zaten kilitli. Önce gerçek sonuç girilmeli.', n=3)
    c4 = banner('bad', '<b>Leakage engellendi</b> · <span class="mono">LR-E-LEAK-001</span><br>Replay adımı gelecek veriye erişmeye çalıştı; işlem iptal edildi, canlı durum değişmedi.', n=4)
    c5 = banner('info', '<b>Hafıza yakalama</b> · kayıtlı durum 3 spin geride<br>Yalnızca yeni kayıtlar işleniyor: 2 / 3', n=5) + prog(66, style='margin:-4px 0 10px')
    wr = ''.join(banner('warn', t, style='margin-bottom:6px') for t in ['Düşük sample.', 'OOS zayıf.', 'Calibration zayıf.', 'Parameter stability yok.', 'Baseline’dan anlamlı ayrışma yok.', 'Leakage şüphesi.'])
    c6 = card('UYARI ÖRNEKLERİ (sabit metinler)', wr, n=6)
    return phone(c1 + c2 + c3 + c4 + c5 + c6, nav=0)
