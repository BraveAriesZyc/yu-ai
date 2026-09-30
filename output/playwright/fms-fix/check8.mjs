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
  const row = [...document.querySelectorAll('div')].find((d) =>
    d.className.includes('min-h-60px'),
  );
  const gapRow = row?.querySelector('.flex.items-center');
  const period = [...document.querySelectorAll('div')].find(
    (d) =>
      d.innerText?.match(/^\d{4} 年第 \d{2} 期$/) &&
      d.className.includes('absolute'),
  );
  return {
    gapRowClass: gapRow?.className,
    gapRowGap: gapRow ? getComputedStyle(gapRow).gap : null,
    periodClass: period?.className,
    periodRight: period ? getComputedStyle(period).right : null,
    periodTop: period ? getComputedStyle(period).top : null,
    periodRect: period ? period.getBoundingClientRect().toJSON() : null,
  };
});
console.log(JSON.stringify(info, null, 2));
await browser.close();
