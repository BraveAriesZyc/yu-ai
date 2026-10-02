import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const [base, tag, tenant] = process.argv.slice(2);
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 2560, height: 1080 } })
).newPage();
await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(2000);
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
await page
  .locator('button[aria-label="login"], button:has-text("登录")')
  .first()
  .click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 40_000,
});
await page.goto(`${base}/fms/voucher/list`, { waitUntil: 'domcontentloaded' });
await sleep(5000);
const r = await page.evaluate(() => {
  const rows = [
    ...document.querySelectorAll('.vxe-table--body-wrapper tbody tr'),
  ];
  const multi = rows
    .map((t) => t.getBoundingClientRect().height)
    .filter((h) => h > 45);
  return {
    rows: rows.length,
    expandedRows: multi.length,
    heights: rows
      .slice(0, 5)
      .map((t) => Math.round(t.getBoundingClientRect().height)),
  };
});
console.log(tag, JSON.stringify(r));
await page.screenshot({
  path: `output/playwright/fms-fix/list-fixed-${tag}.png`,
});
await browser.close();
