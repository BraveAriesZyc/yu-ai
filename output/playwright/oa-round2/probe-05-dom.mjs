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
  .nth(2)
  .click();
await sleep(3000);
const dlg = page.locator('[role="dialog"]:visible').last();
console.log(
  'descriptions-item count:',
  await dlg.locator('.ant-descriptions-item').count(),
);
const sample = await dlg
  .locator('.ant-descriptions-item')
  .first()
  .evaluate((el) => el.outerHTML.slice(0, 500))
  .catch(() => 'none');
console.log('sample item html:', sample);
// 直接取 label/content 对
const pairs = await dlg.locator('.ant-descriptions-item').evaluateAll((els) =>
  els.map((el) => ({
    label: el
      .querySelector('.ant-descriptions-item-label')
      ?.textContent?.trim(),
    content: el
      .querySelector('.ant-descriptions-item-content')
      ?.textContent?.trim()
      .slice(0, 30),
  })),
);
console.log(JSON.stringify(pairs, null, 1));
await browser.close();
