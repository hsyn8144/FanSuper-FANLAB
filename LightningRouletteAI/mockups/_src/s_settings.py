# -*- coding: utf-8 -*-
"""G · AYARLAR"""
from common import *
from ovl import ov

SEC = 'G · AYARLAR'
STABS = ['Tahmin & Meclis', 'Overlay', 'Python / LAB', 'Veri & güvenlik', 'Loglar', 'Tanılama', 'Hakkında']

def s_head(sel, n=None):
    return head_row('⚙️ Ayarlar', pill(f'{VER} · derleme 1', 'line xs')) + hs(STABS, sel, n=n, np='TL')

S1 = (1, 'Ayarlar sekmeleri', 'Tahmin & Meclis · Overlay · Python / LAB · Veri & güvenlik · Loglar · Tanılama · Hakkında. Satır yana kaydırılır.')

def chips2(items, sel, n=None, np='L'):
    return f'<div class="wrap"{A(n, np)}>' + ''.join(pill(t, 'blue' if t in sel else 'line', style='font-size:12px;padding:6px 12px') for t in items) + '</div>'

@sheet('ayar_tahmin', SEC, 'Ayarlar · Tahmin ve Meclis',
       'Aday sayısı, k, yön, çeşitlilik, Table kategorileri, yüzde semantiği, meclis ağırlıkları ve değiştirilemeyen güvenlik kuralları.',
       legend=[S1,
               (2, 'Aday sayısı', 'En az 3, en çok 5 (varsayılan 5). Her aday sayısı ayrı test edilir; 5 aday daha yüksek kapsama sağlar.', '§4 · §65'),
               (3, 'k ve yön', 'Varsayılan k (k1/k2/k3) ve yön: iki yön, L (saat yönünün tersi), R (saat yönü).', '§2 · §20'),
               (4, 'Çeşitlilik kuralı', 'Mümkünse 3–5 adayda en az 2 farklı sector/region kaynağı bulunur.', '§4'),
               (5, 'Table kategorileri', 'TABLE kartı ve overlay’de hangi kategorilerin gösterileceği. Table modelleri sayıdan bağımsızdır.', '§5'),
               (6, 'Yüzde semantiği', 'Kalibre olasılık (varsayılan) · model olasılığı · tarihsel oran ayrı tutulur. Sadece frequency ise “confidence” diye sunulmaz.', '§66'),
               (7, 'Meclis ağırlıkları', 'Kotlin/Python ağırlığı validation’da otomatik seçilir ya da elle (20/80 … 80/20). Öğrenme eşiği: en az 50 spin.', '§11 · §55'),
               (8, 'Kilitli kurallar', 'Prediction lock, versiyonlu artımlı güncelleme ve Champion’a manuel onay kapatılamaz.', '§14 · §67 · §68')],
       refs='§2 · §4 · §5 · §11 · §14 · §20 · §65–§68')
def s_ayar_tahmin():
    c = s_head(0, n=1) \
        + card('ADAYLAR', setrow('Aday sayısı', 'en az 3 · en çok 5', chips2(['3', '4', '5'], ['5'], n=2)) + setrow('Varsayılan k', None, chips2(['k1', 'k2', 'k3'], ['k2']), n=3) + setrow('Yön', None, chips2(['↔', 'L', 'R'], ['↔'])) \
               + setrow('Çeşitlilik: ≥ 2 farklı sector/region', 'mümkünse', sw(True), n=4)) \
        + card('TABLE KATEGORİLERİ', f'<div{A(5, "L")}>' + ''.join(setrow(t, None, sw(True)) for t in ['COLOR', 'PARITY', 'HIGH/LOW', 'DOZEN', 'COLUMN']) + '</div>') \
        + card('YÜZDE SEMANTİĞİ', f'<div{A(6, "L")}>' + radio('Kalibre olasılık', 'varsayılan · kalibrasyon uygulanmış', True) + radio('Model olasılığı', 'ham çıktı', False) + radio('Tarihsel oran', 'yalnızca frekans — “confidence” değildir', False) + '</div>') \
        + card('MECLİS VE HAKEM', setrow('Kotlin / Python ağırlığı', 'validation’da otomatik', choice('Otomatik'), n=7) + setrow('Elle ağırlık', '20/80 … 80/20', choice('50/50')) + setrow('Öğrenme eşiği', 'en az spin', val('50')) + setrow('Python meclisi', None, sw(True))) \
        + card('KİLİTLİ KURALLAR 🔒', f'<div{A(8, "L")}>' + setrow('Prediction lock', 'kapatılamaz', sw(True)) + setrow('Artımlı güncelleme (versiyonlu)', 'kapatılamaz', sw(True)) + setrow('Challenger → Champion', 'yalnızca manuel onay', sw(True)) + '</div>')
    return phone(c, nav=5)

