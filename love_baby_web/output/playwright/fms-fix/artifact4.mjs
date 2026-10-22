import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 2560, height: 1080 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
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
await page.goto(`${BASE}/fms/voucher/list`, { waitUntil: 'domcontentloaded' });
await sleep(4500);
const probe = await page.evaluate(() => {
  const out = [];
  for (const [x, y] of [
    [1000, 400],
    [1000, 430],
    [1100, 445],
    [950, 460],
  ]) {
    const el = document.elementFromPoint(x, y);
    if (!el) {
      out.push({ x, y, el: null });
      continue;
    }
    const cs = getComputedStyle(el);
    const chain = [];
    let cur = el;
    for (let i = 0; i < 4 && cur; i++) {
      chain.push(`${cur.tagName}.${String(cur.className).slice(0, 60)}`);
      cur = cur.parentElement;
    }
    out.push({ x, y, borderBottom: cs.borderBottom, chain });
  }
  return out;
});
console.log(JSON.stringify(probe, null, 1));
await browser.close();
