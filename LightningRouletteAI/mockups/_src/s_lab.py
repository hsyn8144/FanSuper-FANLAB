# -*- coding: utf-8 -*-
"""E · LAB (1/2): Overview … Regime"""
from common import *
import math

SEC = 'E · LAB'
L1 = (1, 'LAB sekmeleri', '16 sekme: Overview · Models · Side/Table · Wheel · Sectors · Neighbors · Patterns · Transitions · Replay · Calibration · Diversity · Regime · Counterfactual · Ablation · Robustness · Experiments. Aktif sekme mavi; satır yana kaydırılır.', '§54')
L2 = (2, 'Üst bilgi', 'Aktif dataset sürümü ve kronolojik Train / Validation / OOS ayrımı. “LAB ≠ LIVE”: LAB canlı modeli ve kilitli tahmini değiştiremez.', '§19 · §22 · §40')

def flag(t, n=None): return banner('warn', t, n=n, style='margin-bottom:6px;padding:7px 10px')

# ───────────────────────── 21 Overview
@sheet('lab_overview', SEC, 'LAB · Overview',
       'LAB’ın özet ekranı: dataset, güncel model, son deney, OOS’ta tabandan fark, robustness ve uyarı bayrakları.',
       legend=[L1, L2,
               (3, 'Özet karoları', 'Dataset boyutu · güncel model · son deney · robustness skoru (Overview’un zorunlu içeriği).', '§54'),
               (4, 'OOS: tabandan fark', 'Her ölçüt için gözlenen − tesadüf tabanı (pp) ve %95 bootstrap aralığı. Sarı çizgi = 0 (taban). Tüm aralıklar 0’ı kapsıyor → anlamlı ayrışma yok. Exact ve Candidate ayrı satırlardır.', '§23 · §25 · §64'),
               (5, 'İstatistik özeti', 'Bootstrap (2 000 örnek) ve permütasyon testi (1 000): görülen farkın rastgele düzenlemelerde de oluşup oluşmadığı.', '§25 · §26'),
               (6, 'Uyarı bayrakları', 'Prompt’taki sabit uyarı metinleri; koşul oluşursa otomatik görünür.', '§55'),
               (7, 'Hızlı eylemler', 'Yeni deney (builder), Kuyruk ve LAB raporu sayfalarına gider.', '§19 · §50')],
       refs='§19 · §22 · §23 · §25 · §26 · §43 · §54 · §55 · §64')
def s_lab_overview():
    tl = tiles(tile('Dataset', th(SPINS), f'{DATASET} · 3 128 / 1 043 / 1 043', n=3, np='TL'), tile('Güncel model', 'v1.0.3', 'Champion · Challenger aday değil'),
               tile('Son deney', '…0001846', 'FULL ablation turu · sınıf C'), tile('Robustness', '41 / 100', 'sınıf C · garanti değildir', 'warn'))
    fo = card('OOS · TABANDAN FARK (pp, %95 CI) · n = 1 043', forest(oos_deltas(), lo=-7, hi=7) + '<div class="mini">● gözlenen fark · çizgi = %95 CI · sarı dikey = tesadüf tabanı</div>', n=4)
    st = card('İSTATİSTİK ÖZETİ', kv('Bootstrap', '2 000 örnek · %95 CI') + kv('Permütasyon (Candidate-5)', '1 000 · p = 0,31', mono=True) + kv('Aranan deney sayısı', '1 848', mono=True, sub='data-snooping: en iyi sonuç kanıt sayılmaz'), n=5)
    fl = card('UYARI BAYRAKLARI', flag('Baseline’dan anlamlı ayrışma yok.', n=6) + flag('Parameter stability yok.') + flag('Düşük sample. (bazı rejimlerde n < 100)') + flag('Yüksek hit oranı tek başına avantaj kanıtı değildir.'))
    act = btns(btn('+ Yeni deney', n=7, np='TL'), btn('Kuyruk', 'ghost'), btn('Rapor', 'ghost'))
    return lab_screen(0, tl + fo + st + fl + act, n_tabs=1, n_head=2)

# ───────────────────────── 22 Models
@sheet('lab_models', SEC, 'LAB · Models',
       'Kotlin ve Python modellerinin aynı hedef tanımıyla karşılaştırması; feature gruplarının ayrı ayrı testi.',
       legend=[L1, L2,
               (3, 'Model tablosu', 'Her model aynı hedefle (Candidate-5 isabeti) ve aynı OOS’ta ölçülür: tabandan fark, log-skor kazancı, Hedge ağırlığı ve sınıf (A–F, O, L, S).', '§10 · §44'),
               (4, 'Feature grupları', 'raw history · wheel · neighbors · sector · region · frequency · transition · pattern · color · parity · high/low · dozen · column · recency/decay. Her grup ayrı test edilir (sadece o grup eklenince fark).', '§10'),
               (5, 'Bağımsızlık notu', 'Aynı hatayı yapan modeller bağımsız kanıt sayılmaz; ayrıntı Diversity sekmesinde.', '§10 · §32')],
       refs='§10 · §32 · §44 · §54')
