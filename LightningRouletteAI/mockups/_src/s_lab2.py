# -*- coding: utf-8 -*-
"""E · LAB (2/2): Counterfactual … Hipotez"""
from common import *
from s_lab import L1, L2, flag, SEC

RTABS = ['Skor', 'Hassasiyet', 'Stres', 'Hata matrisi']
def rob_tabs(sel, n=None): return seg(RTABS, sel, n=n, np='TL')

# ───────────────────────── 33 Counterfactual
@sheet('lab_counterfactual', SEC, 'LAB · Counterfactual',
       '“Bu bileşen kapalı (ya da farklı) olsaydı ne olurdu?” FULL modele göre karşı-olgusal fark.',
       legend=[L1, L2,
               (3, 'Karşı-olgusal tablo', 'Wheel+Table vs Wheel · ML açık/kapalı · Pattern · Frequency · Transition · Recent · k1/k2/k3 · 3/4/5 aday. Değer: FULL’a göre Candidate-5 farkı (pp) ve %95 CI.', '§27'),
               (4, 'Yorum', 'Aralık 0’ı kapsıyorsa bileşenin katkısı kanıtlanmamıştır. Burada hiçbir karşı-olgusal fark anlamlı değil.', '§27 · §44'),
               (5, '3 / 4 / 5 aday', 'Ham isabet farkı kapsama yüzünden büyüktür; kapsama düzeltmeli fark ≈ 0. İkisi yan yana gösterilir.', '§65')],
       refs='§27 · §44 · §54 · §65')
def s_lab_cf():
    rows = [('Wheel+Table → Wheel', -0.1, -1.4, 1.2), ('ML kapalı', -0.3, -1.5, 0.9), ('Pattern kapalı', 0.1, -1.0, 1.2), ('Frequency kapalı', 0.2, -0.8, 1.2), ('Transition kapalı', -0.4, -1.6, 0.8),
            ('Recent kapalı', 0.0, -1.1, 1.1), ('k1 (FULL: k2)', -0.2, -1.5, 1.1), ('k3 (FULL: k2)', 0.1, -1.4, 1.6)]
    f1 = card('KARŞI-OLGUSAL · FULL’a göre Δ Candidate-5 (pp)', forest(rows, lo=-4, hi=4), n=3, right=pill('n=1 043', 'line xs'))
    t = tbl(['Aday', 'Ham Δ', 'Kapsama düz. Δ'], [['3 aday (14/37)', delta(-5.2), delta(+0.3)], ['4 aday (18/37)', delta(-2.7), delta(-0.1)], ['5 aday (22/37) FULL', '—', '—']], al='lrr', n=5, np='TL')
    return lab_screen(12, f1 + banner('info', 'Hiçbir karşı-olgusal fark anlamlı değil: aralıkların hepsi 0’ı kapsıyor.', n=4) + card('3 / 4 / 5 ADAY', t), n_tabs=1, n_head=2)

# ───────────────────────── 34 Ablation
@sheet('lab_ablation', SEC, 'LAB · Ablation',
       'FULL modelden bir feature grubunu çıkarıp değişimi ölçer: Frequency, Pattern, Sector, Region, Neighbor, Transition, Table, ML, Recency.',
       legend=[L1, L2,
               (3, 'FULL referansı', 'Tüm grupların açık olduğu model: Candidate-5 farkı ve %95 CI. Ablation bu referansa göre yapılır.', '§28'),
               (4, 'Grup çıkarma', 'Her satır: o grup çıkarılınca FULL’a göre değişim (pp) ve %95 CI. Negatif = grup çıkınca isabet düştü (olası katkı).', '§28'),
               (5, 'Katkı sıralaması', 'Çıkarıldığında en çok düşüşü yaratan gruplar. Tüm aralıklar 0’ı kapsadığından “kanıtlı katkı” yok.', '§28 · §44'),
               (6, 'Kuyruğa ekle', 'Ablation turu bir deney olarak arka plan kuyruğuna eklenir (LAB-2026-…).', '§49')],
       refs='§28 · §44 · §49 · §54')
def s_lab_ablation():
    rows = [('− Frequency', 0.1, -1.0, 1.2), ('− Pattern', -0.2, -1.3, 0.9), ('− Sector', -0.3, -1.4, 0.8), ('− Region', 0.1, -1.0, 1.2), ('− Neighbor', -0.4, -1.5, 0.7),
            ('− Transition', -0.5, -1.7, 0.7), ('− Table', -0.1, -1.2, 1.0), ('− ML', -0.3, -1.5, 0.9), ('− Recency', 0.0, -1.1, 1.1)]
    full = card('FULL MODEL', kv('Candidate-5 farkı', f'{sg(0.3)} <span class="dim fs10">CI [−1,8 ; +2,4]</span>', mono=True) + kv('Sınıf', cls_b('C') + ' nötr'), n=3, cls='blueb')
    f1 = card('GRUP ÇIKARMA · FULL’a göre Δ (pp)', forest(rows, lo=-3, hi=3), n=4)
    rk = card('KATKI SIRALAMASI (|Δ|)', ''.join(kv(f'{i + 1}. {a}', delta(b)) for i, (a, b, _, _) in enumerate(sorted(rows, key=lambda r: -abs(r[1]))[:4])) + muted('Aralıkların hepsi 0’ı kapsıyor → kanıtlı katkı yok.'), n=5)
    return lab_screen(13, full + f1 + rk + btn('Ablation turunu kuyruğa ekle', 'ghost', n=6, np='TL'), n_tabs=1, n_head=2)

