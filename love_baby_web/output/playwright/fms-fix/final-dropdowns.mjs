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

async function countOptions() {
  return await page.evaluate(() => {
    const dd = [...document.querySelectorAll('.ant-select-dropdown')].find(
      (d) =>
        !d.classList.contains('ant-select-dropdown-hidden') &&
        d.offsetWidth > 0,
    );
    return dd
      ? {
          count: dd.querySelectorAll('.ant-select-item-option').length,
          text: dd.innerText.slice(0, 80).replaceAll('\n', '|'),
        }
      : null;
  });
}

// 1. 顶栏账套切换器
await page.goto(`${BASE}/fms/home`, { waitUntil: 'domcontentloaded' });
await sleep(3500);
await page.locator('header .ant-select').first().click();
await sleep(1200);
const switcher = await countOptions();
await page.keyboard.press('Escape');
await sleep(500);

// 2. 凭证字 + 科目下拉
await page.goto(`${BASE}/fms/voucher/create`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
await page.locator(String.raw`.ant-select.\!w-\[90px\]`).click();
await sleep(1000);
const word = await countOptions();
await page.keyboard.press('Escape');
await sleep(500);
// 科目下拉（第一行分录）
await page
  .locator('.subject-cell .ant-select, tbody tr td .ant-select.w-full')
  .first()
  .click()
  .catch(() => null);
const subjectSels = await page.locator('.ant-select.w-full').count();
if (subjectSels > 0) {
  await page.locator('.ant-select.w-full').first().click();
  await sleep(1000);
}
const subject = await countOptions();
await page.screenshot({
  path: 'output/playwright/fms-fix/subject-dropdown.png',
});

console.log(JSON.stringify({ switcher, word, subject }, null, 2));
await browser.close();
