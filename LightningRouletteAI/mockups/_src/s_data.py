# -*- coding: utf-8 -*-
"""F · VERİ"""
from common import *

SEC = 'F · VERİ'
DTABS = ['Spinler', 'İçe aktar', 'Sürümler', 'Dışa aktar / Yedek']

def d_head(sel, n=None):
    return head_row('💾 Veri', pill(f'{DATASET} · {th(SPINS)} spin', 'line xs')) + hs(DTABS, sel, n=n, np='TL')

def d_tabs_legend(n=1):
    return (n, 'Veri alt sekmeleri', 'Spinler · İçe aktar · Sürümler · Dışa aktar / Yedek.', '§16 · §18 · §38 · §59')

@sheet('veri_spinler', SEC, 'Veri · Spinler',
       'Tüm spinlerin listesi (EN YENİ → EN ESKİ), sayfalamalı. Kaynak etiketleri, sentetik zaman damgası uyarısı ve seçili satır işlemleri.',
       legend=[d_tabs_legend(1),
               (2, 'Özet ve sayfalama', '5 214 kayıt 50’şerli sayfalarda gösterilir (10 bin / 100 bin+ kayıtta bile arayüz akıcı kalır).', '§49'),
               (3, 'Süzgeçler', 'Kaynak: LIVE (canlı giriş) · IMP (içe aktarılan) · SYN (örnek/sentetik). Sayı veya # ile arama.', '§15 · §16'),
               (4, 'Spin satırı', '#kimlik · sayı çipi · zaman damgası · kaynak etiketi. İçe aktarmada batch kimliği de tutulur.', '§15 · §61'),
               (5, 'SYNTHETIC_IMPORT', 'Gerçek zaman damgası yoksa başlangıç zamanı verilir, kayıtlar 1’er dakika artırılır ve timestamp_type=SYNTHETIC_IMPORT olarak işaretlenir. Yapay zaman gerçek zaman gibi yorumlanmaz.', '§17'),
               (6, 'Sıralama notu', 'Arayüz/veritabanı EN YENİ → EN ESKİ; analiz ve replay EN ESKİ → EN YENİ.', '§16 · §61'),
               (7, 'Seçim modu', 'Satıra uzun basınca seçim açılır. Silme, bağımlı tahmin/replay/model durumunu geçersiz kılar ve önce etkiyi gösterir (Ekran 49).', '§60'),
               (8, 'İşlem çubuğu', 'Sil · Düzenle · İptal. Düzenleme de silme gibi bağımlı hesapları geçersiz kılar.', '§60')],
       refs='§15 · §16 · §17 · §49 · §60 · §61')
def s_veri_spinler():
    times = ['14:31:44', '14:30:49', '14:29:58', '14:29:01', '14:28:12', '14:27:20', '14:26:31', '14:25:37']
    vals = LAST8 + [26, 33]
    def row(i, v, t, src, sel=False, chk=True, synth=False):
        cb = f'<span class="chk {"" if sel else "off"}">✓</span>' if chk else ''
        tg = {'LIVE': '<span class="tag live">LIVE</span>', 'IMP': '<span class="tag imp">IMP</span>'}[src]
        sy = ' <span class="tag syn">SENTETİK ZAMAN</span>' if synth else ''
        bg = 'background:rgba(21,101,192,.18);' if sel else ''
        return (f'<div class="row" style="padding:6px 4px;border-bottom:1px solid #1E2A3D;{bg}gap:9px">{cb}<span class="mono dim fs11" style="width:46px">#{th(i)}</span>{chip(v, "m")}'
                f'<div class="grow"><div class="mini">{t}</div></div>{tg}{sy}</div>')
    rows = ''.join(row(SPINS - j, vals[j], f'02.10.2026 {times[j]}', 'LIVE', sel=(j == 3)) for j in range(8))
    rows = rows.replace('<div class="row" style="padding:6px 4px;border-bottom:1px solid #1E2A3D;background', f'<div{A(7, "L")} class="row" style="padding:6px 4px;border-bottom:1px solid #1E2A3D;background', 1)
    gap = '<div class="mini c" style="padding:6px 0">· · · 4 898 kayıt · · ·</div>'
    imp = ''.join(row(4900 - j, [11, 30, 8][j], f'02.10.2026 01:{39 - j}', 'IMP', synth=True) for j in range(3))
    imp = imp.replace('<div class="row"', f'<div{A(5, "L")} class="row"', 1)
    flt = f'<div class="row mb8" style="gap:6px;flex-wrap:wrap"{A(3, "L")}>{pill("Hepsi 5 214", "blue")}{pill("LIVE 314", "line")}{pill("IMP 4 900", "line")}{pill("SYN 0", "line")}</div>'
    pg = f'<div class="row" style="justify-content:center;gap:14px;margin-top:8px;font-size:12px"><span class="dim">‹</span><b>1 / 105</b><span class="info">›</span><span class="dim fs11">50 / sayfa</span></div>'
    bar_ = f'<div class="sheetb"{A(8, "TL")} style="padding-top:10px"><div class="row" style="justify-content:space-between"><b style="font-size:13px">1 seçili</b>{btn("Düzenle", "ghost sm", style="width:92px")}{btn("Sil", "red sm", style="width:78px")}{btn("İptal", "ghost sm", style="width:78px")}</div></div>'
    c = d_head(0, n=1) + f'<div class="row mb8"{A(2, "L")}>{pill("Sayfa 1 / 105", "line")}{pill("EN YENİ → EN ESKİ", "line", n=6, np="TR")}</div>' + flt \
        + card(None, f'<div{A(4, "L")}>{rows}</div>{gap}{imp}{pg}', style='padding:6px 8px 10px') + '<div style="height:70px"></div>'
    return phone(c, nav=4, modal=bar_)

