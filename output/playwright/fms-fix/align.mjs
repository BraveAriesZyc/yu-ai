import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 2560, height: 1080 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
const tenantSelect = page.locator('.ant-select:has-text("请选择租户")');
if (await tenantSelect.count()) {
  await tenantSelect.click();
  await sleep(1200);
  await page
    .locator('.ant-select-dropdown .ant-select-item-option')
    .first()
    .click();
  await sleep(500);
}
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/list`, { waitUntil: 'domcontentloaded' });
await sleep(4500);

const data = await page.evaluate(() => {
  // 找记-2 所在行（多分录凭证）
  const rows = [
    ...document.querySelectorAll('.vxe-table--body-wrapper tbody tr'),
  ];
  const row = rows.find((t) => t.innerText.includes('记-2'));
  if (!row) return null;
  const cells = [...row.querySelectorAll('td')];
  return cells
    .map((td, i) => {
      const title = document
        .querySelectorAll('.vxe-table--header-wrapper th')
        [i]?.innerText?.trim();
      const lines = [
        ...td.querySelectorAll('.vxe-cell--wrapper > div > div'),
      ].map((d) => {
        const r = d.getBoundingClientRect();
        return {
          y: Math.round(r.y),
          h: Math.round(r.height),
          text: d.innerText.slice(0, 20),
        };
      });
      const cellRect = td.getBoundingClientRect();
      return { col: title, cellH: Math.round(cellRect.height), lines };
    })
    .filter((c) => c.lines.length > 0);
});
console.log(JSON.stringify(data, null, 1));
await browser.close();