# ───────────────────────── 35–38 Robustness
@sheet('lab_robustness_skor', SEC, 'LAB · Robustness · skor',
       'Pozitif ve negatif bileşenlerden oluşan robustness skoru (0–100) ve sınıfı. Skor garanti değildir.',
       legend=[L1, L2,
               (3, 'Robustness alt sekmeleri', 'Skor · Hassasiyet · Stres · Hata matrisi.', '§29 · §41 · §42 · §43'),
               (4, 'Gösterge', 'Skor ve sınıf (A–F, O, L, S). 41/100 → C: nötr. Skor bir garanti değil, kanıtın ne kadar tutarlı olduğunun özetidir.', '§43 · §44'),
               (5, 'Pozitif bileşenler', 'OOS performansı · calibration · stability · rejim tutarlılığı · parametre stabilitesi · diversity.', '§43'),
               (6, 'Negatif bileşenler', 'Overfit · leakage · data-snooping · yetersiz sample. Puandan düşülür.', '§43 · §34'),
               (7, 'Not', 'Robustness skoru en yüksek hit oranını aramaz; Final model seçimi ayrı kapılardan geçer (Champion/Challenger).', '§43 · §51')],
       refs='§29 · §34 · §41–§44 · §51')
def s_lab_rob():
    pos = [('OOS performansı', 12, 25), ('Calibration', 17, 20), ('Stability (dönemler)', 9, 15), ('Rejim tutarlılığı', 8, 15), ('Parametre stabilitesi', 5, 15), ('Diversity', 5, 10)]
    neg = [('Overfit', 0, 'işaret yok'), ('Leakage', 0, 'kapılar temiz'), ('Data-snooping', -10, '1 848 deney'), ('Yetersiz sample', -5, '2 rejimde n < 100')]
    pr = ''.join(f'<div class="row" style="padding:4px 0"><div style="width:130px;font-size:12px">{a}</div><div style="flex:1">{bar(v, "#43A047", mx=m, h=7)}</div><span class="mono fs11" style="width:46px;text-align:right">{v}/{m}</span></div>' for a, v, m in pos)
    nr = ''.join(kv(a, f'<span class="{"bad" if v < 0 else "dim"} mono">{v if v == 0 else "−" + str(abs(v))}</span>', sub=s) for a, v, s in neg)
    g = f'<div style="display:flex;justify-content:center">{ch.svg_gauge(41, 230)}</div><div class="row" style="justify-content:center;gap:8px;margin-top:-4px">{cls_b("C")}<b>Nötr</b><span class="dim fs11">pozitif 56 − negatif 15 = 41</span></div>'
    c = rob_tabs(0, n=3) + card('ROBUSTNESS SKORU', g, n=4, cls='blueb') + card('POZİTİF BİLEŞENLER · 56 / 100', pr, n=5) + card('NEGATİF BİLEŞENLER · −15', nr, n=6) \
        + banner('neutral', 'Skor bir garanti değildir; yalnızca kanıtın tutarlılığını özetler.', n=7, icon='📌')
    return lab_screen(14, c, n_tabs=1, n_head=2)

@sheet('lab_robustness_hassasiyet', SEC, 'LAB · Robustness · parametre hassasiyeti',
       'Komşu parametreleri test eder (örn. window 45/50/55/60/65). Tek noktada başarı OVERFIT RISK; geniş aralıkta başarı ROBUSTNESS ADAYI.',
       legend=[L1, L2,
               (3, 'Alt sekmeler', 'Hassasiyet sekmesi açık.', '§29'),
               (4, 'Isı haritası', 'Satır = window (45…65), sütun = k. Hücre = Candidate-5 farkı (pp). Altın çerçeve = komşularından kopuk tek nokta başarısı.', '§29'),
               (5, 'Kesit', 'k2 için window boyunca fark ve %95 bandı. Düz bir bant = parametreye duyarsız (iyi); tek tepe = overfit riski.', '§29'),
               (6, 'Karar', 'Tek noktada başarı → OVERFIT RISK. Geniş aralıkta başarı olsaydı → ROBUSTNESS ADAYI. Burada ikincisi yok.', '§29 · §44'),
               (7, 'Parametre kararlılığı', 'Skora giren parametre stabilitesi puanı (5/15).', '§43')],
       refs='§29 · §43 · §44')