@sheet('veri_ice_aktar', SEC, 'Veri · İçe aktar',
       'İçe aktarma sihirbazının ilk adımı: kaynak türü, sıra bildirimi, sütun eşleme, zaman damgası ve birleştirme seçeneği.',
       legend=[d_tabs_legend(1),
               (2, 'Kaynak türü', 'TXT · CSV · JSON · Export paketi · Ekran görüntüsü (OCR). Dosya Android dosya seçicisiyle seçilir; geniş depolama izni gerekmez.', '§16'),
               (3, 'Seçilen dosya', 'Dosya adı, boyut ve satır sayısı. OCR ile okunan sayılar her zaman raporda elle düzeltilebilir; resimler veritabanına konmaz.', '§16 · §48 · §62'),
               (4, 'Sıra bildirimi', 'Dosyanın hangi sırada olduğunu sen bildirirsin; bu sıra korunur. Arayüzde en yeni üstte, analizde en eski önce kullanılır.', '§16 · §61'),
               (5, 'Sütun eşleme', 'CSV/JSON için değer ve (varsa) zaman sütunu seçilir.', '§16'),
               (6, 'Zaman damgası', 'Dosyada gerçek zaman yoksa başlangıç zamanı verilir; kayıtlar 1’er dakika artırılır ve SYNTHETIC_IMPORT işaretlenir.', '§17'),
               (7, 'Birleştirme', 'Yeni dataset veya mevcut dataset’e ekleme (her ikisinde de yeni sürüm). Overlap/duplicate denetimi ve import batch kimliği her zaman açıktır.', '§38 · §61'),
               (8, 'Doğrula', 'Hiçbir şey kaydedilmeden önce TOTAL / VALID / INVALID / DUPLICATE / NEW raporu üretilir (Ekran 46).', '§16 · §62')],
       refs='§16 · §17 · §38 · §48 · §61 · §62')