def s_lab_models():
    def rows(lst, cl):
        out = []
        for name, desc, w, d, ls, st in lst:
            c = cl.get(name.split(' ')[0], 'C')
            out.append([name, delta(d), f'<span class="mono fs11 dim">{sg(ls, 4, "")}</span>', pc(w * 100, 0), cls_b(c)])
        return out
    kt = tbl(['Model', 'Δ cand-5', 'Log-skor', 'Ağırlık', 'Sınıf'], rows(KOTLIN, {'Region': 'D', 'Frequency': 'D'}), al='lrrrc', n=3, np='TL')
    pt = tbl(['Model', 'Δ cand-5', 'Log-skor', 'Ağırlık', 'Sınıf'], rows(PYTHON, {'Gradient': 'B', '1D-CNN': 'D', 'kNN-DTW': 'D'}), al='lrrrc')
    fg = [('raw history', 0.0), ('wheel', 0.2), ('neighbors', 0.3), ('sector', 0.2), ('region', -0.1), ('frequency', -0.1), ('transition', 0.3), ('pattern', 0.2), ('color', 0.0), ('parity', -0.1), ('high/low', 0.1), ('dozen', 0.1), ('column', 0.0), ('recency/decay', 0.1)]
    chp = ''.join(f'<span class="pill line" style="margin:0 4px 5px 0;font-size:11px">{a} <b class="{"ok" if v > 0.05 else ("bad" if v < -0.05 else "dim")}">{sg(v, 1, "")}</b></span>' for a, v in fg)
    c = card('🔵 KOTLIN MECLİSİ', kt) + card('🐍 PYTHON MECLİSİ', pt) + card('FEATURE GRUPLARI · ayrı test (Δ cand-5, pp)', f'<div{A(4, "L")}>{chp}</div>', n=None) \
        + banner('neutral', 'Aynı hatayı yapan modeller bağımsız kanıt sayılmaz — bkz. Diversity.', n=5, icon='📌')
    return lab_screen(1, c, n_tabs=1, n_head=2)

# ───────────────────────── 23 Side/Table
@sheet('lab_side_table', SEC, 'LAB · Side / Table Analysis',
       'Table kategorilerinin (renk, tek/çift, yüksek/düşük, dozen, column) tabana karşı testi ve Wheel ile birleşik/koşullu analizler.',
       legend=[L1, L2,
               (3, 'Table kategorileri', 'COLOR · PARITY · HIGH/LOW · DOZEN · COLUMN: tabandan fark ve %95 CI. Table, sayı tahmininden bağımsız modellerle ölçülür.', '§5 · §23 · §64'),
               (4, 'Wheel + Table senaryoları', 'Wheel only · Table only · Wheel+Table · Wheel high/Table low · Wheel low/Table high · iki yüksek · iki düşük · agreement · disagreement ayrı ayrı. n < 100 olanlar “Düşük sample” ile işaretlenir.', '§6'),
               (5, 'Koşullu ölçüm', 'Wheel doğru→Table, Table doğru→Wheel, agreement, disagreement, iki yüksek güven, biri yüksek/biri düşük: ayrı oranlar.', '§45'),
               (6, 'Bağımsız teyit', 'Table yalnızca Wheel bilgisini tekrar ediyorsa bağımsız teyit sayılmaz; hata korelasyonu φ = 0,03.', '§6')],
       refs='§5 · §6 · §23 · §45 · §64')
def s_lab_side():
    sel = [r for r in oos_deltas() if r[0] in ('Color', 'Parity', 'High/Low', 'Dozen', 'Column')]
    f1 = card('TABLE KATEGORİLERİ · TABANDAN FARK (pp)', forest(sel, lo=-6, hi=6), n=3)
    sc = [('Wheel only', 1043, '13,8', '—', '+0,3', ''), ('Table only', 1043, '—', '42,6', '+0,4', ''), ('Wheel + Table', 1043, '13,8', '42,6', '+0,3', ''),
          ('Wheel yüksek / Table düşük', 214, '14,5', '41,0', '+1,0', ''), ('Wheel düşük / Table yüksek', 231, '12,6', '43,9', '−0,9', ''), ('İki yüksek', 188, '14,9', '44,1', '+1,4', ''),
          ('İki düşük', 176, '12,2', '41,7', '−1,3', ''), ('Agreement', 325, '14,2', '43,0', '+0,7', ''), ('Disagreement', 718, '13,6', '42,4', '+0,1', ''), ('Zero sonrası (Wheel+Table)', 87, '16,1', '40,2', '+2,6', 'Düşük sample')]
    rows = [[a, f'<span class="mono">{n}</span>', b, c_, f'<b>{d}</b>', f'<span class="tag syn">{e}</span>' if e else ''] for a, n, b, c_, d, e in sc]
    t2 = card('WHEEL + TABLE SENARYOLARI', tbl(['Durum', 'N', 'Wheel c5', 'Table', 'Δ pp', ''], rows, al='lrrrrl', dm=(9,)), n=4)
    t3 = card('KOŞULLU ÖLÇÜM', kv('Wheel doğru → Table doğru', f'{pc(51.8, 1)} <span class="dim fs10">n=144</span>', mono=True) + kv('Table doğru → Wheel (cand-5)', f'{pc(14.1, 1)} <span class="dim fs10">n=520</span>', mono=True)
              + kv('İki yüksek güven', f'{pc(44.1, 1)} <span class="dim fs10">n=188</span>', mono=True) + kv('Biri yüksek / biri düşük', f'{pc(42.8, 1)} <span class="dim fs10">n=445</span>', mono=True), n=5)
    t4 = banner('info', 'Wheel–Table hata korelasyonu φ = 0,03 → Table bağımsız bir sinyal kaynağı; yine de baseline’dan anlamlı ayrışma yok.', n=6)
    return lab_screen(2, f1 + t2 + t3 + t4, n_tabs=1, n_head=2)