def s_lab_rob_h():
    wins = [45, 50, 55, 60, 65]; ks = ['k1', 'k2', 'k3']
    mat = [[0.1, -0.2, 0.0], [0.3, 0.1, -0.3], [0.2, 2.4, 0.4], [-0.1, 0.2, 0.0], [0.0, -0.1, 0.3]]
    hm = ch.svg_heat(mat, [f'w{w}' for w in wins], ks, cw=62, ch=30, vmin=-2.5, vmax=2.5, mode='div', fmt=lambda v: sg(v, 1, ''), left=44, top=18, fs=11, mark=[(2, 1, '#FFC531')])
    ys = [m[1] for m in mat]
    lo = [y - 1.3 for y in ys]; hi_ = [y + 1.3 for y in ys]
    lc = ch.svg_line([dict(ys=ys, color='#64B5F6')], w=340, h=110, ymin=-2, ymax=4, band=(lo, hi_, '#64B5F6'), hlines=[(0, '', '#FFC531', True)], xlabels=[(i, f'w{w}') for i, w in enumerate(wins)], fmt=lambda v: f'{v:g}')
    c = rob_tabs(1, n=3) + card('WINDOW × k · Candidate-5 farkı (pp)', f'<div style="display:flex;justify-content:center">{hm}</div><div class="mini mt6">Altın çerçeve: w55 · k2 (+2,4) — komşuları −0,2…+0,4</div>', n=4) \
        + card('KESİT · k2', lc, n=5) \
        + card('KARAR', f'<div class="row" style="gap:8px">{cls_b("O")}<div><b>OVERFIT RISK</b><div class="mini">Tek noktada başarı (w55 · k2). Komşu parametrelerde yok.</div></div></div>' + '<div class="sep"></div>' + f'<div class="row" style="gap:8px">{cls_b("C")}<div><b>ROBUSTNESS ADAYI: yok</b><div class="mini">Geniş aralıkta tutarlı başarı gözlenmedi.</div></div></div>', n=6) \
        + card('PARAMETRE KARARLILIĞI', kv('Puan', '5 / 15', 'warn', mono=True) + kv('Kararlı hücre oranı', '1 / 15 (anlamlı: 0)', mono=True), n=7)
    return lab_screen(14, c, n_tabs=1, n_head=2)

@sheet('lab_robustness_stres', SEC, 'LAB · Robustness · stres testi',
       'Eksik, yinelenen, kısa/büyük veri, zero yoğunluğu, sektör kümelenmesi/dağılımı ve ani rejim değişimi senaryoları. Sonuçlar SİMÜLASYONDUR.',
       legend=[L1, L2,
               (3, 'Alt sekmeler', 'Stres sekmesi açık.', '§41'),
               (4, 'Senaryo listesi', 'Missing · Duplicate · Short dataset · Large dataset · Zero-heavy · Sector clustering · Sector dispersion · Sudden regime change. Her satır: ne yapıldı, uygulama nasıl davrandı.', '§41'),
               (5, 'SİM rozeti', 'Yapay stres sonucu gerçek performans olarak raporlanmaz; yalnızca dayanıklılık (çökme/yanlış alarm/bayrak) için okunur.', '§41'),
               (6, 'Çalıştır', 'Seçili senaryoları arka planda koşturur (kuyruğa girer).', '§49')],
       refs='§41 · §49')
def s_lab_rob_s():
    sc = [('Missing', '%5 spin silindi', 'çökme yok · 3 uyarı'), ('Duplicate', '%3 yinelenen kayıt', '152 / 152 yakalandı'), ('Short dataset', 'n = 200', '“Düşük sample” bayrağı'), ('Large dataset', '100 000 sentetik spin', 'replay 6 dk · UI akıcı'),
          ('Zero-heavy', 'zero oranı %10', 'zero bayrağı · tahmin sürdü'), ('Sector clustering', 'yapay kümelenme', 'rejim: clustering'), ('Sector dispersion', 'yapay dağılma', 'rejim: dispersed'), ('Sudden regime change', '#2 600’de ani değişim', 'DRIFT işaretlendi · gecikme 41 spin')]
    rows = ''.join(f'<div class="rowcard" style="margin-bottom:6px"><div class="tt"><b>{a} <span class="tag syn">SİM</span></b><span>{b}</span></div><div style="text-align:right;font-size:11.5px" class="ok">✓ {c_}</div></div>' for a, b, c_ in sc)
    c = rob_tabs(2, n=3) + f'<div{A(4, "L")}>{rows}</div>' + banner('warn', 'Yapay stres sonucu gerçek performans olarak raporlanmaz.', n=5) + btn('Seçili senaryoları çalıştır', n=6, np='TL')
    return lab_screen(14, c, n_tabs=1, n_head=2)