@sheet('ayar_overlay', SEC, 'Ayarlar · Overlay',
       'Overlay servisi, izin durumu, görünüm modu, boyut, saydamlık, NEXT sayısı, klavye, konum ve geri bildirim.',
       legend=[S1,
               (2, 'Canlı önizleme', 'Seçilen görünüm, boyut ve saydamlıkla overlay’in nasıl görüneceği.', '§12'),
               (3, 'Overlay servisi', 'Açık/Kapalı. Açıkken ön plan servisi bildirimi görünür (Ekran 67).', '§12 · §49'),
               (4, 'İzin durumu', '“Diğer uygulamaların üzerinde göster”. Verilmediyse [Ayarı aç] sistem ayar sayfasına götürür.', '§12'),
               (5, 'Görünüm', 'Dikey · Yatay · Kompakt · Metin · Simge (bubble).', '§12'),
               (6, 'Boyut ve saydamlık', 'Boyut %80–140; saydamlık %60–100. FanSuper 1.4’teki gibi mavi kart.', '§12'),
               (7, 'NEXT sayısı', '3–5 arası. SON 8 ve TABLE satırı sayısı sabittir (8 ve 5).', '§4 · §12'),
               (8, 'Klavye ve uyarılar', 'Klavyeyi göster/gizle, uyarı simgesi, titreşim geri bildirimi.', '§13'),
               (9, 'Konum', 'Konumu hatırla, kenara yapış; [Konumu sıfırla].', '§12')],
       refs='§4 · §12 · §13 · §49')
def s_ayar_overlay():
    prev = f'<div style="display:flex;justify-content:center;padding:6px 0 2px">{ov("compact", alpha=.92)}</div>'
    c = s_head(1, n=1) + card('ÖNİZLEME', prev, n=2) \
        + card('SERVİS', setrow('Overlay servisi', 'başka uygulamaların üzerinde yüzer', sw(True), n=3) + setrow('İzin: diğer uygulamaların üzerinde göster', '<span class="ok">Verildi ✓</span>', btn('Ayarı aç', 'ghost sm', style='width:86px'), n=4) + setrow('Başlangıçta otomatik aç', None, sw(False))) \
        + card('GÖRÜNÜM', f'<div{A(5, "L")}>' + chips2(['Dikey', 'Yatay', 'Kompakt', 'Metin', 'Simge'], ['Kompakt']) + '</div>' + '<div class="mini mt8">Boyut</div>' + slider(40, '%100', n=6) + '<div class="mini">Saydamlık</div>' + slider(80, '%92')) \
        + card('İÇERİK', setrow('NEXT adayı sayısı', '3–5', chips2(['3', '4', '5'], ['5']), n=7) + setrow('TABLE satırı', 'sabit', val('5')) + setrow('SON 8 sonuç', 'sabit · en yeni solda', val('8'))) \
        + card('ETKİLEŞİM', setrow('Klavyeyi göster', None, sw(True), n=8) + setrow('Uyarı simgesi', None, sw(True)) + setrow('Titreşim geri bildirimi', None, sw(True))) \
        + card('KONUM', setrow('Konumu hatırla', None, sw(True), n=9) + setrow('Kenara yapış', None, sw(True)) + f'<div class="mt8">{btn("Konumu sıfırla", "ghost sm")}</div>')
    return phone(c, nav=5)

