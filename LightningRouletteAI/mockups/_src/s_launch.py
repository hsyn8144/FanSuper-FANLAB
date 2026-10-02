# -*- coding: utf-8 -*-
"""A · AÇILIŞ: splash, dürüstlük uyarısı, izinler, başlangıç verisi"""
from lib import *
import math, charts as ch

def logo_svg(size=120):
    cx = cy = size / 2; R = size / 2 - 3
    o = [f'<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}">']
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{R}" fill="#0B1220" stroke="#FFC531" stroke-width="2.4"/>')
    n = 12
    for i in range(n):
        a0, a1 = i * 360 / n, (i + 1) * 360 / n
        c = '#C62828' if i % 2 == 0 else '#1B1D26'
        if i == 0: c = '#2E7D32'
        o.append(f'<path d="{ch.wedge(cx, cy, R * 0.52, R * 0.93, a0, a1)}" fill="{c}" stroke="#0B1220" stroke-width="1"/>')
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{R * 0.5}" fill="#111B2E" stroke="#22324F"/>')
    s = size / 120
    bolt = [(66, 22), (40, 66), (58, 66), (50, 98), (82, 52), (62, 52), (72, 22)]
    pts = ' '.join(f'{x * s:.1f},{y * s:.1f}' for x, y in bolt)
    o.append(f'<polygon points="{pts}" fill="#FFC531" stroke="#FFE08A" stroke-width="1.2" stroke-linejoin="round"/>')
    o.append('</svg>')
    return ''.join(o)

@sheet('splash', 'A · AÇILIŞ', 'Splash · veritabanı hazırlığı',
       'Uygulama ilk açıldığında (temiz kurulum) Room veritabanı, örnek veri ve motorlar hazırlanır.',
       legend=[
           (1, 'Logo ve ad', '⚡ yıldırım + rulet. Sürüm ve “Araştırma · Tahmin · Doğrulama” alt başlığı. Garanti veya kazanç vaadi yoktur.', '§1'),
           (2, 'Genel ilerleme', 'Adımlar bitince otomatik olarak Hoş geldin sayfasına (ilk kurulum) ya da Ana’ya (sonraki açılışlar) geçilir.'),
           (3, 'Adım listesi', 'Room şeması, örnek veri, Python (Chaquopy) ve motor yükleme tek tek gösterilir. Hata olursa ilgili satır kırmızı olur ve LR-E-* kodu yazılır; uygulama açılmaya devam eder (Python yoksa Kotlin-only).', '§56 · §69'),
           (4, 'Sonraki açılış kısa yolu', 'Sonraki açılışlarda yalnızca yeni kayıtlar işlenir; “tam replay” yalnızca ilk kurulum, durum bozulması veya elle yapılır (FanSuper 1.4 ile aynı).', '§69'),
       ], refs='§1 · §56 · §69 · §73')
