import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1440, height: 1000 } })
).newPage();
await page.goto('http://127.0.0.1:5666/auth/login', {
  waitUntil: 'domcontentloaded',
});
await sleep(500);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto('http://127.0.0.1:5666/hrm/home', { waitUntil: 'networkidle' });
await sleep(2500);
const info = await page.evaluate(() => {
  const cols = [...document.querySelectorAll('.ant-col')]
    .slice(0, 2)
    .map((col) => {
      const cs = getComputedStyle(col);
      return {
        className: col.className,
        display: cs.display,
        gap: cs.gap,
        flexDirection: cs.flexDirection,
        children: col.children.length,
      };
    });
  const cards = [...document.querySelectorAll('.ant-card')].filter(
    (c) => c.getBoundingClientRect().left < 700,
  );
  const gaps = cards.slice(0, -1).map((a, i) => {
    const ar = a.getBoundingClientRect();
    const br = cards[i + 1].getBoundingClientRect();
    return {
      gap: Math.round(br.top - ar.bottom),
      aMb: getComputedStyle(a).marginBottom,
    };
  });
  return { cols, gaps };
});
console.log(JSON.stringify(info, null, 2));
await browser.close();
