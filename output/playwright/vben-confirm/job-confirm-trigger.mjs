import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const baseUrl = process.env.VBEN_ANTD_URL || 'http://127.0.0.1:5666';
const outDir = path.resolve('output/playwright/vben-confirm');
const resultPath = path.join(outDir, 'job-confirm-trigger.json');
const screenshotPath = path.join(outDir, 'job-confirm-trigger.png');

await fs.mkdir(outDir, { recursive: true });

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const result = {
  baseUrl,
  mockedEndpoints: ['/admin-api/infra/job/trigger'],
  notes: [],
  requests: [],
  screenshot: screenshotPath,
  status: 'unknown',
};

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  ignoreHTTPSErrors: true,
  viewport: { height: 1000, width: 1440 },
});
const page = await context.newPage();
page.setDefaultNavigationTimeout(30_000);
page.setDefaultTimeout(20_000);

page.on('console', (message) => {
  const text = message.text();
  if (
    text.includes('Download the Vue Devtools') ||
    text.includes('[StorageManager] empty prefix') ||
    text.includes('menu name duplicate:')
  ) {
    return;
  }
  result.notes.push(`[console:${message.type()}] ${text}`);
});

page.on('requestfinished', async (request) => {
  const url = request.url();
  if (!url.includes('/admin-api/')) return;
  const response = await request.response();
  let body;
  try {
    const contentType = response?.headers()['content-type'] || '';
    if (contentType.includes('application/json')) {
      body = await response?.json();
    }
  } catch {
    body = undefined;
  }
  result.requests.push({
    body,
    method: request.method(),
    postData: request.postData(),
    status: response?.status(),
    url,
  });
});

page.on('requestfailed', (request) => {
  const url = request.url();
  if (!url.includes('/admin-api/')) return;
  result.requests.push({
    failure: request.failure()?.errorText,
    method: request.method(),
    postData: request.postData(),
    url,
  });
});

async function saveAndClose(status, error) {
  result.status = status;
  if (error) {
    result.notes.push(error.stack || error.message || String(error));
  }
  await page
    .screenshot({ fullPage: true, path: screenshotPath })
    .catch(() => {});
  await fs.writeFile(resultPath, JSON.stringify(result, null, 2));
  await context.close();
  await browser.close();
}

try {
  await page.route('**/admin-api/infra/job/trigger**', async (route) => {
    result.notes.push(`mocked trigger request: ${route.request().url()}`);
    await route.fulfill({
      body: JSON.stringify({ code: 0, data: true, msg: '' }),
      contentType: 'application/json',
      status: 200,
    });
  });

  await page.goto(`${baseUrl}/auth/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 30_000,
  });
  await page.waitForLoadState('domcontentloaded');
  await sleep(1200);

  await page.goto(`${baseUrl}/infra/job`, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('domcontentloaded');
  await page
    .waitForResponse(
      (response) =>
        response.url().includes('/admin-api/infra/job/page') &&
        response.request().method() === 'GET',
      { timeout: 30_000 },
    )
    .catch(() => {});
  await sleep(1500);

  const bodyText = await page.locator('body').innerText();
  result.notes.push(
    `job page location: ${await page.evaluate(() => location.href)}`,
  );
  if (!bodyText.includes('定时任务列表')) {
    throw new Error(
      `未进入定时任务列表，当前页面文本：${bodyText.slice(0, 300)}`,
    );
  }

  const firstDataRow = page
    .locator('.vxe-table--body-wrapper tbody tr')
    .first();
  await firstDataRow.waitFor({ state: 'visible' });
  const rowSnapshot = await firstDataRow.innerText();
  result.notes.push(`first row: ${rowSnapshot.replaceAll('\n', ' | ')}`);
  const firstExecute = firstDataRow.getByText('执行', { exact: true });
  await firstExecute.waitFor({ state: 'visible' });
  await firstExecute.click();

  const dialog = page.locator('[data-slot="alert-dialog-content"]').last();
  await dialog.waitFor({ state: 'visible' });
  const dialogText = await dialog.innerText();
  result.notes.push(`confirm dialog: ${dialogText.replaceAll('\n', ' | ')}`);
  if (!dialogText.includes('确定执行一次')) {
    throw new Error(`执行确认弹窗文案异常：${dialogText}`);
  }
  const confirmButton = dialog.getByText(/确\s*认/);
  await confirmButton.waitFor({ state: 'visible' });

  const triggerResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/admin-api/infra/job/trigger') &&
      response.request().method() === 'PUT',
    { timeout: 20_000 },
  );
  await confirmButton.click();
  const triggerResponse = await triggerResponsePromise;
  const triggerJson = await triggerResponse.json().catch(() => undefined);
  result.notes.push(`trigger response: ${JSON.stringify(triggerJson)}`);

  await dialog.waitFor({ state: 'hidden', timeout: 20_000 });
  const successVisible = await page
    .getByText(/操作成功|执行成功|成功/)
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);

  if (!triggerResponse.ok()) {
    throw new Error(`触发接口 HTTP 状态异常：${triggerResponse.status()}`);
  }
  if (triggerJson && triggerJson.code !== undefined && triggerJson.code !== 0) {
    throw new Error(`触发接口业务状态异常：${JSON.stringify(triggerJson)}`);
  }
  if (!successVisible) {
    result.notes.push('未检测到成功提示，但弹窗已关闭且 trigger 请求成功。');
  }

  await saveAndClose('pass');
} catch (error) {
  await saveAndClose('fail', error);
  process.exitCode = 1;
}
