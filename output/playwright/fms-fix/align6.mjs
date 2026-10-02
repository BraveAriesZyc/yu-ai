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
const r = await page.evaluate(() => {
  const row = () =>
    [...document.querySelectorAll('.vxe-table--body .vxe-body--row')].find(
      (r) => r.innerText.includes('记-2'),
    );
  const measure = () => {
    const tr = row();
    const td =
      [...tr.querySelectorAll('td')].find((c) =>
        c.innerText.includes('咨询服务'),
      ) || tr.querySelectorAll('td')[3];
    return {
      tr: tr.getBoundingClientRect().height,
      cell: td.querySelector('.vxe-cell').getBoundingClientRect().height,
    };
  };
  const before = measure();
  const style = document.createElement('style');
  style.textContent =
    '.vxe-table--body .vxe-cell { height: auto !important; max-height: none !important; overflow: visible !important; }';
  document.head.append(style);
  const mid = measure();
  style.textContent +=
    '.vxe-table--body .vxe-cell--wrapper { overflow: visible !important; }';
  const after = measure();
  return { before, mid, after };
});
console.log(JSON.stringify(r));
await page.screenshot({ path: 'output/playwright/fms-fix/align-after.png' });
await browser.close();