def s_veri_ice():
    def src(ic, t, sel=False):
        return f'<div style="flex:1;text-align:center;padding:9px 2px;border-radius:10px;background:{"#1565C0" if sel else "#16223A"};border:1px solid {"#1565C0" if sel else "#22324F"};font-size:11px"><div style="font-size:19px">{ic}</div>{t}</div>'
    srcs = '<div class="row" style="gap:6px">' + src('📄', 'TXT') + src('📊', 'CSV', True) + src('🧾', 'JSON') + src('📦', 'Paket') + src('📷', 'OCR') + '</div>'
    def radio(t, s, on):
        b = '#1565C0' if on else '#2C3E5E'
        dot = f'<span style="width:17px;height:17px;border-radius:50%;border:2px solid {b};flex:none;display:flex;align-items:center;justify-content:center">{"<i style=" + chr(34) + "width:7px;height:7px;border-radius:50%;background:#64B5F6" + chr(34) + "></i>" if on else ""}</span>'
        return f'<div class="row" style="padding:6px 0;gap:10px;align-items:flex-start">{dot}<div><div style="font-size:12.5px">{t}</div><div class="mini">{s}</div></div></div>'
    c = d_head(1, n=1) + card('KAYNAK TÜRÜ', f'<div{A(2, "L")}>{srcs}</div>') \
        + card('SEÇİLEN DOSYA', '<div class="row"><span style="font-size:22px">📊</span><div class="grow"><div style="font-size:13px;font-weight:500">kayit_b.csv</div><div class="mini">112 KB · 2 031 satır</div></div>' + btn('Dosya seç', 'ghost sm', style='width:96px') + '</div>', n=3) \
        + card('SIRA BİLDİRİMİ', f'<div{A(4, "L")}>' + radio('Dosya EN YENİ → EN ESKİ', 'ilk satır en yeni sonuç (varsayılan)', True) + radio('Dosya EN ESKİ → EN YENİ', 'ilk satır en eski sonuç', False) + '</div>'
               + muted('Bildirdiğin sıra korunur. Arayüz: en yeni üstte · analiz: en eski önce.')) \
        + card('SÜTUN EŞLEME', kv('Değer sütunu', '<span class="val">number ▾</span>') + kv('Zaman sütunu', '<span class="val">(yok) ▾</span>'), n=5) \
        + card('ZAMAN DAMGASI', kv('Gerçek zaman yok', 'başlangıç zamanı gerekli') + kv('Başlangıç', '01.10.2026 18:00', mono=True) + kv('Adım', '+ 1 dk', mono=True) + kv('timestamp_type', '<span class="tag syn">SYNTHETIC_IMPORT</span>'), n=6, cls='warnb') \
        + card('BİRLEŞTİRME', radio('Mevcut dataset’e ekle', 'DATASET-003 → yeni sürüm DATASET-004', True) + radio('Yeni, ayrı dataset oluştur', 'DATASET-004 (ayrı)', False) + muted('Overlap / duplicate denetimi ve import batch kimliği her zaman açık.'), n=7) \
        + btn('Doğrula ve rapor oluştur', n=8, np='TL')
    return phone(c, nav=4)

@sheet('veri_rapor', SEC, 'Veri · İçe aktarma raporu',
       'Kayıt yapılmadan önceki doğrulama raporu: TOTAL / VALID / INVALID / DUPLICATE / NEW ve hata türleri. Onay verilmeden hiçbir şey kaydedilmez.',
       legend=[d_tabs_legend(1),
               (2, 'Rapor sayaçları', 'TOTAL = VALID + INVALID. VALID = DUPLICATE + NEW. Burada 2 031 = 2 000 + 31 ve 2 000 = 100 + 1 900.', '§16 · §62'),
               (3, 'Veri kalitesi', '0–36 dışı · boş · duplicate · OCR hatası · ters sıra · eksik veri ayrı ayrı raporlanır.', '§62'),
               (4, 'Overlap', 'Mevcut son kayıtlarla örtüşme (aynı dizi tekrar eklenmesin). Örtüşen kayıtlar atlanır.', '§61'),
               (5, 'Sorunlu satırlar', 'Her satır için neden ve öneri; OCR hataları elle düzeltilebilir.', '§62'),
               (6, 'Ters sıra uyarısı', 'Bildirilen sıra ile zaman damgaları çelişirse uyarı verilir; karar sende.', '§62'),
               (7, 'Onay', 'Yalnızca NEW kayıtları içe aktarır ve yeni dataset sürümü oluşturur (DATASET-003). İptal hiçbir şey yazmaz.', '§38 · §61')],
       refs='§16 · §38 · §61 · §62')