@sheet('ayar_python_lab', SEC, 'Ayarlar · Python / LAB / Performans',
       'Python çalışma zamanı sağlığı ve hata davranışı, LAB arka plan işçisi, önbellek ve sayfalama ayarları.',
       legend=[S1,
               (2, 'Python çalışma zamanı', 'Chaquopy durumu, sürüm ve gecikme. [Sağlık testi] ve [Yeniden başlat].', '§56 · §57'),
               (3, 'Hata davranışı', 'Python hata verirse uygulama çökmez; status=ERROR yazılır ve Kotlin-only fallback açıkça gösterilir. Fallback Python sonucu gibi sunulmaz.', '§56'),
               (4, 'LAB arka plan', 'Paralel iş sayısı, CPU sınırı, pil tasarrufunda duraklatma, checkpoint aralığı ve ön plan bildirimi. LAB canlı arayüzü bloke etmez.', '§49 · §71'),
               (5, 'Önbellek', 'Cache dataset/feature sürümü ve parametre hash’ine bağlıdır; sürüm değişince eskiyen önbellek otomatik geçersiz olur.', '§15 · §48'),
               (6, 'Sayfalama', 'Listelerde sayfa boyutu (10 bin / 100 bin+ spin için).', '§49')],
       refs='§15 · §48 · §49 · §56 · §57 · §71')
def s_ayar_python():
    c = s_head(2, n=1) \
        + card('PYTHON ÇALIŞMA ZAMANI', kv('Durum', '<span class="ok b7">HAZIR</span>') + kv('Sürüm', 'Chaquopy · Python 3.11', mono=True) + kv('Gecikme', '38 ms / tahmin', mono=True) + f'<div class="btns mt8">{btn("Sağlık testi", "ghost sm")}{btn("Yeniden başlat", "ghost sm")}</div>', n=2, cls='good') \
        + card('HATA DAVRANIŞI', setrow('Zaman aşımı', None, val('5 sn')) + setrow('Python hata verirse', 'status=ERROR + Kotlin-only fallback (açık etiket)', val('sabit 🔒')) + muted('Fallback hiçbir zaman Python sonucu gibi gösterilmez.'), n=3) \
        + card('LAB ARKA PLAN', setrow('Paralel iş sayısı', None, chips2(['1', '2'], ['1']), n=4) + setrow('CPU sınırı', None, choice('Orta')) + setrow('Pil tasarrufunda duraklat', None, sw(True)) + setrow('Checkpoint aralığı', 'her 50 spin / 30 sn', val('50 / 30 sn')) + setrow('Ön plan bildirimi', 'LAB çalışırken ilerleme', sw(True))) \
        + card('ÖNBELLEK', kv('Boyut', '124 MB / 500 MB', mono=True) + muted('Dataset/feature sürümü + parametre hash’ine bağlı; eskiyen kayıtlar otomatik geçersiz.') + f'<div class="mt8">{btn("Önbelleği temizle", "ghost sm")}</div>', n=5) \
        + card('SAYFALAMA', setrow('Liste sayfa boyutu', None, chips2(['25', '50', '100'], ['50']), n=6))
    return phone(c, nav=5)

@sheet('ayar_veri_guvenlik', SEC, 'Ayarlar · Veri ve güvenlik',
       'Otomatik yedek, dışa aktarma varsayılanları, depolama kullanımı, izin özeti ve geri alınamaz işlemler.',
       legend=[S1,
               (2, 'Otomatik yedek', 'Günlük yedek, saklanan yedek sayısı. Veritabanı + model metadata yedeklenir.', '§59'),
               (3, 'Dışa aktarma varsayılanı', 'Varsayılan biçim ve tek paket tercihi.', '§18'),
               (4, 'Depolama kullanımı', 'Resimler veritabanına konmaz; ham spinler küçüktür. Büyüme deneyler, model metadata, feature matrisleri, cache ve replay sonuçlarından gelir; gereksiz ara veri saklanmaz.', '§48'),
               (5, 'İzin özeti', 'Overlay, bildirim ve pil izinlerinin durumu; hepsi isteğe bağlıdır.', '§12 · §58'),
               (6, 'Tehlikeli bölge', 'Örnek veriyi sil, tüm veriyi sıfırla. Her ikisi de onay ister; sıfırlama geri alınamaz. Başarısız hipotez hafızası tek tek silinemez.', '§35 · §60')],
       refs='§12 · §18 · §35 · §48 · §58 · §59 · §60')
