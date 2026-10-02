import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const apiErrors = [];
page.on('response', async (res) => {
  if (res.url().includes('account-set')) {
    try {
      const b = await res.json();
      apiErrors.push(
        `${res.url().split('admin-api/')[1]} -> code=${b.code} count=${Array.isArray(b.data) ? b.data.length : '-'}`,
      );
    } catch {}
  }
});
page.on('pageerror', (e) =>
  apiErrors.push(`pageerror: ${e.message.slice(0, 200)}`),
);
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/home`, { waitUntil: 'networkidle' });
await sleep(3500);

// 点击顶栏账套切换器
await page.locator('header .ant-select').first().click();
await sleep(1500);
const dropdown = await page.evaluate(() => {
  const dd = [...document.querySelectorAll('.ant-select-dropdown')].find(
    (d) => !d.classList.contains('ant-select-dropdown-hidden'),
  );
  if (!dd) return { visible: false };
  return {
    visible: true,
    itemCount: dd.querySelectorAll('.ant-select-item-option').length,
    items: [...dd.querySelectorAll('.ant-select-item-option')].map((o) =>
      o.innerText.trim().slice(0, 50),
    ),
    emptyText: dd.querySelector('.ant-empty')?.innerText ?? null,
    html: dd.innerHTML.length,
  };
});
console.log(JSON.stringify({ dropdown, apiErrors }, null, 2));
await page.screenshot({
  path: 'output/playwright/fms-fix/switcher-dropdown.png',
  clip: { x: 550, y: 0, width: 600, height: 400 },
});
await browser.close();
