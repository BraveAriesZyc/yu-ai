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
await page.goto(`${BASE}/fms/home`, { waitUntil: 'networkidle' });
await sleep(3500);

await page.locator('header .ant-select').first().click();
await sleep(1200);
const probe = await page.evaluate(() => {
  const dd = [...document.querySelectorAll('.ant-select-dropdown')].find(
    (d) => !d.classList.contains('ant-select-dropdown-hidden'),
  );
  const searchInput = document.querySelector(
    'header .ant-select-selection-search-input',
  );
  return {
    searchValue: searchInput ? searchInput.value : null,
    itemCount: dd ? dd.querySelectorAll('.ant-select-item-option').length : -1,
    ddClass: dd ? dd.className : null,
    ddText: dd ? dd.innerText.slice(0, 100) : null,
  };
});
console.log(JSON.stringify(probe, null, 2));
await page.screenshot({
  path: 'output/playwright/fms-fix/switcher-dropdown2.png',
  clip: { x: 550, y: 0, width: 700, height: 420 },
});
await browser.close();