@sheet('lab_robustness_hata', SEC, 'LAB · Robustness · hata matrisi',
       'Hataların sınıflandırması: yanlış exact/candidate/sector/region/neighbor/table, aşırı güvenli ıska, düşük güvenli isabet, rejim hatası, model uyuşmazlığı, veri hatası.',
       legend=[L1, L2,
               (3, 'Alt sekmeler', 'Hata matrisi sekmesi açık.', '§42'),
               (4, 'Hata sınıfları', 'Wrong exact · candidate · sector · region · neighbor · table; overconfident miss; underconfident hit; regime failure; model disagreement; data error. Sayı, oran ve eğilim.', '§42'),
               (5, 'Aşırı güvenli ıska', 'Yüksek olasılık verip yanılma. %70+ güvenli tahmin olmadığından 0; yine de izlenir.', '§24 · §42'),
               (6, 'Veri hatası', 'Girdi/DB kaynaklı hatalar (0–36 dışı, yinelenen vb.) modelden ayrı sınıflandırılır.', '§42 · §62')],
       refs='§24 · §42 · §62')
def s_lab_rob_e():
    rows = [('Wrong exact', 1012, 97.0), ('Wrong candidate (5)', 899, 86.2), ('Wrong sector', 916, 87.8), ('Wrong region', 662, 63.5), ('Wrong neighbor', 415, 39.8), ('Wrong table (ort.)', 600, 57.5),
            ('Overconfident miss', 0, 0.0), ('Underconfident hit', 3, 0.3), ('Regime failure', 12, 1.2), ('Model disagreement', 852, 81.7), ('Data error', 0, 0.0)]
    r = rng(38)
    body = ''
    for a, n_, p in rows:
        sp = ch.svg_spark([max(0, p + r.gauss(0, max(1, p * 0.03))) for _ in range(8)], 52, 18, '#64B5F6' if p else '#3A4B6B')
        body += f'<tr><td>{a}</td><td class="r mono">{th(n_)}</td><td class="r mono">{pc(p, 1)}</td><td class="r">{sp}</td></tr>'
    t = f'<table class="tb"{A(4, "TL")}><tr><th>Hata sınıfı</th><th class="r">n</th><th class="r">%</th><th class="r">eğilim</th></tr>{body}</table>'
    c = rob_tabs(3, n=3) + card('HATA SINIFLARI · n = 1 043', t, right=pill('OOS', 'line xs')) + banner('info', 'Aşırı güvenli ıska: 0 — %70+ güven veren tahmin yok.', n=5) + banner('ok', 'Veri hatası: 0 — girdi doğrulaması çalışıyor.', n=6)
    return lab_screen(14, c, n_tabs=1, n_head=2)

# ───────────────────────── 39 Experiments
@sheet('lab_experiments', SEC, 'LAB · Experiments',
       'Tüm deneyler, benzersiz kimlikleri ve sınıflarıyla. Data-snooping panosu: binlerce deneyden en iyi sonucu seçmek başarı kanıtı değildir.',
       legend=[L1, L2,
               (3, 'Data-snooping panosu', 'Toplam deney, arama uzayı, en iyi / medyan sonuç, tekrarlanabilirlik, OOS, CI ve parametre stabilitesi saklanır ve gösterilir.', '§34'),
               (4, 'Süzgeç', 'Hepsi · Tamamlanan · Başarısız hafıza (silinmez) · Kuyrukta.', '§35'),
               (5, 'Deney satırı', 'Benzersiz kimlik (LAB-2026-…), hipotez, parametreler, N, Δ ve sınıf (A–F, O, L, S) ve durum (QUEUED/RUNNING/DONE/REJECTED). Dokununca rapor açılır.', '§19 · §20 · §37 · §44'),
               (6, 'L: reddedildi', 'Leakage şüphesi taşıyan deney (gelecek kovası) sonucu geçersiz sayılır; kaydı hafızada kalır.', '§33 · §35'),
               (7, 'Yeni deney', 'Deney oluşturucuyu açar (Ekran 40).', '§19')],
       refs='§19 · §20 · §33 · §34 · §35 · §37 · §44')
