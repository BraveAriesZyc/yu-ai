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

async function clickVisibleConfirm(page) {
  const confirmButton = page
    .locator(
      '.ant-popconfirm:visible button, .ant-popover:visible button, .el-popconfirm:visible button, .el-popper:visible button',
    )
    .filter({ hasText: /确定|确认|OK|Yes/ })
    .last();
  if ((await confirmButton.count()) === 0) return false;
  await confirmButton.click();
  return true;
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
    if (!url.includes('/admin-api/infra/file-config/')) return;
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
  await page.goto(`${app.baseUrl}/infra/file/file-config`, {
    waitUntil: 'domcontentloaded',
  });
  await page
    .waitForResponse((resp) => resp.url().includes('/infra/file-config/page'), {
      timeout: 20_000,
    })
    .catch(() => {});
  await sleep(1500);

  const before = await page.locator('body').innerText();
  const actionButtons = page
    .locator(
      '.vxe-table--body button, .vxe-table--body a, .vxe-table button, .vxe-table a',
    )
    .filter({ hasText: '主配置' });
  const count = await actionButtons.count();
  let clicked = false;
  for (let index = 0; index < count; index += 1) {
    const button = actionButtons.nth(index);
    const disabled = await button.evaluate((node) => {
      const el = node.closest('button, a') || node;
      return (
        el.hasAttribute('disabled') ||
        el.getAttribute('aria-disabled') === 'true' ||
        el.className.includes('disabled')
      );
    });
    if (disabled) continue;
    await button.click();
    clicked = true;
    break;
  }

  const popconfirmText = clicked ? await page.locator('body').innerText() : '';
  const confirmed = clicked ? await clickVisibleConfirm(page) : false;
  await page
    .waitForResponse(
      (resp) => resp.url().includes('/infra/file-config/update-master'),
      {
        timeout: 10_000,
      },
    )
    .catch(() => {});
  await sleep(1500);

  const updateRequests = requests.filter((item) =>
    item.url.includes('/update-master'),
  );
  const loadingVisible = await page
    .locator(
      '.el-loading-mask:visible, .ant-message-notice:visible, .ant-spin-spinning:visible',
    )
    .count();
  await page.screenshot({
    fullPage: true,
    path: path.join(outDir, `${app.key}-probe-infra-file-config.png`),
  });
  rows.push({
    app: app.key,
    status:
      updateRequests.length > 0 && loadingVisible === 0 ? 'pass' : 'blocked',
    actionButtonCount: count,
    beforeHasPage: before.includes('文件配置列表'),
    clicked,
    confirmed,
    loadingVisible,
    popconfirmOpened: /是否要将|主配置|确定|确认/.test(popconfirmText),
    requests,
  });

  await context.close();
  await browser.close();
}

await fs.writeFile(
  path.join(outDir, 'probe-infra-file-config.json'),
  JSON.stringify(rows, null, 2),
);
console.dir(rows, { depth: 6 });
