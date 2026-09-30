import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium, request as playwrightRequest } from 'playwright';

const outDir = path.resolve('output/playwright/vben-p0-targeted');
await fs.mkdir(outDir, { recursive: true });

const apps = [
  { key: 'antdv-next', baseUrl: 'http://127.0.0.1:5999' },
  { key: 'ele', baseUrl: 'http://127.0.0.1:5777' },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function login(page, baseUrl) {
  await page.goto(`${baseUrl}/auth/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 25_000,
  });
  await sleep(1500);
}

async function readAuth(page) {
  return page.evaluate(() => {
    const local = { ...localStorage };
    const session = { ...sessionStorage };
    return { local, session };
  });
}

function findToken(storage) {
  const all = { ...storage.local, ...storage.session };
  for (const [key, value] of Object.entries(all)) {
    if (key.toLowerCase().includes('access') && /[a-f0-9]{32}/i.test(value)) {
      return value.match(/[a-f0-9]{32}/i)?.[0];
    }
    if (typeof value === 'string' && value.includes('accessToken')) {
      try {
        const json = JSON.parse(value);
        const token = JSON.stringify(json).match(/[a-f0-9]{32}/i)?.[0];
        if (token) return token;
      } catch {}
    }
  }
  return '';
}

const rows = [];
for (const app of apps) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const requests = [];
  page.on('requestfinished', async (request) => {
    const url = request.url();
    if (!url.includes('/admin-api/pay/')) return;
    const response = await request.response();
    let body;
    try {
      body = await response?.json();
    } catch {}
    requests.push({
      method: request.method(),
      status: response?.status(),
      url,
      body,
    });
  });
  await login(page, app.baseUrl);
  const token = findToken(await readAuth(page));
  const api = await playwrightRequest.newContext({
    baseURL: app.baseUrl,
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      'tenant-id': '1',
      'Content-Type': 'application/json',
    },
  });
  const createResp = await api.post('/admin-api/pay/demo-order/create', {
    data: { spuId: 1 },
  });
  const createBody = await createResp.json();
  const demoOrderId = createBody.data;
  const pageResp = await api.get(
    '/admin-api/pay/demo-order/page?pageNo=1&pageSize=1',
  );
  const pageBody = await pageResp.json();
  const demoOrder =
    pageBody.data?.list?.find((item) => item.id === demoOrderId) ||
    pageBody.data?.list?.[0];
  const payOrderId = demoOrder?.payOrderId;

  if (!payOrderId) {
    rows.push({ app: app.key, status: 'blocked', createBody, pageBody });
    await context.close();
    await browser.close();
    continue;
  }

  await page.goto(`${app.baseUrl}/pay/cashier?id=${payOrderId}`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(1800);
  await page.getByText('微信扫码支付', { exact: false }).first().click();
  await sleep(2500);
  const text = await page.locator('body').innerText();
  const qrCount = await page
    .locator('canvas, .ant-qrcode, img[src^="data:image"]')
    .count();
  await page.screenshot({
    fullPage: true,
    path: path.join(outDir, `${app.key}-probe-pay-cashier.png`),
  });
  rows.push({
    app: app.key,
    status: text.includes('请使用微信') && qrCount > 0 ? 'pass' : 'blocked',
    demoOrderId,
    payOrderId,
    qrCount,
    submitRequests: requests.filter((item) =>
      item.url.includes('/pay/order/submit'),
    ),
    text: text.slice(0, 800),
  });
  await api.dispose();
  await context.close();
  await browser.close();
}

await fs.writeFile(
  path.join(outDir, 'probe-pay-cashier.json'),
  JSON.stringify(rows, null, 2),
);
console.dir(rows, { depth: 6 });
