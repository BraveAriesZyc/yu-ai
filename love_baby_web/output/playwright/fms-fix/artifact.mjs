import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
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
await page.screenshot({ path: 'output/playwright/fms-fix/list-artifact.png' });

// 在用户标注区域取几个点，看是什么元素
const probe = await page.evaluate(() => {
  const points = [
    [1100, 500],
    [1100, 550],
    [1000, 450],
    [1150, 600],
  ];
  return points.map(([x, y]) => {
    const el = document.elementFromPoint(x, y);
    if (!el) return { x, y, el: null };
    const chain = [];
    let cur = el;
    for (let i = 0; i < 5 && cur; i++) {
      chain.push(`${cur.tagName}.${String(cur.className).slice(0, 70)}`);
      cur = cur.parentElement;
    }
    const r = el.getBoundingClientRect();
    return {
      x,
      y,
      rect: {
        x: Math.round(r.x),
        y: Math.round(r.y),
        w: Math.round(r.width),
        h: Math.round(r.height),
      },
      chain,
    };
  });
});
console.log(JSON.stringify(probe, null, 1));
await browser.close();
