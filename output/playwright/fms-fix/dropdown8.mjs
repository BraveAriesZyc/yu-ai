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

async function probeSelect(path, selector) {
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' });
  await sleep(3000);
  return await page.evaluate((sel) => {
    const el = document.querySelector(sel);
    let inst = el?.__vueParentComponent;
    const chain = [];
    while (inst) {
      chain.push({
        name: inst.type?.name || inst.type?.__name || '(anon)',
        defaultSlotCount: inst.slots?.default ? inst.slots.default().length : 0,
      });
      inst = inst.parent;
    }
    return chain.slice(0, 6);
  }, selector);
}

const ws = await probeSelect('/infra/webSocket', '.ant-select');
const fms = await probeSelect('/fms/home', 'header .ant-select');
console.log(JSON.stringify({ webSocket: ws, fmsSwitcher: fms }, null, 2));
await browser.close();