def s_lab_exp():
    ds = card('DATA-SNOOPING PANOSU', kv('Toplam deney · arama uzayı', '1 848 · 48 600', mono=True) + kv('En iyi · medyan', f'{sg(2.1)} (O) · {sg(-0.1)}', mono=True)
              + kv('Tekrarlanabilirlik (en iyi 9)', '1 / 9', 'warn', mono=True) + kv('OOS · parametre stabilitesi', '<span class="bad">✗</span> · <span class="bad">yok</span>')
              + muted('Binlerce deneyden en yüksek sonucu seçmek başarı kanıtı değildir.'), n=3, cls='warnb')
    fl = '<div class="row" style="gap:6px;flex-wrap:wrap;margin-bottom:8px"' + A(4, 'L') + '>' + pill('Hepsi 1 848', 'blue') + pill('Tamamlanan', 'line') + pill('Başarısız hafıza 211', 'line') + pill('Kuyrukta 2', 'line') + '</div>'
    st = {'QUEUED': 'que', 'RUNNING': 'run', 'DONE': 'done', 'REJECTED': 'rej'}
    rows = ''
    for i, (eid, hyp, par, n_, d, cl, stt) in enumerate(EXPS[:8]):
        b = cls_b(cl) if cl else '<span class="dim">…</span>'
        att = A(5, 'L') if i == 2 else (A(6, 'L') if eid.endswith('1843') else '')
        rows += (f'<div class="rowcard" style="margin-bottom:6px;padding:8px 10px"{att}><div class="tt"><b style="font-size:12px">{hyp}</b><span class="mono" style="font-size:10px">{eid} · {par}</span>'
                 f'<span style="margin-top:3px"><span class="tag {st[stt]}">{stt}</span> <span class="dim fs10">N {n_} · Δ {d} pp</span></span></div>{b}</div>')
    c = ds + fl + rows + '<div class="fab"' + A(7, 'TL') + '>+ Yeni deney</div>'
    return lab_screen(15, c, n_tabs=1, n_head=2, min_h=900)

# ───────────────────────── 40 Deney oluşturucu
@sheet('lab_deney_olusturucu', SEC, 'LAB · Yeni deney (builder)',
       'Deney matrisi: window, k, aday sayısı, yön, feature’lar, recency/decay, ensemble ağırlıkları ve Table kategorileri. Her kombinasyon benzersiz kimlik alır.',
       legend=[('•', 'Not', 'Bu sayfa LAB sekmelerinin dışında, “Yeni deney” ile açılan tam ekrandır; geri oku Experiments’a döner.'),
               (1, 'Hipotez ve veri', 'Hipotez adı, dataset sürümü ve kronolojik bölme (kilitli: rastgele shuffle yasaktır).', '§19 · §21 · §22'),
               (2, 'Window ve K', 'Window: 20/30/50/75/100/150/200/300/500/1000+. K: k1/k2/k3. Çoklu seçim kombinasyon üretir.', '§20'),
               (3, 'Aday sayısı ve yön', 'Candidates 3/4/5 ve direction: left / right / bidirectional.', '§20'),
               (4, 'Feature’lar', 'frequency · pattern · transition · sector · region · neighbor · table · ML. Seçilen gruplar modele girer.', '§20'),
               (5, 'Recency / decay', 'Recency penceresi ve decay (λ) varyasyonları.', '§20'),
               (6, 'Ensemble ve Table', 'Kotlin/Python ağırlıkları (20/80 … 80/20) ve Table kategori kombinasyonları.', '§11 · §20'),
               (7, 'Seed', 'Aynı dataset + model + parametre + seed + kod sürümü aynı sonucu üretir.', '§37 · §39'),
               (8, 'Özet ve OOS koruması', 'Kombinasyon sayısı, tahmini süre ve OOS kullanım sayacı. OOS yalnızca raporlama içindir; bu OOS ile tekrar model seçimi engellenir.', '§22 · §33'),
               (9, 'Kuyruğa ekle / Hemen çalıştır', 'Deneyler arka planda kuyruğa girer; her biri LAB-2026-… kimliği alır.', '§49')],
       refs='§11 · §19 · §20–§22 · §33 · §37 · §39 · §49', legend_title='Ekrandaki öğeler')
