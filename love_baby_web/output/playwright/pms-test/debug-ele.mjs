import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message.slice(0, 300)));
page.on('console', (m) => {
  if (m.type() === 'error') console.log('CERR:', m.text().slice(0, 250));
});
page.on('requestfailed', (r) =>
  console.log('REQFAIL:', r.url().slice(-80), r.failure()?.errorText),
);
await page.goto('http://localhost:5777/auth/login', {
  waitUntil: 'domcontentloaded',
});
await sleep(1000);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
console.log('logged in');
await page.goto('http://localhost:5777/pms/pm/project/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(6000);
console.log('url:', page.url());
console.log(
  'body:',
  await page.evaluate(() => document.body.innerText.slice(0, 200)),
);
await browser.close();
