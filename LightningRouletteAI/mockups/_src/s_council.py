# -*- coding: utf-8 -*-
"""D · MECLİSLER"""
from common import *

SEC = 'D · MECLİSLER'
CTABS = ['Kotlin', 'Python', 'Table', 'Hakem', 'Champion / Challenger']
TOP1_K = [29, 29, 12, 21, 8, 29, 29, 8]
TOP1_P = [8, 29, 17, 8, 21, 8, 29, 14]

def c_head(sel, n=None, sub=''):
    return head_row('🏛️ Meclisler', pill('Fixed-Share Hedge', 'line xs')) + hs(CTABS, sel, n=n, np='TL')

def members(rows, top1, n_rows=None, n_w=None, n_t=None, n_d=None, n_st=None):
    out = ''
    for i, (name, desc, w, d, ls, st) in enumerate(rows):
        dot = '<span class="dot"></span>' if st == 'on' else '<span class="dot a"></span>'
        stt = '' if st == 'on' else f'<span class="tag pau">BENCH</span>'
        out += (f'<div class="row" style="padding:7px 0;border-bottom:1px solid #1E2A3D;gap:8px">{dot}'
                f'<div class="grow" style="min-width:0"><div style="font-size:12.5px;font-weight:500">{name} {stt}</div><div class="mini">{desc}</div>'
                f'<div class="row" style="gap:6px;margin-top:3px"><div style="width:88px">{bar(w * 100, "#1976D2", mx=20, h=6)}</div><span class="mono fs10 dim">{pc(w * 100, 0)}</span>'
                f'<span class="mono fs10" style="margin-left:4px">{delta(d, 1, " pp")}</span></div></div>'
                f'<div style="text-align:center">{chip(top1[i], "s")}<div class="mini" style="font-size:9px;margin-top:2px">top-1</div></div></div>')
    return out

def consensus(top1, ws):
    tot = {}
    for t, w in zip(top1, ws): tot[t] = tot.get(t, 0) + w
    best = max(tot.items(), key=lambda x: x[1])
    return best

@sheet('meclis_kotlin', SEC, 'Meclisler · Kotlin',
       'Kotlin meclisi: 8 üye. Her üye kendi hipotezini bağımsız test eder; Fixed-Share Hedge ağırlıkları kontrollü günceller.',
       legend=[
           (1, 'Meclis sekmeleri', 'Kotlin · Python · Table · Hakem · Champion/Challenger. FanSuper 1.4’teki Kotlin/Python/Yan düzeninin genişletilmiş hâli.', '§11 · §53'),
           (2, 'Özet kartı', 'Üye sayısı, Hedge durumu, son güncelleme ve meclisin top-1 oyu (ağırlıklı çoğunluk).'),
           (3, 'Üye satırı', 'Üye adı ve ne hesapladığı · ağırlık çubuğu · son OOS’ta candidate-5 farkı (pp) · bu spin için top-1 oyu. Yeşil nokta aktif, sarı nokta BENCH (ağırlığı çok düşük).', '§3 · §7–§10'),
           (4, 'Ağırlıklar', 'Körlemesine değiştirilmez: log-skor kazancı, rejim kazancı ve kalibrasyon cezasıyla yumuşatılmış, alt sınırı olan Fixed-Share güncellemesi.', '§67'),
           (5, 'Üye oyları', 'Üyelerin aynı sayıda toplanması bağımsız kanıt değildir; aynı hatayı yapan modeller ayrı sayılmaz (Diversity).', '§10 · §32'),
           (6, 'Not', 'Bir üyeyi kapatmak canlı modeli değiştirmez; yeni bir Challenger oluşturur ve LAB’da test edilir.', '§40 · §68'),
       ], refs='§3 · §7–§11 · §32 · §40 · §53 · §67 · §68')