# ───────────────────────── 24 Wheel
@sheet('lab_wheel', SEC, 'LAB · Wheel',
       'Wheel Engine testleri: dairesel mesafe dağılımı, k1/k2/k3 kapsama isabetleri, yerel yoğunluk ve zero analizi.',
       legend=[L1, L2,
               (3, 'Dairesel mesafe', 'Ardışık iki spin arasındaki en kısa çark mesafesi (0–18). Sarı kesikli çizgi: tüm cepler eşit olasılıklıysa beklenen sayı. Fark yok.', '§2'),
               (4, 'k1 / k2 / k3 isabeti', 'Bir sonraki sayı, son sayının k komşu aralığında mı? Taban = aralıktaki cep sayısı / 37. Yön varyantları (L / R) Neighbors sekmesinde.', '§2'),
               (5, 'Yerel yoğunluk', 'Son 200 spinde cep başına sıklık; çark sırasında (komşu cepler yan yana). Koyu = az, açık = çok. “Sık geldi” tek başına gerekçe değildir.', '§2 · §3 · §8'),
               (6, 'Zero analizi', 'Zero sonrası 1/2/3. spin, zero↔sector, zero↔neighbor, zero↔table ve zero frekansı ayrı ölçülür.', '§1')],
       refs='§1 · §2 · §3 · §8 · §54')
def s_lab_wheel():
    r = rng(24); n_pairs = 5213
    items = []
    for d in range(1, 19):
        e = n_pairs * 2 / 37
        items.append((str(d), round(e + r.gauss(0, math.sqrt(e) * 0.9))))
    hist = ch.svg_bars(items, w=340, h=130, base=n_pairs * 2 / 37, ymax=360, colors=['#3F6FB5'] * 18, labfs=8.5)
    kk = tbl(['Aralık', 'Cep', 'Gözlenen', 'Taban', 'Fark'], [['k1 (↔)', '3', pc(8.4, 1), pc(3 / 37 * 100, 1), delta(0.3)], ['k2 (↔)', '5', pc(13.8, 1), pc(5 / 37 * 100, 1), delta(0.3)], ['k3 (↔)', '7', pc(19.1, 1), pc(7 / 37 * 100, 1), delta(0.2)]], al='lrrrr')
    rr = rng(240); cnt = [max(0, round(5.4 + rr.gauss(0, 2.3))) for _ in range(37)]
    mx = max(cnt); cells = ''
    sv = [f'<svg width="340" height="62" viewBox="0 0 340 62">']
    for i, nn in enumerate(WHEEL):
        t = cnt[i] / mx
        col_ = '#%02x%02x%02x' % (int(24 + (66 - 24) * t), int(38 + (165 - 38) * t), int(64 + (245 - 64) * t))
        x = 2 + i * 9.1
        sv.append(f'<rect x="{x:.1f}" y="4" width="8.2" height="30" rx="2" fill="{col_}"/><text x="{x + 4.1:.1f}" y="46" font-size="6.2" fill="#8A97AA" text-anchor="middle">{nn}</text>')
    sv.append('<text x="2" y="59" font-size="8" fill="#6F7F96">çark sırası (saat yönü) →</text></svg>')
    zero = tbl(['Zero sonrası', 'N', 'Ort. mesafe', 'Beklenen'], [['+1 spin', '140', '9,2', '9,0'], ['+2 spin', '139', '8,9', '9,0'], ['+3 spin', '139', '9,1', '9,0']], al='lrrr') + kv('zero frekansı', f'{pc(2.65, 2)} <span class="dim fs10">beklenen {pc(2.70, 2)}</span>', mono=True)
    c = card('DAİRESEL MESAFE · ardışık spinler (1–18)', hist + '<div class="mini">sarı çizgi: beklenen ≈ 282 · d=0: 141 beklenen, 138 gözlenen</div>', n=3) \
        + card('k1 / k2 / k3 İSABETİ', kk, n=4) + card('YEREL YOĞUNLUK · son 200 spin', ''.join(sv), n=5) + card('ZERO ANALİZİ', zero, n=6)
    return lab_screen(3, c, n_tabs=1, n_head=2)

# ───────────────────────── 25 Sectors
@sheet('lab_sectors', SEC, 'LAB · Sectors',
       'Sektör testleri: boyut düzeltmeli frekans, sektör→sektör geçiş matrisi ve 15 test (persistence, recurrence, entropy …).',
       legend=[L1, L2,
               (3, 'Sektör frekansı', 'Gözlenen % (çubuk) ve boyut düzeltmeli beklenen % (sarı çizgi). S1 5 cepli olduğu için beklenen daha yüksektir.', '§3 · §23'),
               (4, 'Geçiş matrisi', 'Satır = bir önceki spinin sektörü, sütun = sonraki spinin sektörü; hücre = gözlenen %. Beklenen ≈ %11 (sütuna göre %10,8–13,5).', '§3 · §9'),
               (5, 'Testler', 'frequency · recent · persistence · recurrence · transition · reversal · alternation · clustering · dispersion · dwell/return · momentum · decay · entropy · transition entropy · zero. Δ, p-değeri ve sınıf.', '§3'),
               (6, 'Uyarı', '“Sık geldi” tek başına tahmin gerekçesi kabul edilmez.', '§3')],
       refs='§3 · §9 · §23 · §54')