def s_lab_builder():
    def chips_(items, sel, n=None):
        return f'<div class="wrap"{A(n, "L")}>' + ''.join(pill(t, 'blue' if t in sel else 'line', style='font-size:12px;padding:6px 11px') for t in items) + '</div>'
    c = appbar('Yeni deney', back=True, sub='LAB · Experiment Builder') \
        + card('HİPOTEZ', f'<div class="rbox" style="height:40px;font-size:15px;font-weight:500;letter-spacing:0;justify-content:flex-start;padding-left:12px">Sector transition + k2</div><div class="row mt8" style="gap:6px">{pill(DATASET + " ▾", "line")}{pill("Kronolojik bölme 🔒", "amber xs")}</div>', n=1) \
        + card('WINDOW', chips_(['20', '30', '50', '75', '100', '150', '200', '300', '500', '1000+'], ['100', '150', '200']), n=2) \
        + card('K · ADAY SAYISI · YÖN', '<div class="mini mb6">K</div>' + chips_(['k1', 'k2', 'k3'], ['k2', 'k3']) + '<div class="mini mt8 mb6">Candidates</div>' + chips_(['3', '4', '5'], ['4', '5']) + '<div class="mini mt8 mb6">Direction</div>' + chips_(['left', 'right', 'bidirectional'], ['bidirectional']), n=3) \
        + card('FEATURE’LAR', chips_(['frequency', 'pattern', 'transition', 'sector', 'region', 'neighbor', 'table', 'ML'], ['transition', 'sector', 'neighbor']), n=4) \
        + card('RECENCY / DECAY (λ)', chips_(['λ = 0', '0,01', '0,02', '0,05'], ['0,02']), n=5) \
        + card('ENSEMBLE · TABLE', '<div class="mini mb6">Kotlin / Python</div>' + chips_(['20/80', '30/70', '40/60', '50/50', '60/40', '70/30', '80/20'], ['50/50', '60/40']) + '<div class="mini mt8 mb6">Table kategorileri</div>' + chips_(['color', 'parity', 'high/low', 'dozen', 'column'], ['color', 'parity', 'high/low', 'dozen', 'column']), n=6) \
        + card('SEED', f'<div class="rbox" style="height:36px;font-size:15px;letter-spacing:0;font-weight:500;justify-content:flex-start;padding-left:12px;width:120px">42</div>', n=7) \
        + card('ÖZET', kv('Kombinasyon', '3 × 2 × 2 × 1 × 2 = <b>24 deney</b>', mono=True) + kv('Tahmini süre', '≈ 12 dk · arka plan', mono=True) + kv('OOS kullanım sayacı', '1 · yalnızca rapor', 'warn', mono=True)
              + muted('Seçim validation’da yapılır; aynı OOS’ta tekrar model seçmek yasaktır.'), n=8) \
        + f'<div{A(9, "TL")}>' + btns(btn('Kuyruğa ekle'), btn('Hemen çalıştır', 'green')) + '</div>'
    return phone(c, nav=3)

# ───────────────────────── 41 Kuyruk
@sheet('lab_kuyruk', SEC, 'LAB · Deney kuyruğu (arka plan)',
       'Uzun deneyler arka planda çalışır: ilerleme, checkpoint, duraklat / iptal / devam. Ana arayüz bloke olmaz; uygulama kapansa da kaldığı yerden sürer.',
       legend=[('•', 'Not', 'Kuyruk ekranı Experiments ve Deney oluşturucudan açılır.'),
               (1, 'Çalışan deney', 'Kimlik, hipotez, processed/total ve geçerli adım. Yüzde ve kalan tahmini süre. LAB ayrı iş parçacığında çalışır; arayüz akıcı kalır.', '§49 · §71'),
               (2, 'Kontroller', 'Duraklat · İptal · Devam. İptal edilen deney kısmi sonuçla “CANCELLED” olarak kaydedilir.', '§49 · §71'),
               (3, 'Checkpoint', 'Düzenli aralıklarla yazılır; uygulama kapanırsa son checkpoint’ten devam (resume) edilir.', '§49 · §70'),
               (4, 'Sıradakiler', 'Sıra numarası ve her biri için benzersiz kimlik. Sürükleyerek öncelik değiştirilebilir.', '§19 · §37'),
               (5, 'Duraklatılan', 'Pil tasarrufu veya elle duraklatma; “Devam” kalan yerden sürdürür.', '§71'),
               (6, 'Bildirim', 'Ön plan servis bildirimi: “LAB çalışıyor %63 · İptal”. Bildirim izni yoksa yalnızca uygulama içinde görünür.', '§49')],
       refs='§19 · §37 · §49 · §70 · §71')
def s_lab_queue():
    run = card('▶ ÇALIŞIYOR · LAB-2026-0001847', '<div style="font-size:13.5px;font-weight:500">Neighbor k3 + ML (GBoost)</div><div class="mini">w150 · k3 · c5 · bi · seed 42</div>'
               f'<div class="row sb mini" style="margin:8px 0 4px"><span>processed / total</span><b class="tx mono">657 / 1 043</b></div>{prog(63)}'
               '<div class="row sb mini mt6"><span>geçerli adım: PREDICT → LOCK → REVEAL</span><b class="tx">%63 · ≈ 4 dk</b></div>', n=1, cls='blueb', right=pill('RUNNING', 'blue xs')) \
        + f'<div class="btns" style="margin-bottom:10px"{A(2, "L")}>{btn("Duraklat", "orange sm")}{btn("İptal", "red sm")}</div>' \
        + card('CHECKPOINT', kv('Son checkpoint', '14:30:12 · spin 640', mono=True) + kv('Aralık', 'her 50 spin / 30 sn', mono=True) + kv('Uygulama yeniden başlarsa', '<span class="ok">kaldığı yerden devam</span>'), n=3)
    q = ''.join(f'<div class="rowcard" style="margin-bottom:6px;padding:8px 10px"><span class="mono b7 gold" style="width:22px">{i}</span><div class="tt"><b style="font-size:12px">{h}</b><span class="mono" style="font-size:10px">{e}</span></div>{pill("QUEUED", "line xs")}</div>' for i, (e, h) in enumerate([('LAB-2026-0001848', 'Region transition (3. derece) + recent'), ('LAB-2026-0001849', 'Sector transition + k3'), ('LAB-2026-0001850', 'Sector transition + table')], 1))
    pz = card('⏸ DURAKLATILAN', '<div class="row"><div class="grow"><div style="font-size:12.5px;font-weight:500">Pattern (mixed, len 5)</div><div class="mini mono">LAB-2026-0001844b · 412 / 1 043 · pil tasarrufu</div></div>' + btn('Devam', 'sm', style='width:70px') + '</div>', n=5)
    return phone(c_join(run, f'<div{A(4, "L")}>{q}</div>', pz) + f'<div class="toast" style="position:static;margin-top:6px"{A(6, "L")}>🔔 LAB çalışıyor %63 · [İptal] — ön plan bildirimi</div>', nav=3)

