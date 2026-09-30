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
const r = await page.evaluate(() => {
  const el = document.querySelector('header .ant-select');
  let inst = el?.__vueParentComponent;
  const chain = [];
  while (inst) {
    chain.push({
      name: inst.type?.name || inst.type?.__name || '(anon)',
      hasDefaultSlot: !!inst.slots?.default,
      defaultSlotCount: inst.slots?.default ? inst.slots.default().length : 0,
      childVNodes: Array.isArray(inst.slots?.default?.())
        ? inst.slots
            .default()
            .map((v) => String(v.type?.name || v.type || typeof v))
        : null,
    });
    inst = inst.parent;
  }
  return chain.slice(0, 6);
});
console.log(JSON.stringify(r, null, 2));
await browser.close();