def s_splash():
    steps = [('ok', 'Room veritabanı oluşturuldu', 'şema v1 · temiz kurulum'), ('ok', 'Örnek veri seti yüklendi', 'SAMPLE · 2 000 spin · sentetik'),
             ('ok', 'Kotlin meclisi hazır', '8 üye'), ('run', 'Python (Chaquopy) başlatılıyor', '8 üye · ilk açılışta ~4 sn'),
             ('wait', 'Hakem ve kalibrasyon durumu', 'bekliyor'), ('wait', 'Son durum yükleniyor', 'bekliyor')]
    li = ''
    for st, t, s in steps:
        ic = {'ok': '<span class="ok">✔</span>', 'run': '<span class="info">◔</span>', 'wait': '<span class="dim">○</span>'}[st]
        li += f'<div class="row" style="padding:6px 0;border-bottom:1px solid #1E2A3D"><span style="width:18px;text-align:center">{ic}</span><div class="grow"><div style="font-size:13px;{"color:#6F7F96" if st == "wait" else ""}">{t}</div><div class="mini">{s}</div></div></div>'
    c = f'''<div style="padding:34px 8px 0;text-align:center"><div style="display:flex;justify-content:center">{ann(1, logo_svg(124), 'TR')}</div>
    <div style="font-size:23px;font-weight:900;letter-spacing:2px;margin-top:14px">LIGHTNING<br>ROULETTE AI</div>
    <div class="muted" style="margin-top:6px;letter-spacing:1.4px">ARAŞTIRMA · TAHMİN · DOĞRULAMA</div><div class="mini mt4">{VER} · derleme 1</div></div>
    <div style="margin-top:34px"{A(2, 'L')}><div class="row sb mini" style="margin-bottom:6px"><span>Hazırlanıyor…</span><span class="b7 tx">%58</span></div>{prog(58)}</div>
    <div style="margin-top:14px"{A(3, 'L')}>{li}</div>
    <div class="mini c" style="margin-top:16px;padding:0 6px"{A(4, 'L')}>Sonraki açılışlarda yalnızca yeni kayıtlar işlenir · Çevrimdışı · Veriler bu cihazda kalır</div>'''
    return phone(c, nav=None, min_h=720)

@sheet('hosgeldin', 'A · AÇILIŞ', 'Hoş geldin · dürüstlük uyarısı',
       'İlk kurulumda bir kez gösterilir. Onay kutusu işaretlenmeden “Devam” pasiftir.',
       legend=[
           (1, 'Başlık', 'Uygulamanın ne olduğunu tek cümlede söyler: bir araştırma ve doğrulama aracı.'),
           (2, 'Dürüstlük kartı', 'Rulet sonuçlarının bağımsız olduğu, geçmişteki yüksek hit oranlarının geleceği garanti etmediği açıkça yazılır. Uygulama “kesin tahmin” veya “kazanç” ifadesi kullanmaz.', '§1 · §55 · §74'),
           (3, 'Nasıl çalışır?', 'tahmin → LOCK → gerçek sonuç → değerlendirme → öğrenme döngüsü; tahmin sonuç girilmeden önce kilitlenir ve sonradan değiştirilemez.', '§14 · §47'),
           (4, 'Veri ve gizlilik', 'Her şey cihazda; internet, hesap, reklam yok. Verini istediğin an dışa aktarabilir veya silebilirsin.', '§59 · §69'),
           (5, 'Onay kutusu', 'İşaretlenince “Devam” aktif olur. Bu onay bir kez saklanır; Ayarlar › Hakkında’dan metin her zaman okunabilir.'),
           (6, 'Devam', 'İzinler sayfasına götürür.'),
       ], refs='§1 · §14 · §55 · §74')
def s_welcome():
    c = f'''<div style="padding:10px 4px 12px;text-align:center"><div style="display:flex;justify-content:center">{logo_svg(64)}</div>
    <div style="font-size:21px;font-weight:700;margin-top:10px">{ann(1, 'Başlamadan önce', 'L')}</div><div class="muted mt4">Lightning Roulette AI bir <b class="tx">araştırma ve doğrulama</b> aracıdır.</div></div>
    {card('⚠️ Hiçbir sonuç garanti değildir', '<div style="line-height:1.5;font-size:13px">Rulet sonuçları bağımsız olaylardır. Geçmiş veride görülen yüksek isabet oranı, gelecekte devam edeceği anlamına <b>gelmez</b>. Uygulama kâr veya kazanç vaat etmez; yalnızca modellerin tesadüf tabanından ayrışıp ayrışmadığını dürüstçe ölçer.</div>', n=2, cls='warnb')}
    {card('Nasıl çalışır?', f'{flow(1)}<div class="muted mt8">Tahmin, sonuç girilmeden önce <b class="tx">kilitlenir</b>. Sonra gerçek sonuçla karşılaştırılır ve sistem öğrenir. Kilitli tahmin sonradan değiştirilemez.</div>', n=3)}
    {card('Veri ve gizlilik', '<div class="muted" style="line-height:1.5">✔ Tamamen çevrimdışı · ✔ Hesap ve reklam yok · ✔ Verin yalnızca bu cihazda · ✔ İstediğin an dışa aktar / sil</div>', n=4)}
    <div class="row" style="margin:6px 2px 12px;gap:10px"{A(5, 'L')}><span class="chk">✓</span><div style="font-size:12.5px;line-height:1.4">Anladım: bu uygulama kazanç garantisi vermez ve geleceği bilemez.</div></div>
    {btn('Devam →', n=6)}'''
    return phone(c, nav=None, min_h=720)

