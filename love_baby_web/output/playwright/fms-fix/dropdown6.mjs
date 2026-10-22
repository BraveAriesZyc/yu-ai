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
await page.goto(`${BASE}/fms/home`, { waitUntil: 'domcontentloaded' });
await sleep(3500);
await page.locator('header .ant-select').first().click();
await sleep(1500);
const r = await page.evaluate(() => {
  return [...document.querySelectorAll('.ant-select-dropdown')].map((d) => {
    const style = getComputedStyle(d);
    return {
      hidden: d.classList.contains('ant-select-dropdown-hidden'),
      display: style.display,
      visible: !!(d.offsetWidth || d.offsetHeight),
      items: d.querySelectorAll('.ant-select-item-option').length,
      text: d.innerText.slice(0, 60).replaceAll('\n', '|'),
    };
  });
});
console.log(JSON.stringify(r, null, 2));
await page.screenshot({
  path: 'output/playwright/fms-fix/switcher-dropdown6.png',
  clip: { x: 550, y: 0, width: 700, height: 500 },
});
await browser.close();
