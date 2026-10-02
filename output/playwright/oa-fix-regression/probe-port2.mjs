import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();

await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
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

// ===== 公文发文：键盘输入标题 + accept 属性 =====
await page.goto(`${base}/oa/officialdoc/send`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
await page
  .locator('button', { hasText: /新\s*增/ })
  .first()
  .click();
const dlg = page.locator('[role="dialog"]:visible').last();
await dlg.waitFor({ timeout: 10_000 });
await sleep(1500);
const titleInput = dlg
  .locator('label:has-text("标题")')
  .locator('xpath=..')
  .locator('input')
  .first();
await titleInput.click();
await page.keyboard.type('联动XYZ', { delay: 80 });
await sleep(1200);
console.log('input value:', await titleInput.inputValue());
console.log(
  'h2 after typing:',
  JSON.stringify(
    await dlg
      .locator('article h2')
      .first()
      .textContent()
      .catch(() => 'NO H2'),
  ),
);
// 正式公文上传 input 的 accept
console.log(
  'upload inputs accept:',
  await page.evaluate(() => {
    const dlgEl = [...document.querySelectorAll('[role="dialog"]')].pop();
    return [...dlgEl.querySelectorAll('input[type="file"]')].map(
      (i) => i.accept || '(none)',
    );
  }),
);
// FileUpload 相关提示（title 属性等）
console.log(
  'pdf anywhere in dialog html:',
  (await dlg.innerHTML()).includes('.pdf'),
);
await page.screenshot({
  path: 'output/playwright/oa-fix-regression/probe-port2-officialdoc.png',
});
await page.keyboard.press('Escape');
await sleep(600);

// ===== supply 固定右列按钮 =====
await page.goto(`${base}/oa/supply/item`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
console.log(
  'page 入库 buttons:',
  await page.locator('button', { hasText: /入\s*库/ }).count(),
);
console.log(
  'fixed wrappers:',
  await page
    .locator('.vxe-table--fixed-right-wrapper, .vxe-table--fixed-left-wrapper')
    .count(),
);
await page
  .locator('button', { hasText: /入\s*库/ })
  .first()
  .click();
await sleep(1500);
const dlg2 = page.locator('[role="dialog"]:visible').last();
console.log(
  'instock labels:',
  JSON.stringify(await dlg2.locator('label').allTextContents()),
);
await page.screenshot({
  path: 'output/playwright/oa-fix-regression/antd-port-11-supply-instock.png',
});
await browser.close();
console.log('done');
