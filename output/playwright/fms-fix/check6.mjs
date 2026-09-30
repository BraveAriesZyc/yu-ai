import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/create`, { waitUntil: 'networkidle' });
await sleep(4000);
const info = await page.evaluate(() => {
  const spans = [...document.querySelectorAll('span')].filter(
    (s) => s.innerText.trim() === '凭证字',
  );
  return spans.map((s) => {
    const r = s.getBoundingClientRect();
    const chain = [];
    let el = s;
    for (let i = 0; i < 4 && el; i++) {
      chain.push(`${el.tagName}.${String(el.className).slice(0, 60)}`);
      el = el.parentElement;
    }
    return {
      x: Math.round(r.x),
      y: Math.round(r.y),
      w: Math.round(r.width),
      visible: r.width > 0,
      chain,
    };
  });
});
console.log(JSON.stringify(info, null, 2));
await browser.close();
