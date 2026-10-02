import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
const tenantSelect = page.locator('.ant-select:has-text("请选择租户")');
if (await tenantSelect.count()) {
  await tenantSelect.click();
  await sleep(1200);
  await page
    .locator('.ant-select-dropdown .ant-select-item-option')
    .first()
    .click();
  await sleep(500);
}
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});

await page.goto(`${base}/oa/process/leave`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
await page
  .locator('button', { hasText: /新\s*增/ })
  .first()
  .click();
await sleep(2500);
// 请假类型 label 的上层结构
const html = await page.evaluate(() => {
  const labels = [...document.querySelectorAll('[role="dialog"] label')];
  const l = labels.find((x) => x.textContent.includes('请假类型'));
  if (!l) return 'no label';
  let cur = l;
  const chain = [];
  for (let i = 0; i < 6 && cur; i++) {
    chain.push(`${cur.tagName}.${String(cur.className).slice(0, 60)}`);
    cur = cur.parentElement;
  }
  const formItem = l.closest('[class*="form-item"]');
  return `${chain.join(' < ')}\nformItem cls: ${
    formItem ? formItem.className : 'none'
  }\nformItem html snippet: ${formItem ? formItem.innerHTML.slice(0, 500) : ''}`;
});
console.log(html);
await browser.close();
