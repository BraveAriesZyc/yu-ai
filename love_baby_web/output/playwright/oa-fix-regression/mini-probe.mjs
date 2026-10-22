import { chromium } from 'playwright';
console.log('launching...');
const browser = await chromium.launch({ headless: true });
console.log('launched');
const page = await (await browser.newContext()).newPage();
console.log('goto login');
await page.goto('http://127.0.0.1:5666/auth/login', {
  waitUntil: 'domcontentloaded',
  timeout: 20_000,
});
console.log('login page ok:', await page.title());
await browser.close();
console.log('done');
