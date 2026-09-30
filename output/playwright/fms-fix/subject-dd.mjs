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
await page.goto(`${BASE}/fms/voucher/create`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
// 分录区的科目下拉：排除顶栏（header 外、class 含 w-full 且在表格区域内）
const target = page.locator('.voucher-sheet .ant-select').nth(1); // nth(0) 是凭证字（!w-[90px]），其后是科目
await target.click();
await sleep(1200);
const r = await page.evaluate(() => {
  const dds = [...document.querySelectorAll('.ant-select-dropdown')].filter(
    (d) =>
      !d.classList.contains('ant-select-dropdown-hidden') && d.offsetWidth > 0,
  );
  return dds.map((dd) => ({
    count: dd.querySelectorAll('.ant-select-item-option').length,
    text: dd.innerText.slice(0, 100).replaceAll('\n', '|'),
  }));
});
console.log(JSON.stringify(r, null, 2));
await page.screenshot({
  path: 'output/playwright/fms-fix/subject-dropdown.png',
});
await browser.close();
