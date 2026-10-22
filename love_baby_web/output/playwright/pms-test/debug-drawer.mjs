import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await page.goto('http://localhost:5999/auth/login', {
  waitUntil: 'domcontentloaded',
});
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto('http://localhost:5999/pms/pm/workbench', {
  waitUntil: 'domcontentloaded',
});
await sleep(3500);
await page.locator('table .ant-btn-link').first().click();
await sleep(4000);
const asides = page.locator('aside');
const n = await asides.count();
console.log('aside count:', n);
for (let i = 0; i < n; i++) {
  const t = await asides
    .nth(i)
    .innerText()
    .catch(() => 'N/A');
  console.log(
    `aside[${i}] (len=${t.length}):`,
    t.slice(0, 300).replaceAll('\n', ' | '),
  );
}
await browser.close();
