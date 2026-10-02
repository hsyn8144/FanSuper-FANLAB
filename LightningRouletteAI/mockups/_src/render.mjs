// HTML sayfalarını PNG (DPR 2) + tek PDF'e dönüştürür. Chromium: @sparticuz/chromium (npm; indirme gerektirmez).
//   node render.mjs [png|pdf|all] [süzgeç,süzgeç]
// Ortam: LRA_NODE_MODULES (varsayılan /home/user/render-tools/node_modules)
process.env.AWS_EXECUTION_ENV = 'AWS_Lambda_nodejs22.x';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const here = path.dirname(fileURLToPath(import.meta.url));
const NM = process.env.LRA_NODE_MODULES || '/home/user/render-tools/node_modules';
const require = createRequire(path.join(NM, 'x.js'));
const chromium = (await import(pathToFileURL(require.resolve('@sparticuz/chromium')).href)).default;
const puppeteer = (await import(pathToFileURL(require.resolve('puppeteer-core')).href)).default;

const mode = process.argv[2] || 'all';
const filters = (process.argv[3] || '').split(',').filter(Boolean);
const buildDir = path.join(here, '_build');
const root = path.resolve(here, '..');
const pngDir = path.join(root, 'png');
fs.mkdirSync(pngDir, { recursive: true });

const manifest = JSON.parse(fs.readFileSync(path.join(buildDir, 'manifest.json'), 'utf8'));
let files = manifest.map(m => m.file);
if (filters.length) files = files.filter(f => filters.some(x => f.includes(x)));

const browser = await puppeteer.launch({ args: chromium.args, executablePath: await chromium.executablePath(), headless: 'shell' });
const heights = fs.existsSync(path.join(buildDir, 'heights.json')) ? JSON.parse(fs.readFileSync(path.join(buildDir, 'heights.json'), 'utf8')) : {};
let problems = 0;

async function openPage(f) {
  const page = await browser.newPage();
  await page.setViewport({ width: 480, height: 1000, deviceScaleFactor: 2 });
  await page.goto(pathToFileURL(path.join(buildDir, f + '.html')).href, { waitUntil: 'load' });
  await page.waitForFunction('window.__ready === true', { timeout: 20000 });
  return page;
}

if (mode === 'png' || mode === 'all') {
  for (const f of files) {
    const page = await openPage(f);
    const box = await page.evaluate(() => { const r = document.getElementById('sheet').getBoundingClientRect(); return { w: Math.ceil(r.width), h: Math.ceil(r.height) }; });
    const audit = await page.evaluate(() => window.__audit);
    await page.screenshot({ path: path.join(pngDir, f + '.png'), clip: { x: 0, y: 0, width: box.w, height: box.h }, captureBeyondViewport: true });
    heights[f] = box.h;
    fs.writeFileSync(path.join(buildDir, f + '.frag.html'), await page.evaluate(() => document.getElementById('sheet').outerHTML));
    const issues = [...(audit.missingLegend || []).map(x => 'AÇIKLAMASIZ rozet ' + x), ...(audit.extraLegend || []).map(x => 'ROZETSİZ açıklama ' + x), ...(audit.overflow || [])];
    problems += issues.length;
    console.log(`${f}  ${box.w}x${box.h}  rozet:${(audit.badges || []).length}` + (issues.length ? `  ⚠ ${issues.length}` : ''));
    issues.slice(0, 8).forEach(x => console.log('     - ' + x));
    await page.close();
  }
  fs.writeFileSync(path.join(buildDir, 'heights.json'), JSON.stringify(heights));
}

if (mode === 'pdf' || mode === 'all') {
  // tek PDF: her sayfa kendi yüksekliğinde (480 px genişlik)
  const all = manifest.map(m => m.file);
  const parts = [];
  let css = '';
  for (const f of all) {
    const h = heights[f];
    if (!h) { console.log('PDF için yükseklik yok:', f); continue; }
    const fragPath = path.join(buildDir, f + '.frag.html');
    if (!fs.existsSync(fragPath)) { console.log('PDF için parça yok:', f); continue; }
    const body = fs.readFileSync(fragPath, 'utf8').replace('id="sheet"', '');
    css += `@page p${parts.length}{size:480px ${h}px;margin:0}.pg${parts.length}{page:p${parts.length};break-after:page;width:480px;height:${h}px;overflow:hidden}\n`;
    parts.push(`<div class="pg${parts.length}">${body}</div>`);
  }
  const first = fs.readFileSync(path.join(buildDir, all[0] + '.html'), 'utf8');
  const head = first.match(/<head>[\s\S]*?<\/head>/)[0].replace('</head>', `<style>${css}</style></head>`);
  const doc = `<!doctype html><html lang="tr">${head}<body>${parts.join('\n')}</body></html>`;
  const tmp = path.join(buildDir, '_all.html');
  fs.writeFileSync(tmp, doc);
  const page = await browser.newPage();
  await page.goto(pathToFileURL(tmp).href, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.pdf({ path: path.join(root, 'LightningRouletteAI_Tasarim_Katalogu.pdf'), printBackground: true, preferCSSPageSize: true });
  await page.close();
  console.log('PDF yazıldı');
}

await browser.close();
console.log(problems ? `UYARI: ${problems} sorun` : 'sorun yok');