def c_join(*parts): return ''.join(parts)

# ───────────────────────── 42 Rapor
@sheet('lab_rapor', SEC, 'LAB · Deney raporu',
       'Tek bir deneyin tam raporu (LAB-2026-0001842): kimlik, parametreler, tüm hit türleri, taban, fark, kalibrasyon, CI, rejim, counterfactual/ablation, robustness, overfit/leakage ve durum.',
       legend=[('•', 'Not', 'Experiments listesinden bir satıra dokununca açılır.'),
               (1, 'Kimlik ve sürümler', 'Deney kimliği, dataset/model/feature/kod sürümü, parametre özeti (hash), seed ve zaman damgası: aynı koşullar yeniden üretilebilir.', '§37 · §38 · §39 · §50'),
               (2, 'Parametreler ve N', 'Window, k, aday sayısı, yön, feature’lar ve değerlendirilen spin sayısı (N).', '§20 · §50'),
               (3, 'Hit türleri ve fark', 'Exact · Candidate · Sector · Region · Table (+ Neighbor, Color, Parity, High/Low, Dozen, Column) — her biri taban ve fark ile.', '§50 · §64'),
               (4, 'Skorlar ve CI', 'Confidence, Brier, Log Loss ve %95 güven aralığı.', '§24 · §25 · §50'),
               (5, 'Analiz özetleri', 'Regime · counterfactual · ablation · robustness · overfit · leakage özetleri ve sınıf.', '§27–§31 · §43 · §50'),
               (6, 'Durum', 'DONE / REJECTED vb. ve sınıf (A–F, O, L, S).', '§44 · §50'),
               (7, 'Eylemler', 'JSON dışa aktar · Yeniden üret (determinizm doğrulaması) · Hipotez türet.', '§36 · §39')],
       refs='§20 · §24 · §25 · §27–§31 · §36–§39 · §43 · §44 · §50 · §64')
def s_lab_report():
    ident = kv('Experiment ID', '<span class="mono">LAB-2026-0001842</span>') + kv('Dataset · Model', f'{DATASET} · {CHAMP}', mono=True) + kv('Feature · kod sürümü', 'feat-v5 · app 1.0.0 / code 7f3a2c1', mono=True) \
            + kv('Parametre hash · seed', 'b92e41… · 42', mono=True) + kv('Zaman damgası', '02.10.2026 13:58:41', mono=True)
    par = kv('Parametreler', 'w150 · k2 · bidirectional · 5 aday', mono=True) + kv('Feature’lar', 'sector · transition', mono=True) + kv('N (değerlendirilen)', '1 043', mono=True)
    hits = tbl(['Hit', 'Gözlenen', 'Taban', 'Δ pp'], [['Exact', pc(2.97, 2), pc(2.70, 2), delta(0.27, 2)], ['Candidate', pc(13.81, 2), pc(13.51, 2), delta(0.30, 2)], ['Sector', pc(12.2, 2), pc(11.9, 2), delta(0.3, 2)], ['Region', pc(36.5, 2), pc(36.0, 2), delta(0.5, 2)], ['Table (ort.)', pc(42.6, 2), pc(42.2, 2), delta(0.4, 2)]], al='lrrr')
    sk = kv('Confidence (ort.)', '%2,9', mono=True) + kv('Brier · Log Loss', '0,0258 · 3,6127', mono=True) + kv('Cand-5 %95 CI', '[%11,8 ; %16,0]', mono=True)
    an = kv('Regime', '8 / 11 aynı yönde', mono=True) + kv('Counterfactual', 'anlamlı fark yok') + kv('Ablation', 'anlamlı katkı yok') + kv('Robustness', '41 / 100 (C)', mono=True) + kv('Overfit · Leakage', '<span class="ok">yok · yok</span>')
    c = appbar('Deney raporu', back=True, sub='LAB-2026-0001842 · Sector transition + k2') + card('KİMLİK VE SÜRÜMLER', ident, n=1) + card('PARAMETRELER', par, n=2) + card('HIT TÜRLERİ VE TABAN', hits, n=3) \
        + card('SKORLAR VE CI', sk, n=4) + card('ANALİZ ÖZETLERİ', an, n=5) \
        + f'<div class="row" style="gap:8px;margin-bottom:10px"{A(6, "L")}>{cls_b("C")}<b>DONE · Nötr</b><span class="dim fs11">Baseline’dan anlamlı ayrışma yok.</span></div>' \
        + f'<div{A(7, "TL")}>' + btns(btn('JSON', 'ghost sm'), btn('Yeniden üret', 'ghost sm'), btn('Hipotez türet', 'ghost sm')) + '</div>'
    return phone(c, nav=3)

