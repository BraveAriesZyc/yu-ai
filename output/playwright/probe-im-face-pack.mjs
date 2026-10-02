import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

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
    if (!url.includes('/admin-api/im/manager/face-pack')) return;
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
  await page.goto(`${app.baseUrl}/im/face/pack`, {
    waitUntil: 'domcontentloaded',
  });
  await page
    .waitForResponse(
      (resp) => resp.url().includes('/im/manager/face-pack/page'),
      {
        timeout: 20_000,
      },
    )
    .catch(() => {});
  await sleep(1500);
  const beforeText = await page.locator('body').innerText();
  await page.screenshot({
    fullPage: true,
    path: path.join(outDir, `${app.key}-probe-im-before.png`),
  });

  const manageLocators = await page
    .getByText('管理表情', { exact: false })
    .count();
  let clicked = false;
  const tableManage = page
    .locator('.vxe-table--body, .vxe-table')
    .getByText('管理表情', { exact: false })
    .first();
  if ((await tableManage.count()) > 0) {
    await tableManage.click();
    clicked = true;
  }
  await page
    .waitForResponse(
      (resp) => resp.url().includes('/im/manager/face-pack-item/page'),
      {
        timeout: 20_000,
      },
    )
    .catch(() => {});
  await sleep(1500);
  const afterText = await page.locator('body').innerText();
  await page.screenshot({
    fullPage: true,
    path: path.join(outDir, `${app.key}-probe-im-after.png`),
  });
  rows.push({
    app: app.key,
    actualPath: await page.evaluate(() => location.pathname),
    clicked,
    manageLocators,
    beforeHasPackList: beforeText.includes('表情包列表'),
    afterHasItemList: afterText.includes('表情列表'),
    requests,
    beforeText: beforeText.slice(0, 600),
    afterText: afterText.slice(0, 600),
  });
  await context.close();
  await browser.close();
}

await fs.writeFile(
  path.join(outDir, 'probe-im-face-pack.json'),
  JSON.stringify(rows, null, 2),
);
console.dir(rows, { depth: 5 });