def s_lab_sectors():
    r = rng(25); obs = []; exp = []
    for si, nums in enumerate(SECTORS):
        e = len(nums) / 37 * 100; exp.append(e); obs.append(e + r.gauss(0, 0.6))
    sv = ['<svg width="340" height="130" viewBox="0 0 340 130">']
    L, T, B = 26, 8, 22; ph = 130 - T - B; pw = 340 - L - 6; bw = pw / 9; hi = 18
    for t in range(4):
        v = hi * t / 3; y = T + ph - v / hi * ph
        sv.append(f'<line x1="{L}" x2="334" y1="{y:.1f}" y2="{y:.1f}" stroke="#1E2A3D"/><text x="{L - 4}" y="{y + 3:.1f}" font-size="9" fill="#6F7F96" text-anchor="end">{v:.0f}%</text>')
    for i in range(9):
        x = L + i * bw + bw * 0.14; bh = obs[i] / hi * ph; ey = T + ph - exp[i] / hi * ph
        sv.append(f'<rect x="{x:.1f}" y="{T + ph - bh:.1f}" width="{bw * 0.72:.1f}" height="{bh:.1f}" rx="2.5" fill="#1976D2"/>'
                  f'<line x1="{x - 2:.1f}" x2="{x + bw * 0.72 + 2:.1f}" y1="{ey:.1f}" y2="{ey:.1f}" stroke="#FFC531" stroke-width="2"/>'
                  f'<text x="{x + bw * 0.36:.1f}" y="123" font-size="9.5" fill="#8A97AA" text-anchor="middle">S{i + 1}</text>')
    sv.append('</svg>')
    r2 = rng(250)
    mat = []
    for i in range(9):
        row = [len(SECTORS[j]) / 37 * 100 + r2.gauss(0, 1.7) for j in range(9)]
        tot = sum(row); mat.append([v / tot * 100 for v in row])
    hm = ch.svg_heat(mat, [f'S{i + 1}' for i in range(9)], [f'S{i + 1}' for i in range(9)], cw=34, ch=21, vmin=5, vmax=18, mode='seq', fmt=lambda v: f'{v:.0f}', left=26, top=16, fs=9)
    tests = [('frequency', '+0,2', '0,62', 'C'), ('recent frequency (w50)', '−0,3', '0,55', 'C'), ('persistence', '+0,1', '0,80', 'C'), ('recurrence', '+0,4', '0,41', 'C'), ('transition (1. derece)', '+0,5', '0,33', 'C'),
             ('reversal', '−0,2', '0,70', 'C'), ('alternation', '0,0', '0,98', 'C'), ('clustering', '+0,6', '0,27', 'C'), ('dispersion', '−0,4', '0,51', 'C'), ('dwell / return time', '+0,1', '0,85', 'C'),
             ('momentum', '+0,3', '0,48', 'C'), ('decay (λ 0,02)', '−0,1', '0,88', 'C'), ('entropy', '0,0', '0,95', 'C'), ('transition entropy', '−0,2', '0,66', 'C'), ('zero ilişkisi', '+1,1', '0,19', 'S')]
    tt = tbl(['Test', 'Δ pp', 'p', 'Sınıf'], [[a, b, c_, cls_b(d)] for a, b, c_, d in tests], al='lrrc')
    c = card('SEKTÖR FREKANSI · gözlenen vs beklenen', ''.join(sv) + '<div class="mini">mavi: gözlenen · sarı çizgi: boyut düzeltmeli beklenen</div>', n=3) \
        + card('GEÇİŞ MATRİSİ (önceki → sonraki sektör, %)', f'<div style="display:flex;justify-content:center">{hm}</div>', n=4) \
        + card('TESTLER (OOS)', tt, n=5) + banner('neutral', '“Sık geldi” tek başına tahmin gerekçesi değildir.', n=6, icon='📌')
    return lab_screen(4, c, n_tabs=1, n_head=2)

# ───────────────────────── 26 Neighbors
@sheet('lab_neighbors', SEC, 'LAB · Neighbors',
       'Komşuluk testleri: k1/k2/k3 ve sol/sağ/iki yön; 3, 4 ve 5 aday ayrı ayrı. Kapsama (coverage) farkı her yerde açıkça gösterilir.',
       legend=[L1, L2,
               (3, 'k × yön tablosu', 'k1/k2/k3 ile ↔ (iki yön), L (saat yönünün tersi), R (saat yönü). Taban = aralıktaki cep sayısı / 37. Aralık büyüdükçe isabet tabanı da büyür.', '§2 · §20 · §23'),
               (4, 'Aday sayısı (3 / 4 / 5)', 'Her aday sayısı ayrı test edilir. 5 aday daha yüksek kapsama sağlar; bu rapor ve tabloda belirtilir.', '§65'),
               (5, 'Neighbor Hit', 'Sonuç, herhangi bir adayın komşu aralığındaysa isabet. Kapsama düzeltmeli taban: birleşik kapsama / 37.', '§64'),
               (6, 'Kapsama uyarısı', '“5 aday daha çok isabet ediyor” tek başına başarı değildir; kapsama da büyüdü.', '§65')],
       refs='§2 · §20 · §23 · §64 · §65')
def s_lab_neighbors():
    rows = []
    for k in (1, 2, 3):
        for d, lab in (('bi', '↔'), ('L', 'L'), ('R', 'R')):
            cov = len(span(0, k, d)); base = cov / 37 * 100
            r = rng(260 + k * 10 + ord(d[0])); obs = base + r.gauss(0, 0.5)
            rows.append([f'k{k}' if d == 'bi' else f'{d}{k}', lab, str(cov), pc(obs, 1), f'<span class="dim">{pc(base, 1)}</span>', delta(obs - base)])
    t1 = tbl(['Aralık', 'Yön', 'Cep', 'Gözlenen', 'Taban', 'Fark'], rows, al='lcrrrr', n=3, np='TL')
    cand = [('3 aday', 14, 8.34, 'cand-3'), ('4 aday', 18, 10.74, 'cand-4'), ('5 aday', 22, 13.81, 'cand-5')]
    t2 = tbl(['Aday', 'Kapsama', 'Cand. Hit', 'Taban', 'Neigh. Hit', 'Taban', 'Fark'],
             [['3 aday', '14/37', pc(8.34, 1), pc(8.11, 1), pc(38.4, 1), pc(14 / 37 * 100, 1), delta(0.6)], ['4 aday', '18/37', pc(10.74, 1), pc(10.81, 1), pc(48.9, 1), pc(18 / 37 * 100, 1), delta(0.3)], ['5 aday', '22/37', pc(13.81, 1), pc(13.51, 1), pc(60.2, 1), pc(22 / 37 * 100, 1), delta(0.7)]], al='lrrrrrr')
    nh = kv('Neighbor Hit (5 aday)', f'{pc(60.2, 1)} <span class="dim fs10">kapsama tabanı {pc(22 / 37 * 100, 1)}</span>', mono=True) + kv('Fark', delta(0.7) + ' <span class="dim fs10">CI [−2,3 ; +3,7]</span>') \
         + muted('Kapsama düzeltmeli taban = birleşik kapsama / 37. Aralık veya aday sayısı artınca taban da artar.')
    c = card('k × YÖN · tek aday', t1, n=None, right=pill('n=1 043', 'line xs')) + card('3 / 4 / 5 ADAY · ayrı testler', t2, n=4) + card('NEIGHBOR HIT · kapsama düzeltmeli', nh, n=5) \
        + banner('warn', '5 aday daha yüksek isabet gösterir; çünkü daha fazla çarkı kapsar (22/37). Fark taban dikkate alındığında yok denecek kadar küçük.', n=6)
    return lab_screen(5, c, n_tabs=1, n_head=2)

