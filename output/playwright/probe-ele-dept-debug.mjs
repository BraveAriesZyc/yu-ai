import { chromium } from 'playwright';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
await page.goto('http://127.0.0.1:5777/auth/login', {
  waitUntil: 'domcontentloaded',
});
await page.waitForTimeout(800);
const loginBtn = page.locator('button[aria-label="login"]');
if (await loginBtn.count()) await loginBtn.click();
await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
  timeout: 45_000,
});
await page.goto('http://127.0.0.1:5777/hrm/dept', { waitUntil: 'networkidle' });
await page.waitForTimeout(3000);
const buttons = await page.locator('.vxe-body--row button').allTextContents();
console.log('BUTTONS', JSON.stringify(buttons.slice(0, 15)));
console.log(
  'EL_PRIMARY',
  await page.locator('.vxe-table .el-button--primary').count(),
);
console.log(
  'EL_LINK',
  await page.locator('.vxe-table .el-button.is-link').count(),
);
const link = page.locator('.vxe-table .el-button.is-link').first();
if (await link.count()) {
  await link.click();
  await page.waitForTimeout(3500);
}
console.log('URL', page.url());
const text = await page.locator('body').innerText();
console.log('HAS_DETAIL', text.includes('详细资料'));
console.log('HAS_EMP', text.includes('员工列表'));
console.log('ERRORS', errors.slice(0, 8));
await page.screenshot({
  path: 'output/playwright/hrm-dept/ele/manual-detail.png',
  fullPage: true,
});
await browser.close();