def s_veri_rapor():
    cnt = ('<div style="display:grid;grid-template-columns:repeat(5,1fr);gap:6px;text-align:center">'
           + ''.join(f'<div style="background:#16223A;border:1px solid #22324F;border-radius:10px;padding:8px 2px"><div class="mini" style="font-size:9px;letter-spacing:.5px">{k}</div><div class="mono b7" style="font-size:15px;color:{col_}">{v}</div></div>'
                     for k, v, col_ in [('TOTAL', '2 031', '#E6EDF6'), ('VALID', '2 000', '#81C784'), ('INVALID', '31', '#FF8A80'), ('DUPLICATE', '100', '#FFB74D'), ('NEW', '1 900', '#64B5F6')]) + '</div>')
    dq = tbl(['Sorun', 'n', 'İşlem'], [['0–36 dışı', '12', 'atlanır'], ['boş değer', '5', 'atlanır'], ['OCR hatası', '9', '<span class="lnk">düzelt ›</span>'], ['eksik veri', '5', 'atlanır'], ['<b>toplam INVALID</b>', '<b>31</b>', '']], al='lrl', n=3, np='TL')
    ov = kv('Mevcut son 100 kayıtla örtüşme', '<span class="warn">100 / 100</span>', mono=True) + kv('Karar', 'örtüşenler atlanır (DUPLICATE)')
    pr = tbl(['Satır', 'Okunan', 'Neden', ''], [['17', '37', '0–36 dışı', ''], ['88', '—', 'boş', ''], ['412', '1O', 'OCR: 10?', '<span class="lnk">10 yap</span>'], ['960', '2 5', 'ayrık rakam', '<span class="lnk">25 yap</span>']], al='lllr', n=5, np='TL')
    c = d_head(1, n=1) + card('RAPOR · kayit_b.csv', f'<div{A(2, "L")}>{cnt}</div><div class="mini mt8">TOTAL = VALID + INVALID · VALID = DUPLICATE + NEW</div>') + card('VERİ KALİTESİ', dq, right=pill('INVALID 31', 'red xs')) \
        + card('OVERLAP', ov, n=4) + card('SORUNLU SATIRLAR', pr + '<div class="mini mt6">… 27 satır daha</div>') \
        + banner('warn', 'Ters sıra şüphesi: “EN ESKİ → EN YENİ” bildirildi ama zaman damgaları azalıyor (1 blok). Sırayı kontrol et.', n=6) \
        + btns(btn('İptal', 'ghost'), btn('1 900 yeni kaydı içe aktar', n=7, np='TL'))
    return phone(c, nav=4)

@sheet('veri_surumler', SEC, 'Veri · Dataset sürümleri',
       'Her içe aktarma veya birleştirme yeni bir dataset sürümü oluşturur (DATASET-001, 002 …). Deneyler sürüme bağlıdır; aynı koşullar yeniden üretilebilir.',
       legend=[d_tabs_legend(1),
               (2, 'Aktif sürüm', 'Canlı tahmin ve LAB varsayılanı bu sürümü kullanır. Sürüm değişince modeller için yeni kilit/replay gerekir.', '§38 · §70'),
               (3, 'Sürüm satırı', 'Kimlik, spin sayısı, tarih, kaynak açıklaması ve içerik özeti (hash). Silinmez; yalnızca pasife alınabilir.', '§38'),
               (4, 'SYN etiketi', 'Örnek (sentetik) sürüm açıkça işaretlenir ve gerçek değerlendirmeye varsayılan olarak dahil edilmez.', '§17 · §73'),
               (5, 'Eylemler', 'Aktif yap · Karşılaştır · Dışa aktar.', '§18 · §38'),
               (6, 'Bağlı deneyler', 'Her deney dataset sürümüne bağlıdır; aynı dataset + model + parametre + seed + kod sürümü aynı sonucu üretmelidir.', '§37 · §39')],
       refs='§17 · §18 · §37 · §38 · §39 · §70 · §73')