# ───────────────────────── 27 Patterns
@sheet('lab_patterns', SEC, 'LAB · Patterns',
       'Pattern Engine: exact/kısa/orta/uzun dizi; sayı, sektör, renk … karışık desenler. Geçmişte bulunan desen otomatik olarak “predictive” sayılmaz.',
       legend=[L1, L2,
               (3, 'Desen türleri', 'Exact · kısa/orta/uzun sequence · number/wheel/sector/region/neighbor/color/parity/dozen/column/mixed. Süzgeç çipleri.', '§7'),
               (4, 'Bulunan desenler', 'Desen, bulunma sayısı (n), sonraki sonuç oranı, taban ve OOS farkı. Sağdaki durum: OOS’ta tutmadı / düşük sample / overfit şüphesi.', '§7 · §44'),
               (5, 'Geçmiş ≠ gelecek', 'Desen geçmişte bulundu diye predictive kabul edilmez; desen sonrası sonuç, baseline ve OOS performansı hesaplanır.', '§7'),
               (6, 'Örnek: in-sample vs OOS', 'Aynı desenin eğitim ve OOS isabeti yan yana: fark çöküyorsa desen şanstır.', '§7 · §22')],
       refs='§7 · §22 · §44 · §54')
def s_lab_patterns():
    types = ['exact', 'kısa', 'orta', 'uzun', 'number', 'wheel', 'sector', 'region', 'neighbor', 'color', 'parity', 'dozen', 'column', 'mixed']
    chp = ''.join(f'<span class="pill {"blue" if t in ("sector", "mixed") else "line"}" style="margin:0 4px 5px 0">{t}</span>' for t in types)
    rows = [['S3 → S3 → S8', 'sector · 3', '41', '17,1', '10,8', '+0,3', '<span class="tag err">OOS ✗</span>'],
            ['KIRMIZI ×4 → ?', 'color · 4', '63', '52,4', '48,6', '−1,5', '<span class="tag err">OOS ✗</span>'],
            ['17 · 2 · 31', 'exact · 3', '3', '33,3', '2,7', '—', '<span class="tag syn">Düşük sample</span>'],
            ['TEK TEK ÇİFT ÇİFT', 'parity · 4', '88', '55,7', '48,6', '+2,1', '<span class="cls O" style="width:18px;height:18px;font-size:10px">O</span>'],
            ['D2 → D2 → D3', 'dozen · 3', '112', '36,6', '32,4', '+0,2', '<span class="tag err">OOS ✗</span>'],
            ['komşu zinciri ±2 · 3', 'neighbor · 3', '57', '16,8', '13,5', '+0,6', '<span class="tag syn">Düşük sample</span>']]
    t = tbl(['Desen', 'Tür', 'n', 'Sonra %', 'Taban', 'OOS Δ', 'Durum'], rows, al='llrrrrl', n=4, np='TL')
    bars = ch.svg_bars([('in-sample', 17.1), ('OOS', 11.1)], w=170, h=96, base=10.8, ymax=24, colors=['#3F6FB5', '#C62828'], pad=(24, 8, 6, 20), fmt=lambda v: f'{v:.0f}%')
    bars2 = ch.svg_bars([('in-sample', 55.7), ('OOS', 49.0)], w=170, h=96, base=48.6, ymax=70, colors=['#3F6FB5', '#C62828'], pad=(26, 8, 6, 20), fmt=lambda v: f'{v:.0f}%')
    pair = f'<div class="row" style="gap:6px;justify-content:center"><div class="c"><div class="mini">S3→S3→S8 (n=41 / 9)</div>{bars}</div><div class="c"><div class="mini">TEK TEK ÇİFT ÇİFT (88 / 24)</div>{bars2}</div></div>'
    c = card('DESEN TÜRLERİ', f'<div{A(3, "L")}>{chp}</div>') + card('BULUNAN DESENLER', t, n=None, right=pill('n küçük', 'line xs')) \
        + banner('warn', 'Desen geçmişte bulunmuş diye predictive kabul edilmez. OOS’ta tutmayan desenler otomatik elenir.', n=5) \
        + card('İN-SAMPLE vs OOS', pair, n=6)
    return lab_screen(6, c, n_tabs=1, n_head=2)

# ───────────────────────── 28 Transitions
@sheet('lab_transitions', SEC, 'LAB · Transitions',
       'Transition Engine: 1., 2., 3. derece (A→B; A,B→C; A,B,C→D) — number, sector, region, color, parity, dozen, column ayrı ayrı. Entropi ve sample size gösterilir.',
       legend=[L1, L2,
               (3, 'Derece × tür matrisi', 'OOS’ta sonraki sonucu tahmin etmenin tabandan farkı (pp). Satır = derece (1./2./3.), sütun = tür. Hücre rengi: yeşil +, kırmızı −; hepsi gürültü düzeyinde.', '§9'),
               (4, 'Entropi ve sample size', 'Geçiş entropisi (bit) ve gözlenen geçiş sayısı. Durum sayısı arttıkça (number 3. derece: 50 653 durum) örnek yetersiz kalır ve S (yetersiz sample) işaretlenir.', '§9 · §44'),
               (5, 'Entropi zaman serisi', 'Rolling geçiş entropisi; belirgin düşüş rejim analizi için işaret olabilir (Regime sekmesi).', '§9 · §30')],
       refs='§9 · §30 · §44 · §54')
