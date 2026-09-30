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
  const tr = [
    ...document.querySelectorAll('.vxe-table--body .vxe-body--row'),
  ].find((r) => r.innerText.includes('记-2'));
  const td = tr?.querySelector('.vxe-body--column');
  const cell = td?.querySelector('.vxe-cell');
  // 找作用在 td 上的所有 height 规则
  const rules = [];
  for (const sheet of document.styleSheets) {
    try {
      for (const rule of sheet.cssRules) {
        if (
          rule.cssText.includes('height') &&
          td &&
          rule.selectorText &&
          (td.matches(rule.selectorText) ||
            tr.matches(rule.selectorText) ||
            (cell && cell.matches(rule.selectorText)))
        ) {
          rules.push(rule.cssText.slice(0, 300));
        }
      }
    } catch {}
  }
  return {
    trH: tr ? getComputedStyle(tr).height : null,
    trInline: tr?.style.cssText,
    tdH: td ? getComputedStyle(td).height : null,
    cellH: cell ? getComputedStyle(cell).height : null,
    cellMaxH: cell ? getComputedStyle(cell).maxHeight : null,
    matchingRules: rules.slice(0, 8),
  };
});
console.log(JSON.stringify(r, null, 1));
await browser.close();
