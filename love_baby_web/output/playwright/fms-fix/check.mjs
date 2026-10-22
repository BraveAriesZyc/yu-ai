import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
let apiError = '';
page.on('response', async (res) => {
  if (res.url().includes('/admin-api/') && res.status() !== 200) {
    try {
      apiError += `${res.status()} ${res.url()} ${(await res.text()).slice(0, 200)}\n`;
    } catch {}
  }
});

await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});

// 1. 凭证填制页：不应再出现 voucherTime 参数错误
await page.goto(`${BASE}/fms/voucher/create`, { waitUntil: 'networkidle' });
await sleep(3000);
const pageText = await page.evaluate(() => document.body.innerText);
const hasParamError =
  pageText.includes('请求参数类型错误') ||
  pageText.includes('Failed to convert');
await page.screenshot({ path: 'output/playwright/fms-fix/voucher-create.png' });

// 2. 顶栏账套切换器文本
const switcherText = await page.evaluate(() => {
  const el = document.querySelector(
    '.vben-header .ant-select, header .ant-select',
  );
  return el
    ? el.innerText.replaceAll('\n', ' | ')
    : '(no select found in header)';
});
const switcherHtmlSnippet = await page.evaluate(() => {
  const el = document.querySelector('header .ant-select-selection-item');
  return el ? el.innerText : '(no selection item)';
});

console.log(
  JSON.stringify(
    {
      hasParamError,
      switcherText,
      switcherHtmlSnippet,
      apiError: apiError.slice(0, 500),
      pageErrors: errors.slice(0, 5),
    },
    null,
    2,
  ),
);
await page.screenshot({
  path: 'output/playwright/fms-fix/header.png',
  clip: { x: 0, y: 0, width: 1600, height: 120 },
});
await browser.close();