def s_lab_transitions():
    r = rng(28); types = ['number', 'sector', 'region', 'color', 'parity', 'dozen', 'column']
    mat = []
    for o in range(3):
        mat.append([round(r.gauss(0, 0.5 if t not in ('number',) else 0.3), 1) for t in types])
    hm = ch.svg_heat(mat, ['1. der.', '2. der.', '3. der.'], types, cw=38, ch=26, vmin=-1.5, vmax=1.5, mode='div', fmt=lambda v: sg(v, 1, ''), left=44, top=18, fs=9.5)
    ent = [('number', 5.20, 5.21, 5213, 1), ('sector', 3.15, 3.17, 5213, 1), ('region', 1.58, 1.58, 5213, 1), ('color', 1.58, 1.58, 5213, 1), ('parity', 1.00, 1.00, 5213, 1), ('dozen', 1.58, 1.58, 5213, 1), ('column', 1.58, 1.58, 5213, 1)]
    rows = [[a, f'<span class="mono">{b:.2f}</span>', f'<span class="mono dim">{m:.2f}</span>', f'<span class="mono">{th(n)}</span>', '<span class="tag syn">S · 3. der.</span>' if a == 'number' else ''] for a, b, m, n, _ in ent]
    et = tbl(['Tür', 'H (bit)', 'H max', 'N geçiş', ''], rows, al='lrrrl', n=4, np='TL')
    ys = [0.985 + r.gauss(0, 0.008) for _ in range(40)]
    lc = ch.svg_line([dict(ys=ys, color='#64B5F6')], w=340, h=100, ymin=0.94, ymax=1.03, hlines=[(1.0, 'maks.', '#FFC531', True)], xlabels=[(0, '#1'), (20, '#2 600'), (39, '#5 214')], fmt=lambda v: f'{v:.2f}')
    c = card('DERECE × TÜR · OOS fark (pp)', f'<div style="display:flex;justify-content:center">{hm}</div>', n=3) + card('GEÇİŞ ENTROPİSİ · SAMPLE', et, n=None) \
        + card('ROLLING GEÇİŞ ENTROPİSİ (sektör, normalize)', lc, n=5)
    c = c.replace('<span>GEÇİŞ ENTROPİSİ · SAMPLE</span>', '<span>GEÇİŞ ENTROPİSİ · SAMPLE</span>')
    return lab_screen(7, c, n_tabs=1, n_head=2)

# ───────────────────────── 29 Replay
@sheet('lab_replay', SEC, 'LAB · Replay',
       'Walk-forward replay: TRAIN → PREDICT → LOCK → REVEAL → EVALUATE → UPDATE. Her adımda geçmiş fiziksel olarak kesilir; rastgele shuffle yoktur.',
       legend=[L1, L2,
               (3, 'Walk-forward adımları', 'TRAIN → PREDICT → LOCK → REVEAL → EVALUATE → UPDATE. Rastgele shuffle ile zaman serisi bölme yasaktır.', '§21'),
               (4, 'PAST | CUT | FUTURE', 'Her replay adımında geçmiş kopyalanıp kesilir; gelecek veriye erişim fiziksel olarak mümkün değildir.', '§21 · §33'),
               (5, 'Leakage kapıları', 'Sıra denetimi · son bilinen kayıt kimliği · tahmin zamanı ≤ gerçek zaman · test setiyle model seçimi yok. Biri kalırsa LR-E-LEAK-001.', '§33'),
               (6, 'Determinizm', 'Aynı dataset + model + parametre + seed + kod sürümü aynı sonucu üretmelidir; fark varsa REPRODUCIBILITY ERROR.', '§39'),
               (7, 'İlerleme', 'processed/total, geçerli deney, iptal ve devam (checkpoint). Ana arayüz bloke olmaz.', '§49 · §71'),
               (8, 'Çalıştır', 'Artımlı replay (yalnızca yeni kayıtlar), tam replay veya determinizm testi.', '§70')],
       refs='§21 · §33 · §39 · §49 · §70 · §71')
def s_lab_replay():
    steps = ''.join(f'<div class="st {"done" if i < 5 else "act"}"><i>{"✓" if i < 5 else "6"}</i>{t}</div>' + ('<div class="ln"></div>' if i < 5 else '') for i, t in enumerate(['TRAIN', 'PREDICT', 'LOCK', 'REVEAL', 'EVALUATE', 'UPDATE']))
    fl = f'<div class="flow">{steps}</div>'
    gates = ''.join(kv(a, '<span class="ok b7">✓</span> <span class="dim fs11">' + b + '</span>') for a, b in [('Kayıt sırası (eski → yeni)', '5 214 / 5 214'), ('lastKnownRecordId', 'ardışık'), ('predictionTimestamp ≤ actualTimestamp', '1 043 / 1 043'), ('Test setiyle model seçimi', 'yok'), ('Rastgele shuffle', 'kullanılmadı')])
    det = kv('Çalıştırma 1 · hash', '<span class="mono">a41f…c09</span>') + kv('Çalıştırma 2 · hash', '<span class="mono">a41f…c09</span>') + kv('Sonuç', '<span class="ok b7">Aynı ✓</span>')
    c = card('WALK-FORWARD DÖNGÜSÜ', fl, n=3) + card('PAST | CUT | FUTURE · adım 3 129', ch.svg_timeline(340, 0.6, 0.6) + '<div class="mini">PAST: #1…#3 128 → tahmin bu kesimden üretildi · FUTURE erişilemez</div>', n=4) \
        + card('LEAKAGE KAPILARI', gates, n=5) + card('DETERMİNİZM (§39)', det, n=6) \
        + card('İLERLEME', f'<div class="row sb mini" style="margin-bottom:4px"><span>processed / total</span><b class="tx mono">1 043 / 1 043</b></div>{prog(100)}<div class="mini mt6">tamamlandı · 38 sn · LAB-2026-0001846</div>', n=7) \
        + btns(btn('Artımlı', 'sm', n=8, np='TL'), btn('Tam replay', 'ghost sm'), btn('Determinizm', 'ghost sm'))
    return lab_screen(8, c, n_tabs=1, n_head=2)

