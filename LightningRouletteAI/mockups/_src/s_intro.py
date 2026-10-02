# -*- coding: utf-8 -*-
"""GENEL: uygulama haritası, 1.4 → Lightning eşlemesi, tasarım sistemi"""
from lib import *
import lib as L

SECTION_ORDER = []

@sheet('uygulama_haritasi', 'GENEL', 'Uygulama haritası ve ekran dizini',
       'Alt gezinme, ana döngü ve bu pakette bulunan tüm ekranlar. Numara = sayfa numarası.',
       legend=[
           ('•', 'Alt gezinme (6 sekme)', 'Ana · Meclisler · Wheel · LAB · Veri · Ayarlar. FanSuper 1.4’teki 6 sekmenin (Ana/Meclisler/Keşif/Grafik/FAN LAB/Ayarlar) yeni uygulamaya uyarlanmış hâli.'),
           ('•', 'Ana döngü', 'Her tahmin kilitlenir; gerçek sonuç girilince değerlendirilir; sistem öğrenir; yeni tahmin kilitlenir. Uygulama geleceği bildiğini iddia etmez.', '§14 · §47 · §73'),
           ('•', 'Overlay ayrı bir giriştir', 'Diğer uygulamaların üzerinde yüzer; aynı veritabanı ve aynı tahmin motorunu kullanır. Overlay’den girilen sonuç uygulamada anında görünür.', '§12 · §13'),
           ('•', 'Çevrimdışı', 'Tüm ekranlar internetsiz çalışır; bulut, hesap veya reklam yoktur.', '§1 · §69'),
       ], refs='§1 · §12 · §14 · §54 · §73', legend_title='Notlar')
def s_map():
    secs = []
    for i, m in enumerate(L.SHEETS):
        if i < 3: continue
        if not secs or secs[-1][0] != m['section']: secs.append((m['section'], []))
        secs[-1][1].append((i, m['title']))
    ico = {'A · AÇILIŞ': '🚀', 'B · ANA': '🏠', 'C · WHEEL & MASA': '🎡', 'D · MECLİSLER': '🏛️', 'E · LAB': '🧪', 'F · VERİ': '💾', 'G · AYARLAR': '⚙️', 'H · OVERLAY': '🪟'}
    nav = '<div class="nav" style="border-radius:12px;border:1px solid #22324F;margin-bottom:10px">' + ''.join(
        f'<div class="{"on" if i == 0 else ""}"><b>{ic}</b>{lb}</div>' for i, (ic, lb) in enumerate(NAV)) + '</div>'
    o = card('Alt gezinme (her ekranın altında)', nav + muted('Overlay bu çubuğun dışındadır: servis olarak çalışır, Ayarlar › Overlay’den açılır.'))
    o += card('Ana döngü', flow(3) + muted('Kilit açıkken ikinci tahmin üretilmez; DEL/geri al, önceki anlık görüntüye döner ve aynı kilitli tahmin geri gelir (FanSuper 1.4 ile aynı).', style='margin-top:8px'))
    for s, items in secs:
        mi = ''.join(f'<div class="mi"><b>{i:02d}</b>{t}</div>' for i, t in items)
        o += card(f'{ico.get(s, "")} {s}', f'<div class="mgrid">{mi}</div>', right=pill(f'{len(items)} sayfa', 'line xs'))
    return f'<div class="board">{o}</div>'

@sheet('eslesme_1_4', 'GENEL', 'FanSuper 1.4 → Lightning Roulette AI',
       'Mantık taşıma haritası: neyi aynen alıyoruz, neyi rulete uyarlıyoruz, neyi yeni ekliyoruz.',
       legend=[
           ('•', 'Taşınan çekirdek', 'predict → LOCK → sonuç → değerlendir → öğren akışı, Replay sızıntı denetimi, Room kalıcı hafıza, Kotlin + Python (Chaquopy) meclisleri, Fixed-Share Hedge, Baş Hakem, mavi sürüklenebilir overlay, hata kodları ve olay günlüğü.'),
           ('•', 'Uyarlanan kısım', 'Alfabe 1..4 yerine 0–36 (N=37). Eksenler NUMBER/BS/OE/COMB yerine NUMBER, COLOR, PARITY, HIGH/LOW, DOZEN, COLUMN, SECTOR, REGION. Rakam yerine “aday = merkez + k komşu”.'),
           ('•', 'Yeni eklenenler', 'Wheel ve Masa ayrı koordinat sistemleri, Champion/Challenger (elle onay), LAB V2 (16 sekme + deney motoru + Robustness skoru), İçe/dışa aktarma raporu, Dataset sürümleri, Hipotez üretici.'),
           ('•', 'Konum', 'Aynı repoda, ayrı klasörde: LightningRouletteAI/ (ayrı Gradle projesi). FanSuper dosyalarına dokunulmaz. Paket adı önerisi: fan.lightningroulette — onayına sunulur.'),
       ], refs='§1 · §8 · §9 · §20–§24 · §47 · §67 · §73', legend_title='Özet')
