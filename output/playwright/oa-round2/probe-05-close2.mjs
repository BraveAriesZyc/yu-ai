import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function login(page, base) {
  await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(1500);
  const t = page.locator('.ant-select:has-text("请选择租户")');
  if (await t.count()) {
    await t.click();
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
}
const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await login(page, base);
await page.goto(`${base}/oa/seal/apply`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
await page
  .locator(
    '.vxe-table--body-wrapper.body--wrapper tbody tr td span.cursor-pointer:visible',
  )
  .first()
  .click();
await sleep(2500);
const dlg = page.locator('[role="dialog"]:visible').last();
// 依次尝试候选关闭按钮
for (const sel of ['button.flex-center', 'button.inline-flex']) {
  const btn = dlg.locator(sel).last();
  console.log(sel, 'count:', await btn.count());
  if (await btn.count()) {
    await btn
      .click()
      .catch((error) => console.log('err', String(error).slice(0, 80)));
    await sleep(800);
    const left = await page.locator('[role="dialog"]:visible').count();
    console.log(sel, 'clicked, dialogs left:', left);
    if (left === 0) break;
  }
}
await browser.close();