@sheet('izinler', 'A · AÇILIŞ', 'İzinler',
       'Yalnızca gerçekten gereken izinler, her birinin nedeniyle birlikte istenir. Hepsi atlanabilir; uygulama izinsiz de çalışır.',
       legend=[
           (1, 'Overlay izni', '“Diğer uygulamaların üzerinde göster”: overlay’in başka uygulamanın üstünde yüzmesi için. [İzin ver] Android’in ilgili ayar sayfasını açar. Verilmezse overlay kullanılamaz, uygulama normal çalışır.', '§12'),
           (2, 'Bildirim izni', 'Android 13+: overlay çalışırken ön plan servisi bildirimi ve LAB arka plan ilerlemesi için.', '§49 · §71'),
           (3, 'Dosya erişimi', 'İçe/dışa aktarma Android’in dosya seçicisini kullanır; geniş depolama izni istenmez.', '§16 · §18'),
           (4, 'Ağ yok', 'Uygulama internete bağlanmaz; veri dışarı gönderilmez.', '§69'),
           (5, 'Pil kısıtı (isteğe bağlı)', 'Uzun LAB deneylerinin arka planda kesilmemesi için “kısıtlama yok” önerilir; reddedilirse deney kalan yerden devam eder (resume).', '§71'),
           (6, 'Atla / Devam', 'İzinler Ayarlar › Veri & güvenlik ve Ayarlar › Overlay sayfalarından sonradan verilebilir.'),
       ], refs='§12 · §16 · §49 · §69 · §71')
def s_perm():
    def pr(ic, t, s, st, b, n, bk='blue'):
        return (f'<div class="rowcard"{A(n)}><div class="ic">{ic}</div><div class="tt"><b>{t}</b><span>{s}</span><span style="margin-top:4px">{st}</span></div>{b}</div>')
    c = f'''<div style="padding:4px 2px 10px"><div style="font-size:19px;font-weight:700">İzinler</div><div class="muted mt4">Hepsi isteğe bağlıdır. Neden istendiği her satırda yazar.</div></div>
    {pr('🪟', 'Diğer uygulamaların üzerinde göster', 'Overlay için gerekli. Ayar sayfası açılır.', pill('Verilmedi', 'amber xs'), '<div class="btn sm" style="height:32px;font-size:12px">İzin ver</div>', 1)}
    {pr('🔔', 'Bildirimler', 'Overlay servisi ve LAB arka plan ilerlemesi.', pill('Verilmedi', 'amber xs'), '<div class="btn sm" style="height:32px;font-size:12px">İzin ver</div>', 2)}
    {pr('📁', 'Dosya erişimi', 'Android dosya seçici kullanılır; ek izin gerekmez.', pill('Gerekmiyor', 'green xs'), '<span class="ok" style="font-size:18px">✔</span>', 3)}
    {pr('🌐', 'İnternet', 'Kullanılmaz. Veri cihazdan çıkmaz.', pill('Kullanılmıyor', 'green xs'), '<span class="ok" style="font-size:18px">✔</span>', 4)}
    {pr('🔋', 'Pil kısıtı (isteğe bağlı)', 'Uzun LAB deneyleri kesilmesin.', pill('Önerilir', 'blue xs'), '<div class="btn sm" style="height:32px;font-size:12px">Ayarı aç</div>', 5)}
    <div class="btns" style="margin-top:14px"{A(6, 'TL')}>{btn('Şimdilik atla', 'ghost')}{btn('Devam →')}</div>'''
    return phone(c, nav=None, min_h=720)