def s_council_k():
    best, wsum = consensus(TOP1_K, [r[2] for r in KOTLIN])
    summ = (f'<div class="row" style="gap:10px"><span style="font-size:24px">🔵</span><div class="grow"><div class="b7" style="font-size:14px">Kotlin Meclisi · 8 üye</div>'
            f'<div class="mini">Hedge α=0,05 · son güncelleme spin #5 214</div></div><div style="text-align:center">{chip(best, "m")}<div class="mini" style="font-size:9px">meclis top-1</div></div></div>')
    wbar = ch.svg_strip([(r[2], r[0].split(' ')[0]) for r in KOTLIN], ['#3F6FB5', '#4F86C6', '#2F5C99', '#5B93D1', '#27508A', '#6AA0DA', '#35639F', '#4380C0'], w=340, h=26)
    c = c_head(0, n=1) + card(None, summ, n=2, cls='blueb') + card('ÜYELER', members(KOTLIN, TOP1_K), n=3, right=pill('OOS n=1 043', 'line xs')) \
        + card('AĞIRLIK DAĞILIMI', wbar + '<div class="mini mt6">Toplam %100 · en düşük ağırlık %8 (alt sınır %1,9)</div>', n=4) \
        + card('ÜYE OYLARI (bu spin)', '<div class="row" style="gap:5px;flex-wrap:wrap">' + ''.join(f'<span class="row" style="gap:3px;display:inline-flex">{chip(t, "s")}</span>' for t in TOP1_K) + '</div>'
               '<div class="mini mt6">29: 4 oy (%53 ağırlık) · 8: 2 oy · 12: 1 · 21: 1</div>', n=5) \
        + banner('neutral', 'Bir üyeyi kapatmak canlı modeli değiştirmez; yeni bir Challenger oluşturur.', n=6, icon='📌')
    return phone(c, nav=1)

@sheet('meclis_python', SEC, 'Meclisler · Python',
       'Python meclisi (Chaquopy, uygulamaya gömülü): 8 üye + çalışma zamanı durumu + Kotlin–Python sözleşmesi.',
       legend=[
           (1, 'Çalışma zamanı', 'Chaquopy · Python 3.11 durumu, ortalama gecikme ve son hata. Python hata verirse status=ERROR olur, uygulama çökmez ve açık fallback gösterilir.', '§56'),
           (2, 'Test et / Yeniden başlat', 'Python motorunu sağlık testinden geçirir veya yeniden başlatır.'),
           (3, 'Üye satırı', 'LSTM · Transformer · 1D-CNN · Gradient Boosting · HMM · kNN-DTW · Bağlam modeli · Motif keşfi. Ağırlık, OOS farkı ve top-1 oyu Kotlin sayfasıyla aynı düzende.', '§10'),
           (4, 'Sözleşme', 'Giriş: dataset sürümü, geçmiş, feature sürümü, parametreler. Çıkış: aday numaralar, skorlar, table olasılıkları, metadata, model sürümü, deney kimliği.', '§57'),
           (5, 'Doğrulama', 'Kotlin, Python sonucunu doğrulamadan overlay’e koymaz: şema, 0–36 aralığı, olasılık toplamı, sürüm uyumu.', '§57'),
           (6, 'Fallback kuralı', 'Python çıktısı yoksa Kotlin-only çalışılır ve bu açıkça yazılır; fallback Python sonucu gibi gösterilmez.', '§56'),
       ], refs='§10 · §56 · §57 · §58')
def s_council_p():
    best, _ = consensus(TOP1_P, [r[2] for r in PYTHON])
    rt = (f'<div class="row" style="gap:10px"><span style="font-size:24px">🐍</span><div class="grow"><div class="b7" style="font-size:14px">Python Meclisi · 8 üye</div>'
          f'<div class="mini">Chaquopy · Python 3.11 · gecikme 38 ms/tahmin · son hata: yok</div></div><div style="text-align:center">{chip(best, "m")}<div class="mini" style="font-size:9px">meclis top-1</div></div></div>'
          f'<div class="btns mt8" {A(2, "L")}>{btn("Sağlık testi", "ghost sm")}{btn("Yeniden başlat", "ghost sm")}</div>')
    contract = ('<div class="mini" style="line-height:1.5"><b class="hd">GİRİŞ</b><div class="mono" style="color:#CFE0F5;font-size:11px">dataset sürümü · geçmiş · feature sürümü · parametreler</div>'
                '<b class="hd" style="display:block;margin-top:6px">ÇIKIŞ</b><div class="mono" style="color:#CFE0F5;font-size:11px">aday numaralar · skorlar · table olasılıkları · metadata · model sürümü · deney kimliği</div></div>')
    valid = ''.join(kv(a, '<span class="ok b7">✓</span>') for a in ['Şema doğrulandı', 'Tüm numaralar 0–36', 'Olasılık toplamı 1,000', 'Sürüm uyumlu (feat-v5)'])
    c = c_head(1, n=None) + card(None, rt, n=1, cls='good') + card('ÜYELER', members(PYTHON, TOP1_P), n=3, right=pill('OOS n=1 043', 'line xs')) \
        + card('KOTLIN ↔ PYTHON SÖZLEŞMESİ', contract, n=4) + card('KOTLIN DOĞRULAMASI (son tahmin)', valid, n=5) \
        + banner('warn', 'Python hata verirse: status=<b>ERROR</b> · Kotlin-only fallback — fallback Python sonucu olarak gösterilmez.', n=6)
    return phone(c, nav=1)

