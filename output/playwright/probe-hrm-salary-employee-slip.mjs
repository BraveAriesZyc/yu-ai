/**
 * HRM 薪资档案 + 发放记录三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-salary-employee-slip.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-salary-employee-slip/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve(
      'output/playwright/hrm-salary-employee-slip/antdv-next',
    ),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-salary-employee-slip/ele'),
  },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function login(page, baseUrl) {
  await page.goto(`${baseUrl}/auth/login`, {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  });
  await sleep(1000);
  const loginBtn = page.locator('button[aria-label="login"]');
  if (await loginBtn.count()) {
    await loginBtn.click();
  } else {
    const user = page
      .locator(
        'input[type="text"], input[placeholder*="账号"], input[placeholder*="用户"]',
      )
      .first();
    const pass = page.locator('input[type="password"]').first();
    if (await user.count()) await user.fill('admin');
    if (await pass.count()) await pass.fill('admin123');
    await page
      .locator('button:has-text("登录"), button[type="submit"]')
      .first()
      .click();
  }
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 45_000,
  });
  await sleep(1500);
}

async function probePage(page, baseUrl, route, apiPattern, titleHints) {
  const apiHits = [];
  const handler = (res) => {
    const url = res.url();
    if (url.includes(apiPattern)) {
      apiHits.push({ url, status: res.status() });
    }
  };
  page.on('response', handler);
  await page.goto(`${baseUrl}${route}`, {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  });
  await sleep(4000);
  page.off('response', handler);
  const bodyText = await page.locator('body').innerText();
  const hasTitle = titleHints.some((hint) => bodyText.includes(hint));
  const apiOk = apiHits.some((h) => h.status === 200);
  return { hasTitle, apiOk, apiHits: apiHits.slice(-5) };
}

async function probeApp(app) {
  await fs.mkdir(app.outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();

  const result = { app: app.key, employeeInfo: null, slipSendRecord: null };

  try {
    await login(page, app.baseUrl);

    result.employeeInfo = await probePage(
      page,
      app.baseUrl,
      '/hrm/salary/employee-info',
      '/hrm/salary/employee-info/page',
      ['薪资档案', '员工姓名', '批量调薪'],
    );
    await page.screenshot({
      path: path.join(app.outDir, 'employee-info.png'),
      fullPage: true,
    });

    result.slipSendRecord = await probePage(
      page,
      app.baseUrl,
      '/hrm/salary/slip',
      '/hrm/salary/slip-send-record/page',
      ['工资条发放记录', '工资月份', '发放人数'],
    );
    await page.screenshot({
      path: path.join(app.outDir, 'slip-send-record.png'),
      fullPage: true,
    });

    result.employeeInfo.ok =
      result.employeeInfo.hasTitle && result.employeeInfo.apiOk;
    result.slipSendRecord.ok =
      result.slipSendRecord.hasTitle && result.slipSendRecord.apiOk;
  } catch (error) {
    result.error = String(error);
  } finally {
    await browser.close();
  }

  await fs.writeFile(
    path.join(app.outDir, 'results.json'),
    JSON.stringify(result, null, 2),
  );
  return result;
}

const results = [];
for (const app of apps) {
  // eslint-disable-next-line no-await-in-loop
  results.push(await probeApp(app));
}
console.log(JSON.stringify(results, null, 2));
const failed = results.filter(
  (r) => !r.employeeInfo?.ok || !r.slipSendRecord?.ok || r.error,
);
process.exit(failed.length > 0 ? 1 : 0);