def s_ayar_veri():
    use = ('<div style="display:flex;height:12px;border-radius:6px;overflow:hidden;margin:4px 0 8px"><i style="width:2%;background:#64B5F6"></i><i style="width:66%;background:#1976D2"></i><i style="width:24%;background:#7E57C2"></i><i style="width:8%;background:#4DB6AC"></i></div>'
           + kv('<span style="color:#64B5F6">■</span> Spinler', '0,4 MB', mono=True) + kv('<span style="color:#1976D2">■</span> Deneyler + replay', '14,1 MB', mono=True) + kv('<span style="color:#9575CD">■</span> Model metadata', '5,2 MB', mono=True) + kv('<span style="color:#4DB6AC">■</span> Cache (ayrı)', '124 MB', mono=True))
    c = s_head(3, n=1) + card('YEDEK', setrow('Otomatik yedek', 'veritabanı + model metadata', sw(True), n=2) + setrow('Sıklık', None, choice('Günlük 03:00')) + setrow('Saklanan yedek', None, val('7'))) \
        + card('DIŞA AKTARMA', setrow('Varsayılan biçim', None, choice('JSON'), n=3) + setrow('Tek paket', None, sw(True))) \
        + card('DEPOLAMA', use, n=4, right=pill('DB 19,7 MB', 'line xs')) \
        + card('İZİNLER', kv('Diğer uygulamaların üzerinde göster', '<span class="ok">Verildi</span>') + kv('Bildirimler', '<span class="warn">Verilmedi</span>') + kv('Pil kısıtı', '<span class="warn">Kısıtlı</span>'), n=5) \
        + card('TEHLİKELİ BÖLGE', btn('Örnek veriyi sil (SAMPLE, 2 000 spin)', 'ghost sm') + '<div style="height:8px"></div>' + btn('Tüm veriyi sıfırla', 'red sm') + muted('Sıfırlama geri alınamaz.', style='margin-top:6px'), n=6, cls='bad')
    return phone(c, nav=5)

@sheet('ayar_loglar', SEC, 'Ayarlar · İşlem logları ve hata kodları',
       'Olay günlüğü (NEW_RECORD … REPLAY_COMPLETED ve yenileri) ve LR-E-* hata kodu sözlüğü.',
       legend=[S1,
               (2, 'Süzgeç', 'Hepsi · Hata · Uyarı · Bilgi.'),
               (3, 'Olay satırı', 'Saat · seviye · olay adı · ayrıntı. FanSuper 1.4’teki olay adları korunur; IMPORT_DONE, EXPERIMENT_*, MODEL_PROMOTED gibi yenileri eklenir.', '§47 · §69'),
               (4, 'Hata satırı', 'Hata kodu (LR-E-…), neyin durdurulduğu ve canlı durumun korunduğu.', '§33 · §56'),
               (5, 'Hata kodu sözlüğü', 'LEAK · LOCK · SEQ · STATE · PY · DB · REPLAY · REC (FanSuper’dan) + IMP · EXP · REPRO (yeni). Kodlar dokümantasyonda ve test raporlarında aynıdır.', '§33 · §39 · §69'),
               (6, 'Dışa aktar', 'Logu paylaşılabilir dosyaya yazar; “Temizle” yalnızca bilgi seviyesini siler (hata kayıtları kalır).')],
       refs='§33 · §39 · §47 · §56 · §69')