def s_veri_surum():
    def ver(i, n_, d, src, active=False, syn=False, hs_='9f2c41…', exp='', n_row=None, n_act=None):
        tag = pill('AKTİF', 'green xs') if active else (pill('SYN', 'amber xs') if syn else pill('pasif', 'line xs'))
        acts = f'<div class="btns mt8"{A(n_act, "L")}>{btn("Aktif yap", "sm" if not active else "dis sm")}{btn("Karşılaştır", "ghost sm")}{btn("Dışa aktar", "ghost sm")}</div>'
        return card(f'DATASET-00{i}', f'<div class="row sb"{A(n_row, "L")}><b style="font-size:15px" class="mono">{th(n_)} spin</b><span class="mini">{d}</span></div><div class="mini mt4">{src}</div><div class="mini mono dim mt4">hash {hs_}{exp}</div>{acts}', right=tag, cls='good' if active else '')
    c = d_head(2, n=1) + ann(2, ver(4, 5214, '02.10.2026 14:31', '+314 canlı giriş (LIVE) — DATASET-003 üzerine', active=True, exp=' · 1 848 deney bağlı'), 'TL', block=True) \
        + ver(3, 4900, '01.10.2026 20:12', 'kayit_b.csv · +1 900 yeni (100 duplicate atlandı)', hs_='4ab87d…', n_row=3, n_act=5) \
        + ver(2, 3000, '01.10.2026 19:40', 'casino_a.csv · 3 000 kayıt', hs_='c10e9a…') \
        + ann(4, ver(1, 2000, '01.10.2026 09:05', 'SAMPLE · sentetik demo verisi (SYNTHETIC_IMPORT)', syn=True, hs_='71d3ff…'), 'TL', block=True) \
        + banner('neutral', 'Deneyler dataset sürümüne bağlıdır; aynı koşullar aynı sonucu üretmelidir (determinizm).', n=6, icon='📌')
    return phone(c, nav=4)

@sheet('veri_disa_aktar', SEC, 'Veri · Dışa aktar ve yedek',
       'Dataset, tahminler, deneyler ve model metadata’sını JSON/TXT/CSV olarak dışa aktar; veritabanını yedekle ve yeni cihaza geri yükle.',
       legend=[d_tabs_legend(1),
               (2, 'Biçim', 'JSON · TXT · CSV. Dışa aktarma sırası kullanıcı arayüzüyle aynıdır (en yeni → en eski) ve dosyada açıkça belirtilir.', '§18'),
               (3, 'Kapsam', 'Dataset (spinler) · tahminler · deneyler · model metadata: ayrı ayrı veya tek paket olarak.', '§18'),
               (4, 'Tek paket', 'Seçilenleri tek .lrexport dosyasında toplar.', '§18'),
               (5, 'Round-trip testi', 'Dışa aktar → içe aktar zinciri otomatik doğrulanır; kayıt sayısı ve içerik özeti eşleşmelidir.', '§18 · §52'),
               (6, 'Yedek al', 'Veritabanı + model metadata tek dosyaya. Resimler veritabanında olmadığı için yedeğe girmez.', '§48 · §59'),
               (7, 'Geri yükle', 'Cihaz değişiminde dataset, deneyler, model metadata ve tahminler geri yüklenir. Önce önizleme, sonra onay.', '§59'),
               (8, 'Otomatik yedek', 'Günlük otomatik yedek ve saklama sayısı Ayarlar › Veri & güvenlik’te.', '§59')],
       refs='§18 · §48 · §52 · §59')
def s_veri_disa():
    def chk(t, on=True): return f'<div class="row" style="padding:5px 0;gap:10px"><span class="chk {"" if on else "off"}">✓</span><span style="font-size:12.5px">{t}</span></div>'
    ex = card('DIŞA AKTAR', f'<div{A(2, "L")}>' + seg(['JSON', 'TXT', 'CSV'], 0) + '</div>' + f'<div{A(3, "L")}>' + chk('Dataset (5 214 spin)') + chk('Tahminler (5 214)') + chk('Deneyler (1 848)') + chk('Model metadata') + '</div>'
              + f'<div class="row" style="padding:5px 0"{A(4, "L")}><span class="grow" style="font-size:12.5px">Tek paket (.lrexport)</span>{sw(True)}</div>' + btn('Dışa aktar', style='margin-top:6px'))
    rt = card('ROUND-TRIP TESTİ', kv('Son test', '<span class="ok b7">✓ başarılı</span>') + kv('Kayıt eşleşmesi', '5 214 / 5 214', mono=True) + kv('İçerik özeti', '9f2c41… = 9f2c41…', mono=True) + f'<div class="mt8">{btn("Şimdi test et", "ghost sm")}</div>', n=5)
    bk = card('YEDEK', kv('Kapsam', 'veritabanı + model metadata') + kv('Son yedek', 'bugün 03:00 · 18,4 MB · otomatik', mono=True) + f'<div class="mt8">{btn("Yedek al (.lrbackup)", n=6, np="TL")}</div>')
    rs = card('GERİ YÜKLE', '<div class="muted" style="margin-bottom:8px">Yeni cihazda: dataset, deneyler, model metadata ve tahminler geri yüklenir. Dosya seçildikten sonra önce önizleme gösterilir.</div>' + btn('Yedekten geri yükle…', 'ghost', n=7, np='TL'))
    c = d_head(3, n=1) + ex + rt + bk + rs + banner('neutral', 'Günlük otomatik yedek: Ayarlar › Veri & güvenlik', n=8, icon='⚙️')
    return phone(c, nav=4)