def s_map14():
    rows = [
        ('🔵 Kotlin Meclisi — 8 üye', 'Kotlin Meclisi — 8 üye', 'Wheel · Sector · Region · Neighbor · Frequency · Pattern · Transition · Kotlin ML'),
        ('🐍 Python Meclisi — 10 üye (Chaquopy)', 'Python Meclisi — 8 üye', 'LSTM · Transformer · 1D-CNN · GBoost · HMM · kNN-DTW · Bağlam · Motif'),
        ('Fixed-Share Hedge', 'Fixed-Share Hedge', 'meclis içi yarış aynen; her eksen için ayrı AxisEnsemble'),
        ('⚖️ Baş Hakem: stacking + kalibrasyon + konformal', '⚖️ Meta-Ensemble Hakem', 'stacking + kalibrasyon + konformal aday kümesi + diversity'),
        ('Eksenler: NUMBER · BS · OE · COMB', 'Eksenler: NUMBER · COLOR · PARITY · HIGH/LOW · DOZEN · COLUMN · SECTOR · REGION', 'rulete özgü'),
        ('predict → LOCK → sonuç → öğren', 'Prediction Lock', 'tek spin için tek tahmin; kilitliyken ikinci tahmin yok'),
        ('O(1) geri alma (⌫)', 'Geri al (son spin)', 'anlık görüntüye dön; aynı kilitli tahmin geri gelir'),
        ('Rejim: 3 merkezli k-means (A/B/C)', 'Regime sekmesi', 'kümelenme · dağılım · geçiş entropisi · zero-yoğunluk'),
        ('ReplayEngine (copyOf(i) ile kesme)', 'Walk-forward Replay', 'PAST | CUT | FUTURE; determinizm + leakage kapıları'),
        ('FAN LAB — 8 sekme', 'LAB V2 — 16 sekme', 'Replay · Calibration · Diversity · Regime · Robustness · Experiments …'),
        ('🔍 Keşif Laboratuvarı', 'Hipotez Üretici', 'başarısız hipotez hafızası + data-snooping uyarısı'),
        ('Overlay: K / Py / YAN / RAKAM, son 6', 'Overlay: NEXT · TABLE · SON 8 · 0–36', 'dikey · yatay · kompakt · metin · simge'),
        ('Room/SQLite + durum blobları', 'Room + model durumu', 'SPINS · PREDICTIONS · EVALUATIONS · EXPERIMENTS'),
        ('FAN-E-* hata kodları', 'LR-E-* hata kodları', 'LEAK · LOCK · SEQ · STATE · PY · DB · REPLAY · REC + IMP · EXP'),
        ('Olay günlüğü NEW_RECORD … REPLAY_COMPLETED', 'İşlem logları', 'aynı olaylar + IMPORT_DONE · EXPERIMENT_* · MODEL_PROMOTED'),
        ('GitHub Actions ile APK', 'GitHub Actions ile APK', 'aynı yöntem; ayrı iş akışı'),
    ]
    body = f'<div class="row" style="font-size:10px;letter-spacing:1px;color:#6F7F96;margin-bottom:2px"><div style="flex:1">FANSUPER 1.4</div><div style="width:18px"></div><div style="flex:1.15">LIGHTNING ROULETTE AI</div></div>'
    for a, b, d in rows:
        body += f'<div class="mrow"><div class="a">{a}</div><div class="ar">→</div><div class="b">{b}<small>{d}</small></div></div>'
    return f'<div class="board">{card("Mantık taşıma haritası", body)}</div>'

@sheet('tasarim_sistemi', 'GENEL', 'Tasarım sistemi',
       'Renkler, rulet sayı çipleri, düğmeler, sınıf rozetleri ve uyarı stilleri. Tüm ekranlar bu bileşenlerle kurulur.',
       legend=[
           ('•', 'Renk dili', 'FanSuper Theme.kt ile aynı koyu tema (#0F1726 zemin, #16223A kart). Overlay kartı FanSuper’daki gibi mavi (#1565C0), yüzde değerleri turuncu.'),
           ('•', 'Rulet çipleri', 'Kırmızı/siyah/yeşil gerçek rulet renkleri. Wheel’de çip yuvarlak, listelerde yuvarlatılmış kare.'),
           ('•', 'Sınıf rozetleri', 'A güçlü · B umut verici · C nötr · D zayıf · F başarısız · O overfit şüphesi · L leakage şüphesi · S yetersiz sample. Hiçbir sınıf “kazanç garantisi” demek değildir.', '§44'),
           ('•', 'Taban çizgisi', 'Her çubuk/değer yanında tesadüf tabanı (sarı çizgi) gösterilir; yüksek yüzde tek başına başarı sayılmaz.', '§64'),
       ], refs='§44 · §55 · §64', legend_title='Notlar')