def s_ayar_log():
    ev = [('14:32:07', 'INFO', 'PREDICTION_LOCKED', 'PRED-0005215 · ref #5 214'), ('14:32:07', 'INFO', 'NEW_RECORD', 'spin #5 214 = 17 · LIVE'), ('14:31:44', 'INFO', 'EVALUATION_DONE', 'PRED-0005214 · cand ✗ · table 2/5'),
          ('14:20:12', 'WARN', 'PY_TIMEOUT', 'Python 5 sn → Kotlin-only · LR-E-PY-001'), ('14:05:00', 'INFO', 'EXPERIMENT_STARTED', 'LAB-2026-0001847'), ('13:58:41', 'INFO', 'EXPERIMENT_DONE', 'LAB-2026-0001842 · C'),
          ('13:50:02', 'INFO', 'EXPORT_ROUNDTRIP_OK', '5 214 / 5 214'), ('09:12:30', 'ERR', 'LEAK_BLOCKED', 'LR-E-LEAK-001 · replay adımı iptal'), ('03:00:00', 'INFO', 'BACKUP_DONE', '18,4 MB'), ('01:39:00', 'INFO', 'IMPORT_DONE', 'batch-003 · +1 900 · 100 dup'), ('00:10:44', 'INFO', 'REPLAY_COMPLETED', 'artımlı · +3')]
    lv = {'INFO': 'info', 'WARN': 'warn', 'ERR': 'bad'}
    rows = ''
    for i, (t, l, e, d) in enumerate(ev):
        a = A(3, 'L') if i == 1 else (A(4, 'L') if l == 'ERR' else '')
        rows += f'<div class="row" style="padding:5px 2px;border-bottom:1px solid #1E2A3D;gap:7px;align-items:flex-start"{a}><span class="mono dim fs10" style="width:50px;padding-top:2px">{t}</span><span class="tag {"err" if l == "ERR" else ("pau" if l == "WARN" else "run")}" style="width:34px;text-align:center">{l}</span><div class="grow"><div class="mono b7 {lv[l]}" style="font-size:10.5px">{e}</div><div class="mini">{d}</div></div></div>'
    codes = tbl(['Kod', 'Anlamı'], [['LR-E-LEAK-001', 'gelecek veriye erişim engellendi'], ['LR-E-LOCK-001/2/3', 'kilit çakışması / ikinci tahmin / kilit kaybı'], ['LR-E-SEQ-001', 'kayıt sırası bozuk'], ['LR-E-STATE-001/2', 'model durumu uyuşmuyor'], ['LR-E-PY-001', 'Python hata / zaman aşımı'], ['LR-E-DB-001', 'veritabanı işlemi başarısız'],
                                ['LR-E-REPLAY-001', 'replay tutarsız'], ['LR-E-REC-001', 'kayıt doğrulanamadı'], ['LR-E-IMP-001', 'içe aktarma doğrulaması'], ['LR-E-EXP-001', 'deney çalıştırma hatası'], ['LR-E-REPRO-001', 'REPRODUCIBILITY ERROR']], al='ll', n=5, np='TL')
    c = s_head(4, n=1) + f'<div class="row mb8" style="gap:6px;flex-wrap:wrap"{A(2, "L")}>{pill("Hepsi", "blue")}{pill("Hata 1", "line")}{pill("Uyarı 1", "line")}{pill("Bilgi 9", "line")}</div>' \
        + card('OLAYLAR', rows, style='padding:6px 8px') + card('HATA KODU SÖZLÜĞÜ (LR-E-*)', codes) + f'<div{A(6, "TL")}>' + btns(btn('Logu dışa aktar', 'ghost sm'), btn('Bilgileri temizle', 'ghost sm')) + '</div>'
    return phone(c, nav=5)

@sheet('ayar_tanilama', SEC, 'Ayarlar · Tanılama (self-test)',
       'Uygulama içi test kataloğu: veri bütünlüğü, sızıntı, replay determinizmi, kalıcılık, istatistik, aktarım ve model kuralları.',
       legend=[S1,
               (2, 'Sistem bilgisi', 'Uygulama sürümü, Room şema sürümü, Python/Chaquopy ve dataset sürümü.', '§15 · §73'),
               (3, 'Özet', 'Geçen / toplam test, son çalıştırma zamanı ve [Tümünü çalıştır].', '§52'),
               (4, 'Test grupları', 'Veri bütünlüğü · Canlı giriş · Sızıntı ve replay · Kalıcılık · İstatistik · Aktarım · Model kuralları. Gruba dokununca tek tek sonuçlar açılır.', '§52'),
               (5, 'Durum simgeleri', '✓ geçti · ✗ kaldı (kod ve açıklamayla) · ● atlandı · ◔ çalışıyor.', '§52'),
               (6, 'Raporu dışa aktar', 'Sonuçlar paylaşılabilir metin olarak dışa aktarılır.')],
       refs='§15 · §52 · §73')
