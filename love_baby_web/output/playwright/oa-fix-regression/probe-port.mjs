import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) =>
  console.log('PAGEERROR:', (e.stack || e.message).slice(0, 300)),
);

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

// ===== 公文发文：标题联动与 pdf 提示 =====
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
console.log('== officialdoc ==');
console.log(
  'labels:',
  JSON.stringify(await dlg.locator('label').allTextContents()),
);
// 标题字段 input 个数与位置
const titleInput = dlg
  .locator('label:has-text("标题")')
  .locator('xpath=..')
  .locator('input');
console.log('title inputs:', await titleInput.count());
await titleInput.first().fill('联动测试标题ABC');
await sleep(1000);
console.log(
  'preview article text head:',
  (
    await dlg
      .locator('article')
      .first()
      .innerText()
      .catch(() => 'NO ARTICLE')
  )
    .slice(0, 120)
    .replaceAll('\n', ' / '),
);
console.log(
  'preview h2:',
  await dlg
    .locator('article h2')
    .first()
    .textContent()
    .catch(() => 'NO H2'),
);
// pdf 帮助提示
console.log(
  'pdf in dialog text:',
  (await dlg.evaluate((el) => el.innerText)).includes('pdf'),
);
console.log(
  'help/hint nodes:',
  await page.evaluate(() => {
    const dlgEl = [...document.querySelectorAll('[role="dialog"]')].pop();
    const hits = [];
    dlgEl.querySelectorAll('*').forEach((el) => {
      const t = el.textContent || '';
      if (t.includes('.pdf') && t.length < 100)
        hits.push(
          `${el.tagName}.${String(el.className).slice(0, 40)}: ${t.trim().slice(0, 60)}`,
        );
    });
    return hits.slice(0, 8);
  }),
);
await page.screenshot({
  path: 'output/playwright/oa-fix-regression/probe-port-01-officialdoc.png',
});
await page.keyboard.press('Escape');
await sleep(600);

// ===== 办公用品入库按钮 =====
await page.goto(`${base}/oa/supply/item`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
console.log('== supply ==');
const firstRow = page.locator('.vxe-table--body-wrapper tbody tr').first();
console.log(
  'row buttons:',
  JSON.stringify(await firstRow.locator('button').allTextContents()),
);
console.log('row html tail:', (await firstRow.innerHTML()).slice(-600));
