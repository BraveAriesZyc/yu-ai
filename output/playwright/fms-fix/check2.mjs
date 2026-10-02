import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300));
});
page.on('pageerror', (e) =>
  consoleErrors.push(`pageerror: ${e.message.slice(0, 300)}`),
);
const apiErrors = [];
page.on('response', async (res) => {
  if (res.url().includes('/admin-api/')) {
    try {
      const body = await res.json();
      if (body && body.code !== 0)
        apiErrors.push(
          `${res.url().split('/admin-api/')[1]} -> ${body.code} ${String(body.msg).slice(0, 150)}`,
        );
    } catch {}
  }
});

await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});

await page.goto(`${BASE}/fms/voucher/create`, { waitUntil: 'networkidle' });
await sleep(4000);

// 页面正文文本（前 600 字）+ 页面上的错误提示
const bodyText = await page.evaluate(() =>
  document.body.innerText.slice(0, 600),
);
const errorEls = await page.evaluate(() =>
  [
    ...document.querySelectorAll(
      '.ant-message, .ant-alert-error, .ant-form-item-explain-error',
    ),
  ].map((e) => e.innerText.slice(0, 200)),
);
await page.screenshot({
  path: 'output/playwright/fms-fix/voucher-create-2.png',
  fullPage: false,
});

console.log(
  JSON.stringify(
    {
      consoleErrors: consoleErrors.slice(0, 10),
      apiErrors: apiErrors.slice(0, 10),
      errorEls,
      bodyText,
    },
    null,
    2,
  ),
);
await browser.close();