def s_ayar_tani():
    groups = [('Veri bütünlüğü', 'integrity · kronolojik sıra · import sırası · timestamp · 0–36 · duplicate', 6), ('Canlı giriş', 'live input · delete · enter · lock', 4), ('Sızıntı ve replay', 'no leakage · replay sırası/determinizm · artımlı/tam replay', 4),
              ('Kalıcılık', 'Room · yeniden başlatma kurtarma · LAB izolasyonu · dataset/deney sürümü', 4), ('İstatistik', 'baseline · bootstrap · permutation · calibration · counterfactual · ablation · hassasiyet · regime · drift · diversity · stress', 11),
              ('Aktarım ve önbellek', 'export/import round-trip · bozuk veri · sentetik zaman · sayfalama · cache geçersizleme', 5), ('Model kuralları', 'aday çeşitliliği · min 3/max 5 · min 2 sector/region · Table bağımsızlığı · Wheel/Table uyumu · LAB iptal/devam', 6)]
    g = ''.join(f'<div class="rowcard" style="padding:9px 10px;margin-bottom:6px"><span class="ok b7" style="font-size:16px;width:22px">✓</span><div class="tt"><b style="font-size:12.5px">{a}</b><span>{b}</span></div><span class="mono b7 ok" style="font-size:12px">{n_}/{n_}</span></div>' for a, b, n_ in groups)
    c = s_head(5, n=1) + card('SİSTEM', kv('Uygulama', f'{VER} · derleme 1', mono=True) + kv('Room şeması', 'v1 (temiz kurulum)', mono=True) + kv('Python', 'Chaquopy · 3.11', mono=True) + kv('Aktif dataset', DATASET, mono=True), n=2) \
        + card('ÖZET', '<div class="row sb"><div><div class="b7 ok" style="font-size:20px">40 / 40 geçti</div><div class="mini">son çalıştırma: bugün 14:12</div></div>' + btn('Tümünü çalıştır', 'sm', style='width:130px') + '</div>', n=3, cls='good') \
        + f'<div{A(4, "L")}>{g}</div>' \
        + f'<div class="row mb8" style="gap:10px;font-size:11.5px;color:#9FB3CC"{A(5, "L")}><span><b class="ok">✓</b> geçti</span><span><b class="bad">✗</b> kaldı</span><span><b class="dim">●</b> atlandı</span><span><b class="info">◔</b> çalışıyor</span></div>' \
        + btn('Raporu dışa aktar', 'ghost', n=6, np='TR')
    return phone(c, nav=5)

@sheet('ayar_hakkinda', SEC, 'Ayarlar · Hakkında ve uyarılar',
       'Sürüm bilgisi, dürüstlük bildirimi, sabit uyarı metinleri ve bilimsel çerçeve. Uygulama geleceği bildiğini iddia etmez.',
       legend=[S1,
               (2, 'Sürüm', 'Uygulama adı, sürüm, derleme ve temel mantığın kaynağı (FanSuper 1.4 FanLab).', '§73'),
               (3, 'Dürüstlük bildirimi', '“Sistem geleceği biliyorum demeyecek.” Her tahmin geçmişten üretilir, kilitlenir, sonuç sonradan açılır, değerlendirilir ve ancak ondan sonra öğrenmeye girer.', '§74'),
               (4, 'Sabit uyarılar', 'Prompt’taki yedi uyarı metni ve ne zaman görüneceği.', '§55'),
               (5, 'Bilimsel çerçeve', 'Çok veri daha iyi tahmin ve daha dar CI verebilir; rastgele bağımsız veriden kalıcı avantaj çıkması zorunlu değildir. Uygulama bunu gizlemez, ama hipotezleri ciddi biçimde araştırır.', '§72'),
               (6, 'Gizlilik', 'Çevrimdışı çalışır; hesap, reklam ve ağ yok.', '§58')],
       refs='§55 · §58 · §72 · §73 · §74')
def s_ayar_hakkinda():
    wr = ''.join(f'<div class="row" style="padding:4px 0;gap:8px"><span class="warn">⚠</span><span style="font-size:12.5px">{t}</span></div>' for t in ['Yüksek hit oranı tek başına avantaj kanıtı değildir.', 'Düşük sample.', 'OOS zayıf.', 'Calibration zayıf.', 'Parameter stability yok.', 'Baseline’dan anlamlı ayrışma yok.', 'Leakage şüphesi.'])
    c = s_head(6, n=1) + card(None, f'<div style="text-align:center;padding:6px 0"><div style="font-size:34px">⚡</div><div class="b7" style="font-size:17px;letter-spacing:1.5px">LIGHTNING ROULETTE AI</div><div class="mini mt4">{VER} · derleme 1 · FanSuper 1.4 FanLab mantığı üzerine</div></div>', n=2) \
        + card('DÜRÜSTLÜK BİLDİRİMİ', '<div style="font-size:13px;line-height:1.5">Bu sistem <b>“geleceği biliyorum” demez</b>. Her tahmin geçmişten üretilir, kilitlenir, gerçek sonuç sonradan açılır, değerlendirilir ve ancak ondan sonra öğrenmeye girer. Amaç en yüksek yüzdeyi kovalamak değil; gerçekten tekrarlanabilir bilgi olup olmadığını kanıtlamaktır.</div>', n=3, cls='warnb') \
        + card('SABİT UYARILAR', wr, n=4) + card('BİLİMSEL ÇERÇEVE', '<div class="muted" style="line-height:1.5">Çok veri daha iyi bir tahmin ve daha dar güven aralığı sağlayabilir; ancak rastgele, bağımsız veriden kalıcı bir avantaj çıkması zorunlu değildir. Uygulama bunu gizlemez.</div>', n=5) \
        + card('GİZLİLİK', '<div class="muted">Çevrimdışı · hesap yok · reklam yok · ağ erişimi yok. Veriler yalnızca bu cihazda.</div>', n=6)
    return phone(c, nav=5)