def s_design():
    sw_ = [('#0F1726', 'Zemin', 'bg'), ('#16223A', 'Kart', 'card'), ('#22324F', 'Çerçeve / pasif', 'border'), ('#E6EDF6', 'Metin', 'text'),
           ('#8AA3C4', 'Başlık / ikincil', 'head'), ('#1565C0', 'Birincil / overlay', 'blue'), ('#FFC531', '⚡ Vurgu / LOCK / taban', 'gold'), ('#81C784', 'Olumlu', 'ok'),
           ('#FFB74D', 'Uyarı · overlay %', 'warn'), ('#FF8A80', 'Hata', 'bad')]
    c1 = ''.join(f'<div class="sw-col"><i style="background:{h}"></i><div><b>{n}</b><code>{h}</code></div></div>' for h, n, _ in sw_[:5])
    c2 = ''.join(f'<div class="sw-col"><i style="background:{h}"></i><div><b>{n}</b><code>{h}</code></div></div>' for h, n, _ in sw_[5:])
    o = card('Renkler', f'<div class="mgrid"><div>{c1}</div><div>{c2}</div></div>')
    o += card('Yazı tipleri', '<div style="font-size:19px;font-weight:700">Başlık 19 · Roboto Bold</div><div style="font-size:13px;margin-top:3px">Gövde 13 · Roboto Regular — Türkçe: ığüşöç İĞÜŞÖÇ</div>'
              '<div class="mono" style="font-size:14px;margin-top:3px">29-k2 · 8-k2 · %50,2 · PRED-0005215</div><div class="muted mt4">Roboto Mono sayılar ve kimlikler için</div>')
    o += card('Rulet sayı çipleri', f'<div class="row" style="gap:6px;flex-wrap:wrap">{chip(0,"l")}{chip(1,"l")}{chip(2,"l")}{chip(17,"m")}{chip(14,"m")}{chip(0,"m")}{chip(8,"s")}{chip(21,"s")}{chip(0,"s")}{chip(4,"xs")}{chip(36,"xs")}</div>'
              f'<div class="row mt8" style="gap:6px"><span class="nc round l">29</span><span class="nc round l b">8</span><span class="nc round l ring">21</span><span class="muted" style="margin-left:4px">yuvarlak · altın halka = aday</span></div>')
    o += card('Düğmeler', btns(btn('Birincil'), btn('Onayla', 'green'), btn('Sil', 'red')) + spacer(6) + btns(btn('İkincil', 'gray'), btn('Çerçeve', 'ghost'), btn('Pasif', 'dis')) +
              f'<div class="row mt8" style="gap:8px;flex-wrap:wrap">{pill("Pill")}{pill("Aktif", "blue")}{pill("Başarılı", "green")}{pill("Dikkat", "amber")}{pill("Hata", "red")}{pill("Champion", "gold")}{pill("Challenger", "purple")}</div>')
    gl = ''.join(f'<div class="row" style="gap:8px;margin:3px 0">{cls_b(c)}<span style="font-size:11.5px;color:#CFE0F5">{CLS_NAMES[c]}</span></div>' for c in 'ABCDFOLS')
    o += card('Sınıf rozetleri (A/B/C/D/F/O/L/S)', f'<div class="mgrid">{gl}</div>')
    o += banner('warn', 'Dikkat: örnek uyarı — yüksek hit oranı tek başına avantaj kanıtı değildir.') + banner('bad', 'Hata: örnek — Python motoru yanıt vermedi (LR-E-PY-001).') + banner('ok', 'Başarılı: örnek — içe aktarma tamamlandı.') + banner('info', 'Bilgi: örnek — tahmin kilitli.')
    o += card('Çubuk + taban çizgisi', f'<div class="kv"><div class="k">Candidate-5 Hit</div><div style="width:130px">{bar(13.8, "#1976D2", base=13.51, mx=30)}</div><div class="v">%13,8</div></div>'
              + muted('Sarı çizgi = tesadüf tabanı (%13,51). Fark ±CI ile birlikte gösterilir.'))
    o += card('Anahtar · sekme · ilerleme', f'<div class="row" style="gap:10px;margin-bottom:8px">{sw(True)}{sw(False)}<span class="chk">✓</span><span class="chk off">✓</span></div>' + seg(['Kotlin', 'Python', 'Table', 'Hakem'], 1) + prog(62))
    return f'<div class="board">{o}</div>'
