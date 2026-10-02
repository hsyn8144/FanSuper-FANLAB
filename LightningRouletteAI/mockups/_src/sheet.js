// Sayfa betiği: (1) kaydırmalı sekmeleri aktif sekmeye ortala, (2) numaralı işaretleri yerleştir,
// (3) taşma denetimi yap. Sonuç window.__audit içinde render.mjs tarafından okunur.
//
// İşaret konumları (data-np):  [x oranı, y oranı, dx, dy]  →  merkez = sol + x*genişlik + dx , üst + y*yükseklik + dy
//   tl tr bl br : köşeler (öğenin köşesine oturur)      l r t b c : kenar ortaları / merkez
//   L R T B     : öğenin DIŞINDA (metni örtmez)          TL TR BL BR : köşenin biraz dışında
const ANCH = {
  tl: [0, 0, 0, 0], tr: [1, 0, 0, 0], bl: [0, 1, 0, 0], br: [1, 1, 0, 0],
  l: [0, .5, 0, 0], r: [1, .5, 0, 0], t: [.5, 0, 0, 0], b: [.5, 1, 0, 0], c: [.5, .5, 0, 0],
  L: [0, .5, -13, 0], R: [1, .5, 13, 0], T: [.5, 0, 0, -13], B: [.5, 1, 0, 13],
  TL: [0, 0, -6, -6], TR: [1, 0, 6, -6], BL: [0, 1, -6, 6], BR: [1, 1, 6, 6],
};
(async () => {
  try { await document.fonts.ready; } catch (e) {}
  document.querySelectorAll('.hs').forEach(c => {
    // kaydırmalı sekme satırı: aktif sekmeyi ortala (DOM'a yazılır → PDF'te de korunur)
    const on = c.querySelector('.on'); const first = c.firstElementChild;
    if (!on || !first) return;
    const max = c.scrollWidth - c.clientWidth;
    const off = Math.max(0, Math.min(max, on.offsetLeft - (c.clientWidth - on.offsetWidth) / 2));
    first.style.marginLeft = (-off) + 'px';
  });
  const stage = document.querySelector('.stage');
  const audit = { badges: [], overflow: [], missingLegend: [], extraLegend: [], dupBadge: [] };
  if (stage) {
    const sr = stage.getBoundingClientRect();
    const targets = stage.querySelectorAll('[data-n]');
    const seen = new Set();
    const placed = [];
    targets.forEach(el => {
      const n = el.getAttribute('data-n');
      const pos = el.getAttribute('data-np') || 'tl';
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) { audit.overflow.push('rozet hedefi görünmüyor: ' + n); return; }
      if (seen.has(n)) audit.dupBadge.push(n);
      const a = ANCH[pos] || ANCH.tl;
      const x = r.left - sr.left + a[0] * r.width + a[2];
      const y = r.top - sr.top + a[1] * r.height + a[3];
      const b = document.createElement('div');
      b.className = 'bd'; b.textContent = n;
      b.style.left = (x - 10.5) + 'px'; b.style.top = (y - 10.5) + 'px';
      stage.appendChild(b);
      placed.push({ n, x, y });
      seen.add(n);
    });
    // iki rozet üst üste biniyor mu?
    for (let i = 0; i < placed.length; i++) for (let j = i + 1; j < placed.length; j++) {
      if (Math.hypot(placed[i].x - placed[j].x, placed[i].y - placed[j].y) < 16) audit.overflow.push('rozetler çakışıyor: ' + placed[i].n + ' ve ' + placed[j].n);
    }
    const legend = new Set([...document.querySelectorAll('.legend .li .n:not(.o)')].map(e => e.textContent.trim()));
    seen.forEach(n => { if (!legend.has(n)) audit.missingLegend.push(n); });
    legend.forEach(n => { if (!seen.has(n)) audit.extraLegend.push(n); });
    audit.badges = [...seen];

    // taşma denetimi
    const scr = stage.querySelector('.scr');
    if (scr) {
      const cr = scr.getBoundingClientRect();
      stage.querySelectorAll('.scr *').forEach(el => {
        if (el.closest('.hs') || el.closest('[data-ovf]') || el.closest('svg')) return;
        const r = el.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) return;
        const t = (el.textContent || '').trim().slice(0, 40);
        if (r.right > cr.right + 1.5 || r.left < cr.left - 1.5) {
          audit.overflow.push('dışarı taşıyor: <' + el.tagName.toLowerCase() + '.' + (el.className || '') + '> "' + t + '" (' + Math.round(r.left - cr.left) + '..' + Math.round(r.right - cr.left) + ' / ' + Math.round(cr.width) + ')');
        } else if (el.children.length === 0 && el.scrollWidth > el.clientWidth + 1 && getComputedStyle(el).overflowX === 'visible' && el.clientWidth > 0 && getComputedStyle(el).display !== 'inline') {
          audit.overflow.push('içerik sığmıyor: <' + el.tagName.toLowerCase() + '.' + (el.className || '') + '> "' + t + '" (' + el.scrollWidth + ' > ' + el.clientWidth + ')');
        }
      });
    }
  }
  window.__audit = audit;
  window.__ready = true;
})();
