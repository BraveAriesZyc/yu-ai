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

let cleaned = 0;
for (let round = 0; round < 5; round++) {
  const idx = await page.evaluate(() => {
    const rows = [
      ...document.querySelectorAll('.vxe-table--body-wrapper tbody tr'),
    ];
    return rows.findIndex((t) => t.innerText.includes('功能验收测试凭证'));
  });
  if (idx < 0) break;
  const actionRow = page
    .locator(
      '.vxe-table--fixed-right-wrapper tbody tr, .vxe-table--fixed--right-wrapper tbody tr',
    )
    .nth(idx);
  await actionRow.locator('text=删除').first().click();
  await sleep(1000);
  await page
    .locator('button:has-text("确 认"), button:has-text("确认")')
    .last()
    .click();
  await sleep(2000);
  cleaned++;
  await page.reload({ waitUntil: 'domcontentloaded' });
  await sleep(3500);
}
const left = await page.evaluate(() =>
  document.body.innerText.includes('功能验收测试凭证'),
);
console.log(JSON.stringify({ cleaned, stillLeft: left }));
await browser.close();