# ───────────────────────── 30 Calibration
@sheet('lab_calibration', SEC, 'LAB · Calibration',
       'Tahmin olasılıklarının gerçekleşme oranlarıyla uyumu: 0–10 … 90–100 bucket’ları, Brier, Log Loss ve kalibrasyon hatası.',
       legend=[L1, L2,
               (3, 'Eksen seçici', 'Exact · Cand-5 · Color · Parity · High/Low · Dozen · Column. Olasılıklar küçük olan eksenlerde (Exact) bucket’lar boş kalır; bu normaldir.', '§24'),
               (4, 'Güvenilirlik diyagramı', 'x: tahmin edilen olasılık, y: gözlenen oran; sarı köşegen = mükemmel kalibrasyon. Nokta büyüklüğü = örnek sayısı.', '§24'),
               (5, 'Bucket tablosu', '0–10 … 90–100 bucket’ları: n, ortalama tahmin, gözlenen. Kullanılmayan bucket’lar “—”.', '§24'),
               (6, 'Skorlar', 'Brier, Log Loss, ECE (kalibrasyon hatası) ve rastgele taban karşılaştırması.', '§24'),
               (7, '%70+ güven', 'Yüksek güven (%70+) alan tahminlerin uzun dönem gerçek oranı incelenir; yoksa açıkça “örnek yok” yazılır.', '§24')],
       refs='§24 · §66')
def s_lab_calibration():
    ax = hs(['Exact', 'Cand-5', 'Color', 'Parity', 'High/Low', 'Dozen', 'Column'], 2, n=3, np='TL')
    pts = [(45, 44.1, 412), (55, 55.4, 631)]
    pts = [(46.8, 47.9, 412), (53.1, 52.2, 631)]
    rel = ch.svg_reliability(pts, w=340, h=210)
    brow = [['0–10', '0', '—', '—'], ['10–20', '0', '—', '—'], ['20–30', '0', '—', '—'], ['30–40', '0', '—', '—'], ['40–50', '412', '46,8', '47,9'], ['50–60', '631', '53,1', '52,2'], ['60–70', '0', '—', '—'], ['70–80', '0', '—', '—'], ['80–90', '0', '—', '—'], ['90–100', '0', '—', '—']]
    bt = tbl(['Bucket %', 'n', 'Tahmin', 'Gözlenen'], brow, al='lrrr', dm=(0, 1, 2, 3, 6, 7, 8, 9), n=5, np='TL')
    sc = kv('Brier (Color)', '0,2503 <span class="dim fs10">taban 0,2500</span>', mono=True) + kv('Log Loss (Color)', '0,6938 <span class="dim fs10">taban 0,6931</span>', mono=True) + kv('ECE (kalibrasyon hatası)', '0,021', mono=True) + kv('Kalibrasyon farkı', '+0,9 pp', mono=True)
    c = card('EKSEN', ax, style='padding-bottom:0') + card('GÜVENİLİRLİK DİYAGRAMI · COLOR', rel, n=4, right=pill('n = 1 043', 'line xs')) + card('BUCKET TABLOSU', bt) \
        + card('SKORLAR', sc, n=6) + banner('info', '%70+ güven alan tahmin yok (0 / 1 043) → bu aralığın gerçek oranı incelenemedi.', n=7)
    return lab_screen(9, c, n_tabs=1, n_head=2)

# ───────────────────────── 31 Diversity
@sheet('lab_diversity', SEC, 'LAB · Diversity',
       'Kotlin ve Python modellerinin uyuşması, ayrışması, ortak hataları ve benzersiz isabet/ıskaları. Aynı hatayı yapan modeller bağımsız kanıt sayılmaz.',
       legend=[L1, L2,
               (3, 'Agreement', 'İki meclisin top-1 oyu aynı olduğunda oran; ayrışma (disagreement) oranı.', '§32'),
               (4, 'İsabet matrisi', 'Her iki meclis de isabet · yalnız Kotlin · yalnız Python · ikisi de ıskaladı. “Bağımsız olsalardı” beklenen değerle yan yana.', '§32'),
               (5, 'Hata korelasyonu', '8 modelin hata korelasyon ısı haritası (φ). Yüksek korelasyon = aynı hata = bağımsız kanıt değil.', '§10 · §32'),
               (6, 'Jensen–Shannon', 'Üye olasılık dağılımları arasındaki ortalama JS ayrışması (çeşitlilik ölçüsü).', '§32')],
       refs='§10 · §32')