# ───────────────────────── 43 Hipotez üretici + başarısız hafıza
@sheet('lab_hipotez', SEC, 'LAB · Hipotez üretici ve başarısız hipotez hafızası',
       'İyi görünen bir bileşenden kontrollü yeni hipotezler üretir; her biri bağımsız OOS testine girer. Başarısız deneyler silinmez.',
       legend=[('•', 'Not', 'Experiments ve Overview’dan açılır.'),
               (1, 'Kaynak hipotez', 'En iyi görünen bileşen (burada “sector transition + k2”, sınıf C). Üretici bundan kontrollü varyasyonlar çıkarır.', '§36'),
               (2, 'Önerilen hipotezler', '+k3 · +table · +recent · +ML vb. Her satır kuyruğa eklenebilir ve bağımsız OOS testine girer; sonuç garanti edilmez.', '§36'),
               (3, 'Bağımsız OOS', 'Yeni hipotez, kaynağın seçildiği OOS ile değerlendirilmez; ayrı pencere kullanılır.', '§22 · §36'),
               (4, 'Başarısız hipotez hafızası', 'Başarısız deneyler silinmez: hipotez, parametreler, dataset, sonuç, başarısızlık nedeni, N ve zaman damgası saklanır.', '§35'),
               (5, 'Başarısızlık nedeni', 'Neden etiketi: OOS’ta tutmadı · leakage şüphesi · yetersiz sample · overfit. Aynı hipotez yeniden önerilmeden önce uyarı verilir.', '§35'),
               (6, 'Silinemez', 'Hafıza kayıtları kilitlidir; yalnızca dışa aktarılabilir.', '§35')],
       refs='§22 · §35 · §36')
def s_lab_hip():
    src = card('KAYNAK', f'<div class="row" style="gap:8px">{cls_b("C")}<div class="grow"><b>Sector transition + k2</b><div class="mini mono">LAB-2026-0001842 · Δ +0,3 pp</div></div></div>', n=1)
    sug = [('+ k3', 'k2 → k3 (7 cep)'), ('+ table', 'Table uyumu eklenir'), ('+ recent', 'recency λ = 0,02'), ('+ ML', 'GBoost sinyali eklenir')]
    sg_ = ''.join(f'<div class="rowcard" style="margin-bottom:6px;padding:8px 10px"><div class="tt"><b style="font-size:12.5px">Sector transition {a}</b><span>{b}</span></div>{btn("Kuyruğa", "sm", style="width:74px;height:30px;font-size:12px")}</div>' for a, b in sug)
    fails = [('Region transition (2. derece)', 'OOS’ta tutmadı', '1 043', '2026-09-30'), ('Frequency (future bucket)', 'Leakage şüphesi', '1 043', '2026-10-01'), ('Wheel+Table agreement', 'Yetersiz sample (n=212)', '212', '2026-10-01'), ('Pattern (mixed, len 4)', 'Overfit şüphesi', '1 043', '2026-10-02')]
    fm = ''.join(f'<div class="rowcard" style="margin-bottom:6px;padding:8px 10px"{A(5, "L") if i == 1 else ""}><div class="tt"><b style="font-size:12px">{h}</b><span><span class="tag rej">{why}</span> <span class="dim fs10">N {n_} · {d}</span></span></div><span style="font-size:14px">🔒</span></div>' for i, (h, why, n_, d) in enumerate(fails))
    c = src + card('ÖNERİLEN HİPOTEZLER', f'<div{A(2, "L")}>{sg_}</div><div class="mini">Her biri ayrı, bağımsız OOS penceresinde test edilir.</div>', n=3) + card('BAŞARISIZ HİPOTEZ HAFIZASI', fm + '<div class="mini">Hipotez · parametreler · dataset · sonuç · neden · N · zaman damgası saklanır.</div>', n=4, right=pill('211 kayıt', 'line xs')) \
        + banner('neutral', 'Başarısız deneyler silinemez; yalnızca dışa aktarılabilir.', n=6, icon='🔒')
    return phone(c, nav=3)