@sheet('meclis_table', SEC, 'Meclisler · Table',
       'Table motoru: COLOR · PARITY · HIGH/LOW · DOZEN · COLUMN kategorileri sayı tahmininden bağımsız, kendi feature’larıyla öğrenilir.',
       legend=[
           (1, 'Kategori sekmeleri', 'Beş Table kategorisi ayrı modeldir. Table, Wheel tahmininin basit türevi olamaz.', '§5'),
           (2, 'Kategori kartı', 'Seçilen taraf, kalibre olasılık, tesadüf tabanı ve fark. Baz değerler: renk/tek-çift/yüksek-düşük %48,6; dozen/column %32,4.', '§5 · §23'),
           (3, 'Kullanılan modeller', 'Her kategori için frequency (decay), 1. derece geçiş, gradient boosting ve lojistik; ağırlık ve OOS farkı.', '§5 · §8 · §9'),
           (4, 'Bağımsızlık testi', 'Table hataları ile Wheel hataları arasındaki korelasyon. Yüksekse Table bağımsız teyit sayılmaz.', '§6 · §45'),
           (5, 'Koşullu tablo', 'Wheel doğru → Table, Table doğru → Wheel, agreement, disagreement: ayrı ölçülür (LAB › Side/Table).', '§45'),
       ], refs='§5 · §6 · §8 · §9 · §23 · §45')
def s_council_t():
    cats = [('COLOR', 'SİYAH', 50.2, 48.65, 'Siyah'), ('PARITY', 'TEK', 49.6, 48.65, 'Tek'), ('HIGH/LOW', 'BÜYÜK', 50.9, 48.65, '19–36'), ('DOZEN', '2. DOZEN', 33.8, 32.43, '13–24'), ('COLUMN', '2. COLUMN', 33.1, 32.43, 'Sütun 2')]
    body = ''
    for cat, lab, p, b, pk in cats:
        body += (f'<div style="padding:7px 0;border-bottom:1px solid #1E2A3D"><div class="row sb"><b style="font-size:12.5px">{cat} → {lab}</b><span class="mono b7">{pc(p, 1)}</span></div>'
                 f'<div class="row" style="gap:8px;margin-top:4px"><div style="flex:1">{bar(p, "#1976D2", base=b, mx=60, h=6)}</div><span class="fs10 dim">taban {pc(b, 1)}</span><span class="fs10">{delta(p - b)}</span></div></div>')
    models = tbl(['Model', 'Ağırlık', 'OOS Δ'], [['Frequency (decay 0,02)', '%28', delta(0.2)], ['Geçiş (1. derece)', '%24', delta(0.1)], ['Gradient boosting', '%30', delta(0.4)], ['Lojistik (recent)', '%18', delta(-0.1)]], al='lrr', n=3, np='TL')
    c = c_head(2, n=1) + card('KATEGORİLER', body, n=2, right=pill('kalibre', 'line xs')) + card('MODELLER (COLOR için)', models) \
        + card('BAĞIMSIZLIK', kv('Wheel–Table hata korelasyonu', 'φ = 0,03', 'ok', mono=True) + kv('Yorum', 'bağımsız teyit sayılabilir', 'ok') + muted('Table yalnızca Wheel bilgisini tekrar ediyorsa bağımsız teyit sayılmaz.'), n=4) \
        + card('KOŞULLU ÖLÇÜM', kv('Wheel doğru → Table doğru', f'{pc(51.8, 1)} <span class="dim fs10">n=144</span>', mono=True) + kv('Table doğru → Wheel (cand-5)', f'{pc(14.1, 1)} <span class="dim fs10">n=520</span>', mono=True) + kv('Agreement', f'{pc(31.2, 1)} <span class="dim fs10">n=325</span>', mono=True) + kv('Disagreement', f'{pc(12.9, 1)} <span class="dim fs10">n=718</span>', mono=True), n=5, right=pill('LAB › Side/Table', 'line xs'))
    return phone(c, nav=1)