@sheet('veri_silme_etkisi', SEC, 'Veri · silme etkisi (onay diyaloğu)',
       'Eski bir spin silinince bağımlı tahminler, replay ve model durumu geçersiz olur. Uygulama önce etkiyi gösterir ve gerekirse tam replay ister.',
       legend=[('•', 'Not', 'Spinler listesinde bir satırı seçip “Sil” denince açılır (Ekran 44).'),
               (1, 'Başlık', 'Hangi spin silinecek ve konumu.', '§60'),
               (2, 'Etki listesi', 'Geçersiz kılınan tahminler, bozulan replay/model durumu, etkilenen deneyler ve oluşacak yeni dataset sürümü. Hesaplanan gerçek sayılar gösterilir.', '§60 · §38'),
               (3, 'Tam replay zorunlu', 'Bu spinden sonraki her şey yeniden hesaplanmalıdır; işlem arka planda ilerleme göstererek yapılır.', '§60 · §70'),
               (4, 'Anladım kutusu', 'İşaretlenmeden “Sil ve replay başlat” pasiftir.', '§60'),
               (5, 'Vazgeç / Sil', 'Vazgeç hiçbir şey değiştirmez. Sil işlemi tek transaction’dır; hata olursa geri alınır.', '§69')],
       refs='§38 · §60 · §69 · §70')
def s_veri_silme():
    bg = ''.join(f'<div class="row" style="padding:7px 4px;border-bottom:1px solid #1E2A3D;gap:9px;opacity:.55"><span class="mono dim fs11" style="width:46px">#{th(5200 - j)}</span>{chip(v, "m")}<div class="grow"><div class="mini">02.10.2026 14:0{j}</div></div><span class="tag live">LIVE</span></div>' for j, v in enumerate([9, 22, 18, 5, 30, 12, 1, 25]))
    body = (f'<div{A(2, "L")}><div class="row" style="gap:8px;align-items:flex-start;margin:6px 0"><span class="bad b7">✗</span><div style="font-size:12.5px"><b>25 bağlı tahmin</b> geçersiz kılınır<div class="mini">PRED-0005191 … PRED-0005215</div></div></div>'
            '<div class="row" style="gap:8px;align-items:flex-start;margin:6px 0"><span class="bad b7">✗</span><div style="font-size:12.5px"><b>Model durumu ve replay</b> bozulur<div class="mini">#5 190’dan sonrası yeniden hesaplanır</div></div></div>'
            '<div class="row" style="gap:8px;align-items:flex-start;margin:6px 0"><span class="warn b7">!</span><div style="font-size:12.5px"><b>14 deney</b> eski sürüme bağlı kalır<div class="mini">silinmez; “eski dataset” işareti</div></div></div>'
            '<div class="row" style="gap:8px;align-items:flex-start;margin:6px 0"><span class="info b7">+</span><div style="font-size:12.5px">Yeni sürüm: <b class="mono">DATASET-005</b></div></div></div>')
    dlg = (f'<h4{A(1, "TL")}>Spin #5 190 silinsin mi?</h4>{body}<div class="bn warn" style="margin:8px 0 6px"{A(3, "L")}><span class="ic">⚠️</span><div><b>Tam replay zorunlu</b> · ≈ 2 dk · arka planda ilerleme gösterilir</div></div>'
           f'<div class="row" style="gap:10px;margin:6px 0"{A(4, "L")}><span class="chk">✓</span><span style="font-size:12.5px">Etkiyi anladım</span></div>'
           f'<div class="acts"><span>Vazgeç</span><span class="fl" style="background:#C62828"{A(5, "TR")}>Sil ve replay başlat</span></div>')
    c = d_head(0) + f'<div class="card" style="padding:6px 8px">{bg}</div>'
    return phone(c, nav=4, modal=f'<div class="scrim"><div class="dlg" style="margin-top:70px;width:340px">{dlg}</div></div>')
