import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://localhost:5999';
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
await page.goto(`${BASE}/fms/voucher/list`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
await page.screenshot({ path: 'output/playwright/fms-fix/list-view.png' });
const info = await page.evaluate(() => {
  const trs = [...document.querySelectorAll('tr')].filter((t) =>
    t.innerText.includes('功能验收测试凭证'),
  );
  return trs.map((t) => ({
    parent:
      t.closest(
        '.vxe-table--body-wrapper, .vxe-table--fixed-right-wrapper, .vxe-table--fixed-left-wrapper',
      )?.className ?? 'other',
    lastCells: [...t.querySelectorAll('td')]
      .slice(-2)
      .map((c) => c.innerText.slice(0, 40)),
  }));
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