@sheet('meclis_hakem', SEC, 'Meclisler · Hakem (Meta-Ensemble)',
       'Kotlin, Python ve Table çıktılarını kontrollü birleştirir. Ağırlıklar validation’da seçilir; OOS’ta tekrar optimize edilmez.',
       legend=[
           (1, 'Boru hattı', 'Kotlin meclisi + Python meclisi + Table → Meta-Ensemble → Calibration → Prediction Lock.', '§11 · §53'),
           (2, 'Hakem özeti', 'Seçili Kotlin/Python ağırlığı (50/50), yöntem (stacking + kalibrasyon + konformal) ve hakemin top-1 oyu.', '§11'),
           (3, 'Ağırlık testi', '20/80 … 80/20 ağırlıkları VALIDATION üzerinde karşılaştırılır (log loss; düşük iyi). Kesikli çizgi = rastgele taban (ln 37 = 3,6109). Hiçbiri tabandan iyi değil ve bu açıkça yazılır.', '§11 · §22'),
           (4, 'OOS kilidi', 'Ağırlık seçimi test setinde yapılmaz; aynı OOS’ta tekrar tekrar seçim yasaktır. Kullanım sayacı gösterilir.', '§11 · §22 · §33'),
           (5, 'Konformal küme', '%90 kapsam için gereken aday kümesi boyutu: 33/37 → bilgilendirici değil (taban 33,3).', '§66'),
           (6, 'Kalibrasyon', 'Sıcaklık ölçekleme ve bucket kalibrasyonu; ayrıntı LAB › Calibration.', '§24'),
       ], refs='§11 · §22 · §24 · §33 · §53 · §66')
def s_council_h():
    pipe = ('<div class="row" style="gap:4px;flex-wrap:nowrap;font-size:10px;text-align:center">'
            + ''.join(f'<div style="flex:1;background:#1B2A47;border:1px solid #2A3B5C;border-radius:8px;padding:6px 2px">{t}</div>' + ('<span class="info">→</span>' if i < 4 else '')
                      for i, t in enumerate(['Kotlin<br>8', 'Python<br>8', 'Table<br>5', 'Meta<br>Ensemble', 'Kalibre<br>+ LOCK'])) + '</div>')
    wts = [('20/80', 3.6141), ('30/70', 3.6138), ('40/60', 3.6137), ('50/50', 3.6135), ('60/40', 3.6136), ('70/30', 3.6139), ('80/20', 3.6143)]
    rows = [[w, f'<span class="mono">{v:.4f}</span>', f'<span class="bad fs11 mono">{sg(v - 3.6109, 4, "")}</span>', '<span class="gold b7">● seçili</span>' if w == '50/50' else ''] for w, v in wts]
    hk = (f'<div class="row" style="gap:10px"><span style="font-size:24px">⚖️</span><div class="grow"><div class="b7" style="font-size:14px">Meta-Ensemble Hakem</div>'
          f'<div class="mini">Kotlin 50 · Python 50 · stacking + kalibrasyon + konformal</div></div><div style="text-align:center">{chip(29, "m")}<div class="mini" style="font-size:9px">hakem top-1</div></div></div>')
    c = c_head(3, n=None) + card('BORU HATTI', pipe, n=1) + card(None, hk, n=2, cls='warnb') \
        + card('AĞIRLIK TESTİ · VALIDATION log loss', tbl(['Kotlin/Python', 'Log loss', 'Tabana göre', ''], rows, al='lrrr', hl=(3,)) + '<div class="mini mt6">Rastgele taban ln 37 = <b class="tx">3,6109</b> — hiçbir ağırlık tabandan iyi değil.</div>', n=3) \
        + card('OOS KİLİDİ', kv('Bu OOS ile model seçimi', '<span class="ok">yapılmadı</span>') + kv('OOS’a bakış sayısı', '1 / 1 (rapor)', mono=True) + kv('Seçim verisi', 'Validation 1 043', mono=True), n=4) \
        + card('KONFORMAL KÜME (%90 kapsam)', kv('Küme boyutu', '33 / 37 sayı', 'warn', mono=True) + kv('Taban', '33,3', 'dim', mono=True) + muted('Bilgilendirici değil: küme neredeyse tüm çarkı kapsıyor.'), n=5) \
        + card('KALİBRASYON', kv('Yöntem', 'sıcaklık τ = 1,02 + bucket') + kv('ECE (OOS)', '0,021', mono=True), n=6)
    return phone(c, nav=1)

