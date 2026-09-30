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
await page.screenshot({
  path: 'output/playwright/fms-fix/voucher-create-5.png',
  clip: { x: 240, y: 195, width: 800, height: 100 },
});
// 检测重叠：凭证字 label 与 select 的边界
const overlap = await page.evaluate(() => {
  const spans = [...document.querySelectorAll('span')].filter(
    (s) => s.innerText === '凭证字',
  );
  const sel = document.querySelector(String.raw`.ant-select.\!w-\[90px\]`);
  if (spans.length === 0 || !sel)
    return { found: false, spanCount: spans.length };
  const sr = spans[0].getBoundingClientRect();
  const tr = sel.getBoundingClientRect();
  return {
    found: true,
    spanCount: spans.length,
    spanRight: sr.right,
    selectLeft: tr.left,
    overlapPx: Math.round(sr.right - tr.left),
  };
});
console.log(JSON.stringify(overlap));
await browser.close();
