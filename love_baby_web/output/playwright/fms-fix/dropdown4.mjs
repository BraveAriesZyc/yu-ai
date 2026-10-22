import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/create`, { waitUntil: 'networkidle' });
await sleep(4000);

// 凭证字下拉
await page.locator(String.raw`.ant-select.\!w-\[90px\]`).click();
await sleep(1000);
const r1 = await page.evaluate(() => {
  const dd = [...document.querySelectorAll('.ant-select-dropdown')].find(
    (d) => !d.classList.contains('ant-select-dropdown-hidden'),
  );
  return dd
    ? {
        count: dd.querySelectorAll('.ant-select-item-option').length,
        text: dd.innerText.slice(0, 60),
      }
    : null;
});
await page.keyboard.press('Escape');
await sleep(500);

// 顶栏切换器：不触发 visible-change 的强制刷新后再看？直接点开
await page.locator('header .ant-select').first().click();
await sleep(1500);
const r2 = await page.evaluate(() => {
  const dd = [...document.querySelectorAll('.ant-select-dropdown')].find(
    (d) => !d.classList.contains('ant-select-dropdown-hidden'),
  );
  return dd
    ? {
        count: dd.querySelectorAll('.ant-select-item-option').length,
        text: dd.innerText.slice(0, 60),
      }
    : null;
});
console.log(JSON.stringify({ voucherWord: r1, accountSet: r2 }, null, 2));
await browser.close();