@sheet('ayar_diyaloglar', SEC, 'Ayarlar · onay diyalogları',
       'Geri alınamaz veya canlı modeli etkileyen işlemlerde çıkan onay diyalogları.',
       legend=[('•', 'Not', 'Diyaloglar ekranın ortasında, arka plan karartılarak gösterilir; burada karşılaştırma için alt alta dizilmiştir.'),
               (1, 'Tüm veriyi sıfırla', 'Geri alınamaz; yazı yazarak onay (“SIFIRLA”).', '§60'),
               (2, 'Challenger → Champion', 'Yalnızca manuel onayla; kapılar ve özet gösterilir.', '§40 · §68'),
               (3, 'Tam replay', 'Süre tahmini ve “canlı model etkilenmez” bilgisi; arka planda çalışır.', '§60 · §70'),
               (4, 'Overlay izni', 'İzin yoksa nedenini açıklar ve sistem ayarına yönlendirir.', '§12'),
               (5, 'Python yeniden başlat', 'Kilitli tahmin etkilenmez; bu sırada Kotlin-only fallback etiketi görünür.', '§56'),
               (6, 'Deneyi iptal et', 'Kısmi sonuç CANCELLED olarak saklanır; silinmez.', '§35 · §49')],
       refs='§12 · §35 · §40 · §49 · §56 · §60 · §68 · §70')
def s_ayar_dlg():
    def d(n, t, b, acts):
        a = ''.join(f'<span class="{c}">{x}</span>' for x, c in acts)
        return f'<div class="dlg" style="width:auto;margin:0 0 12px;position:relative"{A(n, "TL")}><h4>{t}</h4><p>{b}</p><div class="acts">{a}</div></div>'
    c = d(1, 'Tüm veri sıfırlansın mı?', 'Spinler, tahminler, deneyler ve model durumu silinir. Bu işlem geri alınamaz. Onay için <b class="mono">SIFIRLA</b> yaz.', [('Vazgeç', ''), ('Sıfırla', 'fl" style="background:#C62828')]) \
        + d(2, 'Challenger Champion yapılsın mı?', 'v1.1.0-rc2 canlı modelin yerine geçer. 3 kapı geçilemedi (baseline, robustness, parametre). Yine de geçmek istiyor musun?', [('Vazgeç', ''), ('Onayla', 'dg')]) \
        + d(3, 'Tam replay başlatılsın mı?', 'Tüm geçmiş yeniden hesaplanır (≈ 2 dk). Canlı tahmin etkilenmez; ilerleme arka planda gösterilir.', [('Vazgeç', ''), ('Başlat', 'fl')]) \
        + d(4, 'Overlay izni gerekli', 'Overlay’in diğer uygulamaların üzerinde görünebilmesi için “Diğer uygulamaların üzerinde göster” izni verilmeli.', [('Şimdi değil', ''), ('Ayarı aç', 'fl')]) \
        + d(5, 'Python yeniden başlatılsın mı?', 'Kilitli tahmin etkilenmez. Bu sırada Kotlin-only fallback etiketi görünür (Python sonucu değildir).', [('Vazgeç', ''), ('Yeniden başlat', 'fl')]) \
        + d(6, 'Deney iptal edilsin mi?', 'LAB-2026-0001847 kısmi sonuçla CANCELLED olarak saklanır; silinmez.', [('Devam et', ''), ('İptal et', 'dg')])
    return phone(c, nav=5)
