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

async function createFreshPayOrder(page, app) {
  const token = findToken(await readAuth(page));
  const api = await playwrightRequest.newContext({
    baseURL: app.baseUrl,
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'tenant-id': '1',
    },
  });
  try {
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
    return {
      createBody,
      demoOrderId,
      pageBody,
      payOrderId: demoOrder?.payOrderId,
    };
  } finally {
    await api.dispose();
  }
}

const rows = [];
for (const app of apps) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const submitCalls = [];

  await page.route('**/admin-api/pay/order/submit', async (route) => {
    submitCalls.push({
      method: route.request().method(),
      postData: route.request().postData(),
    });
    await route.fulfill({
      contentType: 'application/json',
      status: 200,
      body: JSON.stringify({
        code: 0,
        msg: '',
        data: {
          displayContent: 'weixin://wxpay/bizpayurl?pr=codexMockQr',
          displayMode: 'qr_code',
          status: 0,
        },
      }),
    });
  });

  await login(page, app.baseUrl);
  const payOrder = await createFreshPayOrder(page, app);
  if (!payOrder.payOrderId) {
    rows.push({ app: app.key, status: 'blocked', payOrder });
    await context.close();
    await browser.close();
    continue;
  }

  await page.goto(`${app.baseUrl}/pay/cashier?id=${payOrder.payOrderId}`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(1800);
  await page.getByText('微信扫码支付', { exact: false }).first().click();
  await sleep(1800);

  const bodyText = await page.locator('body').innerText();
  const qrCount = await page
    .locator(
      '.ant-qrcode, canvas, img[alt="支付二维码"], img[src^="data:image"]',
    )
    .count();
  const imageSrcs = await page.locator('img').evaluateAll((imgs) =>
    imgs.map((img) => ({
      alt: img.getAttribute('alt'),
      src: img.getAttribute('src')?.slice(0, 120),
    })),
  );
  await page.screenshot({
    fullPage: true,
    path: path.join(outDir, `${app.key}-probe-pay-qrcode-mock.png`),
  });
  rows.push({
    app: app.key,
    status: bodyText.includes('请使用微信') && qrCount > 0 ? 'pass' : 'fail',
    demoOrderId: payOrder.demoOrderId,
    imageSrcs,
    payOrderId: payOrder.payOrderId,
    qrCount,
    submitCalls,
    text: bodyText.slice(0, 700),
  });

  await context.close();
  await browser.close();
}

await fs.writeFile(
  path.join(outDir, 'probe-pay-qrcode-mock.json'),
  JSON.stringify(rows, null, 2),
);
console.dir(rows, { depth: 6 });
