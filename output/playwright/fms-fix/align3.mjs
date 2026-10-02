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
  const grid = document.querySelector('.fms-voucher-list-grid');
  const td = document.querySelector('.vxe-table--body .vxe-body--column');
  // 检查我的样式规则是否注入
  const styles = [...document.querySelectorAll('style')].map(
    (s) => s.textContent || '',
  );
  const injected = styles.some((s) => s.includes('fms-voucher-list-grid'));
  return {
    gridFound: !!grid,
    tdInlineH: td?.style.height ?? '(none)',
    tdComputedH: td ? getComputedStyle(td).height : null,
    ruleInjected: injected,
  };
});
console.log(JSON.stringify(r, null, 1));
await browser.close();
