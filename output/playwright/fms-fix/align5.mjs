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
  const td =
    [...tr.querySelectorAll('td')].find((c) =>
      c.innerText.includes('咨询服务'),
    ) || tr.querySelectorAll('td')[3];
  const cell = td.querySelector('.vxe-cell');
  const wrap = td.querySelector('.vxe-cell--wrapper');
  const inner = wrap.querySelector('div');
  const entryDivs = [...inner.querySelectorAll('div')];
  const info = (el, name) =>
    el
      ? {
          name,
          h: getComputedStyle(el).height,
          maxH: getComputedStyle(el).maxHeight,
          overflow: getComputedStyle(el).overflow,
          position: getComputedStyle(el).position,
          display: getComputedStyle(el).display,
        }
      : { name, missing: true };
  return [
    info(tr, 'tr'),
    info(td, 'td'),
    info(cell, 'vxe-cell'),
    info(wrap, 'wrapper'),
    info(inner, 'inner'),
    ...entryDivs.map((d, i) => info(d, `entry${i}`)),
  ];
});
console.log(JSON.stringify(r, null, 1));
await browser.close();
