import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const warns = [];
page.on('console', (m) => {
  const t = m.text();
  if (
    !t.includes('route component') &&
    !t.includes('menu name duplicate') &&
    !t.includes('Download the Vue DevTools') &&
    !t.includes('Intlify') &&
    !t.includes('devtools')
  )
    warns.push(`${m.type()}: ${t.slice(0, 250)}`);
});
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/home`, { waitUntil: 'networkidle' });
await sleep(3500);
await page.locator('header .ant-select').first().click();
await sleep(1200);
console.log(JSON.stringify({ warns: warns.slice(0, 15) }, null, 2));
await browser.close();