@sheet('baslangic_verisi', 'A · AÇILIŞ', 'Başlangıç verisi',
       'İlk veri kaynağını seç: örnek (sentetik) veri, boş başlangıç ya da dosyadan içe aktarma.',
       legend=[
           (1, 'Örnek veri seti', '2 000 spinlik SENTETİK demo verisi. Arayüzü ve LAB’ı denemek içindir; gerçek rulet verisi değildir ve “SYN” etiketiyle işaretlenir. Gerçek değerlendirmeye varsayılan olarak katılmaz.', '§17 · §73'),
           (2, 'Boş başla', 'Veri yok; canlı girişle (ya da overlay ile) spin ekledikçe sistem öğrenir. Yeterli veri olana kadar “Öğreniyor” gösterilir ve tahmin yerine --.', '§13 · §55'),
           (3, 'Dosyadan içe aktar', 'CSV / TXT / JSON. Önce sütun eşleme ve doğrulama raporu gösterilir; hiçbir şey rapor onaylanmadan kaydedilmez.', '§16'),
           (4, 'Önizleme', 'Seçilen kaynağın ilk satırları ve özet sayıları. Sentetik veri uyarı rozetiyle gösterilir.'),
           (5, 'Başla', 'Seçime göre Ana ekrana (ya da İçe aktar sihirbazına) geçer.'),
       ], refs='§13 · §16 · §17 · §55 · §73')
def s_start():
    def opt(sel, t, s, n, extra=''):
        b = '#1565C0' if sel else '#2C3E5E'
        dot = f'<span style="width:18px;height:18px;border-radius:50%;border:2px solid {b};flex:none;display:flex;align-items:center;justify-content:center">{"<i style=" + chr(34) + "width:8px;height:8px;border-radius:50%;background:#64B5F6" + chr(34) + "></i>" if sel else ""}</span>'
        return f'<div class="rowcard" style="border-color:{b if sel else "#22324F"}"{A(n)}>{dot}<div class="tt"><b>{t}</b><span>{s}</span>{extra}</div></div>'
    prev = chips([17, 2, 31, 0, 14, 8, 21, 4, 26, 33, 12, 9], 's')
    c = f'''<div style="padding:4px 2px 10px"><div style="font-size:19px;font-weight:700">Başlangıç verisi</div><div class="muted mt4">Daha sonra Veri sekmesinden değiştirebilirsin.</div></div>
    {opt(True, 'Örnek veri seti (SAMPLE)', '2 000 spin · sentetik demo verisi', 1, '<div class="mt4"><span class="tag syn">SYN</span> <span class="tag live">çevrimdışı</span></div>')}
    {opt(False, 'Boş başla', 'Canlı giriş veya overlay ile veri ekle', 2)}
    {opt(False, 'Dosyadan içe aktar', 'CSV · TXT · JSON — önce doğrulama raporu', 3)}
    {card('Önizleme · SAMPLE', f'<div{A(4, "L")}>{prev}</div><div class="mt8">{kv("Spin sayısı", "2 000", mono=True)}{kv("Kaynak", "SYN · sentetik", "warn")}{kv("Zaman damgası", "SYNTHETIC_IMPORT · +1 dk adım", "dim")}{kv("0 oranı", "%2,65 (beklenen %2,70)", mono=True)}</div>')}
    {banner('warn', 'Sentetik veri gerçek rulet verisi değildir; yalnızca arayüzü denemek içindir.')}
    {btn('Başla →', n=5)}'''
    return phone(c, nav=None, min_h=720)