def s_lab_diversity():
    ag = kv('Top-1 agreement (Kotlin = Python)', '%18,4', mono=True) + kv('Disagreement', '%81,6', mono=True) + kv('Jensen–Shannon (ort.)', '0,18', mono=True)
    m22 = tbl(['', 'Python isabet', 'Python ıska'], [['Kotlin isabet', '<b>19</b> <span class="dim fs10">beklenen 19,6</span>', '<b>125</b>'], ['Kotlin ıska', '<b>124</b>', '<b>775</b>']], al='lrr', n=4, np='TL') \
          + '<div class="mini mt6">Cand-5 · n = 1 043 · ortak isabet beklenenden farksız → modeller ayrı ayrı gürültü üretiyor.</div>'
    names = ['Whl', 'Sec', 'Nbr', 'Frq', 'Pat', 'Trn', 'GB', 'LSTM']
    r = rng(31); n_ = len(names); mat = [[1.0 if i == j else 0 for j in range(n_)] for i in range(n_)]
    for i in range(n_):
        for j in range(i + 1, n_):
            v = round(abs(r.gauss(0.08, 0.07)), 2); mat[i][j] = mat[j][i] = v
    mat[1][2] = mat[2][1] = 0.46; mat[0][1] = mat[1][0] = 0.31
    hm = ch.svg_heat(mat, names, names, cw=36, ch=22, vmin=0, vmax=0.6, mode='seq', fmt=lambda v: f'{v:.2f}'.replace('0.', '.').replace('1.00', '1'), left=40, top=16, fs=8.5, mark=[(1, 2, '#FFC531'), (2, 1, '#FFC531')])
    c = card('MECLİS UYUŞMASI', ag, n=3) + card('İSABET MATRİSİ · Candidate-5', m22, n=None) \
        + card('HATA KORELASYONU (φ)', f'<div style="display:flex;justify-content:center">{hm}</div><div class="mini mt6">Sarı çerçeve: Sector ↔ Neighbor φ = 0,46 → aynı hatayı yapıyorlar, ayrı kanıt sayılmaz.</div>', n=5) \
        + card('BENZERSİZ İSABET / IŞKA', kv('Yalnız Kotlin’in bulduğu', '125', mono=True) + kv('Yalnız Python’un bulduğu', '124', mono=True) + kv('İkisinin de kaçırdığı', '775', mono=True), n=6)
    return lab_screen(10, c, n_tabs=1, n_head=2)

# ───────────────────────── 32 Regime
@sheet('lab_regime', SEC, 'LAB · Regime',
       'Rejim analizi (kısa/orta/uzun, sektör kümelenmesi/dağılımı, geçiş entropisi, zero yoğunluğu) ve model drift izleme.',
       legend=[L1, L2,
               (3, 'Rejim şeridi', 'Veri akışının rejimlere ayrılışı (renkli bloklar). Rejimler ayrı analiz edilir; ortalama sonuç rejim farklarını gizleyebilir.', '§30'),
               (4, 'Rejim tablosu', 'Short · medium · long · sector clustering/dispersion · yüksek/düşük geçiş entropisi · zero-heavy/light · concentrated/dispersed: n ve Cand-5 farkı. n < 100 olan satırlar “Düşük sample”.', '§30 · §55'),
               (5, 'Model drift', 'Zaman dönemlerine göre isabet trendi. Düşüş varsa MODEL DRIFT işaretlenir; burada belirgin düşüş yok.', '§31'),
               (6, 'Rejim tutarlılığı', 'Robustness skoruna giren rejim tutarlılığı: kaç rejimde fark aynı yönde?', '§43')],
       refs='§30 · §31 · §43 · §55')
def s_lab_regime():
    strip = ch.svg_strip([(18, 'Short'), (26, 'Medium'), (22, 'Long'), (14, 'Short'), (20, 'Medium')], ['#5C6BC0', '#26A69A', '#FFA726', '#5C6BC0', '#26A69A'], w=340, h=30)
    rows = [['Short (≤ 50)', '312', '+0,6', ''], ['Medium (51–200)', '489', '−0,2', ''], ['Long (> 200)', '242', '+0,3', ''], ['Sector clustering', '131', '+1,2', ''], ['Sector dispersion', '176', '−0,8', ''],
            ['Yüksek geçiş entropisi', '402', '+0,1', ''], ['Düşük geçiş entropisi', '95', '+2,2', 'Düşük sample'], ['Zero-heavy', '64', '−1,6', 'Düşük sample'], ['Zero-light', '979', '+0,3', ''], ['Concentrated', '288', '+0,5', ''], ['Dispersed', '755', '+0,2', '']]
    rt = tbl(['Rejim', 'n', 'Δ c5', ''], [[a, f'<span class="mono">{n}</span>', delta(float(d.replace(',', '.').replace('−', '-'))), f'<span class="tag syn">{e}</span>' if e else ''] for a, n, d, e in rows], al='lrrl', dm=(6, 7), n=4, np='TL')
    r = rng(32); per = [13.9, 13.2, 14.1, 13.6, 13.4, 14.0, 13.1, 13.7, 13.5, 13.8]
    dr = ch.svg_line([dict(ys=per, color='#64B5F6'), dict(ys=[13.51] * 10, color='#FFC531', dash=True, width=1.2)], w=340, h=110, ymin=11, ymax=16, xlabels=[(0, 'dönem 1'), (4, '5'), (9, '10')], fmt=lambda v: f'{v:.0f}%')
    c = card('REJİM ŞERİDİ', strip + '<div class="mini mt4"><span style="color:#7986CB">■</span> short · <span style="color:#4DB6AC">■</span> medium · <span style="color:#FFB74D">■</span> long</div>', n=3) + card('REJİM TABLOSU · OOS', rt) \
        + card('MODEL DRIFT · dönemlere göre Cand-5 %', dr + '<div class="row sb mini mt4"><span>trend eğimi</span><b class="tx mono">−0,02 pp / dönem</b></div><div class="row sb mini"><span>MODEL DRIFT</span><b class="ok">yok</b></div>', n=5) \
        + card('REJİM TUTARLILIĞI', kv('Aynı yönde rejim', '8 / 11', mono=True) + kv('Anlamlı rejim', '0 / 11', mono=True), n=6)
    return lab_screen(11, c, n_tabs=1, n_head=2)