@sheet('champion_challenger', SEC, 'Champion / Challenger',
       'Canlı model (Champion) ile aday (Challenger). Challenger ancak tüm kapıları geçerse aday olur; canlıya geçiş yalnızca elle onayla.',
       legend=[
           (1, 'Champion', 'Şu an canlı tahmin üreten model (v1.0.3): dataset, feature sürümü, OOS özeti. LAB onu otomatik değiştiremez.', '§40 · §67'),
           (2, 'Challenger', 'LAB’da geliştirilen aday (v1.1.0-rc2). Canlıya henüz geçmedi.', '§67 · §68'),
           (3, 'Kapı listesi', 'Minimum sample · OOS · baseline üstü · calibration · robustness · leakage yok · tekrarlanabilir · dönem ve parametre stabilitesi · kabul edilebilir overfit. En yüksek hit oranı otomatik seçilmez.', '§51 · §68'),
           (4, 'Karar', 'Kapılardan biri kalırsa “Champion yap” pasif kalır ve nedeni yazılır. Hepsi geçse bile geçiş manuel onay ister.', '§40 · §68'),
           (5, 'Sürüm geçmişi', 'Her artımlı güncelleme versiyonludur; eski sürüme geri dönülebilir.', '§67'),
           (6, 'Geri dön', 'Seçili eski Champion sürümünü yeniden canlı yapar (onay + yeni kilit).'),
       ], refs='§40 · §51 · §67 · §68')
def s_champion():
    ch_ = card(f'🏆 CHAMPION · v1.0.3', kv('Durum', 'CANLI', 'ok') + kv('Dataset · feature', 'DATASET-004 · feat-v5', mono=True) + kv('OOS Candidate-5', f'{pc(13.8, 1)} <span class="dim fs10">taban {pc(13.51, 1)}</span>', mono=True) + kv('Sınıf', cls_b('C') + ' nötr'), n=1, cls='good', right=pill('LIVE', 'green xs'))
    cl_ = card('🧪 CHALLENGER · v1.1.0-rc2', kv('Durum', 'ADAY DEĞİL', 'warn') + kv('Dataset · feature', 'DATASET-004 · feat-v6', mono=True) + kv('OOS Candidate-5', f'{pc(14.4, 1)} <span class="dim fs10">+0,9 pp · CI [−1,3 ; +3,1]</span>', mono=True) + kv('Sınıf', cls_b('C') + ' nötr'), n=2, cls='warnb', right=pill('LAB', 'purple xs'))
    gates = [('Minimum sample (≥ 1 000 OOS)', True, 'n = 1 043'), ('OOS ayrımı (kullanılmamış)', True, 'tek bakış'), ('Baseline üstü (anlamlı)', False, 'CI sıfırı kapsıyor'), ('Calibration', True, 'ECE 0,020'),
             ('Robustness ≥ 60', False, '43 / 100'), ('Leakage yok', True, 'kapılar temiz'), ('Tekrarlanabilir (determinizm)', True, 'hash eşleşti'), ('Dönem stabilitesi', True, '4/5 dönem'), ('Parametre stabilitesi', False, 'tek nokta'), ('Kabul edilebilir overfit', True, 'O işareti yok')]
    g = ''.join(f'<div class="row" style="padding:5px 0;border-bottom:1px solid #1E2A3D"><span class="{"ok" if ok else "bad"} b7" style="width:18px">{"✓" if ok else "✗"}</span><div class="grow" style="font-size:12.5px">{t}</div><span class="mini">{s}</span></div>' for t, ok, s in gates)
    c = c_head(4, n=None) + ch_ + cl_ + card('KAPILAR · 7 / 10 geçti', g, n=3, right=pill('adaylık: HAYIR', 'red xs')) \
        + f'<div{A(4, "TL")}>{btn("Champion yap (manuel onay)", "dis")}<div class="mini c mt4">Pasif: baseline üstü · robustness · parametre stabilitesi kapıları geçilmedi.</div></div>' \
        + card('SÜRÜM GEÇMİŞİ', tbl(['Sürüm', 'Tarih', 'Durum'], [['v1.0.3 · u5214', '02.10.2026', '<span class="ok">Champion</span>'], ['v1.0.2', '28.09.2026', '<span class="dim">eski</span>'], ['v1.0.1', '21.09.2026', '<span class="dim">eski</span>']], al='llr', dm=(1, 2)) + f'<div class="mt8">{btn("Seçili sürüme geri dön", "ghost sm", n=6, np="TL")}</div>', n=5)
    return phone(c, nav=1)
