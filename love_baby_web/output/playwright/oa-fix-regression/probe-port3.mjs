import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
const ts = page.locator('.ant-select:has-text("请选择租户")');
if (await ts.count()) {
  await ts.click();
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
await page.keyboard.type('失焦测试', { delay: 60 });
// 失焦
await dlg
  .locator('label:has-text("字号")')
  .locator('xpath=..')
  .locator('input')
  .first()
  .click()
  .catch(() => {});
await page.keyboard.press('Tab');
await sleep(1500);
console.log(
  'h2 after blur:',
  JSON.stringify(
    await dlg
      .locator('article h2')
      .first()
      .textContent()
      .catch(() => 'NO H2'),
  ),
);
console.log(
  'article innerText:',
  JSON.stringify(
    (
      await dlg
        .locator('article')
        .first()
        .innerText()
        .catch(() => '')
    ).slice(0, 200),
  ),
);
await browser.close();
