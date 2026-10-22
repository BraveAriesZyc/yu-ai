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

// 抓 next-number 请求的实际参数
const reqs = [];
page.on('request', (r) => {
  if (r.url().includes('next-number') || r.url().includes('voucher-word'))
    reqs.push(r.url());
});

// 凭证字 Select：当前显示文本
const selectText = await page.evaluate(() => {
  const el = document.querySelector('.ant-select-selection-item');
  return el ? { text: el.innerText, title: el.getAttribute('title') } : null;
});
// 打开下拉
await page.locator('.ant-select').first().click();
await sleep(800);
const options = await page.evaluate(() =>
  [...document.querySelectorAll('.ant-select-item-option')].map((o) =>
    o.innerText.trim(),
  ),
);
// next-number 触发：改凭证号日期不需要；直接改凭证字选择第二项（若有）
console.log(JSON.stringify({ selectText, options, reqs }, null, 2));
await page.screenshot({
  path: 'output/playwright/fms-fix/voucher-word-dropdown.png',
});
await browser.close();
