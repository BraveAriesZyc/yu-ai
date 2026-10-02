/**
 * HRM 薪资管理（月度工资表）三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-salary-month-record.mjs
 *
 * 验收：页面文案 + /hrm/salary/month-record/last 与 month-employee-record 相关接口
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-salary-month-record/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve(
      'output/playwright/hrm-salary-month-record/antdv-next',
    ),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-salary-month-record/ele'),
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

async function probeApp(app) {
  await fs.mkdir(app.outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const apiHits = [];
  page.on('response', (res) => {
    const url = res.url();
    if (url.includes('/hrm/salary/month-record/last')) {
      apiHits.push({ url, status: res.status(), kind: 'last' });
    }
    if (url.includes('/hrm/salary/month-record/payroll-readiness')) {
      apiHits.push({ url, status: res.status(), kind: 'readiness' });
    }
    if (url.includes('/hrm/salary/month-employee-record/page')) {
      apiHits.push({ url, status: res.status(), kind: 'employee-page' });
    }
    if (url.includes('/hrm/salary/month-employee-record/change-count')) {
      apiHits.push({ url, status: res.status(), kind: 'change-count' });
    }
    if (url.includes('/hrm/salary/month-record/option-summary')) {
      apiHits.push({ url, status: res.status(), kind: 'option-summary' });
    }
  });

  const result = {
    app: app.key,
    list: null,
    url: `${app.baseUrl}/hrm/salary/month-record`,
  };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/salary/month-record`, {
      waitUntil: 'domcontentloaded',
      timeout: 60_000,
    });
    await sleep(2500);
    await page.screenshot({
      path: path.join(app.outDir, 'page.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasPageTitle =
      bodyText.includes('月度工资表') ||
      bodyText.includes('暂无月度工资表') ||
      bodyText.includes('初始化月度工资表');
    const lastOk = apiHits.some((h) => h.kind === 'last' && h.status === 200);
    const hasEmployeeApi = apiHits.some(
      (h) =>
        (h.kind === 'employee-page' ||
          h.kind === 'change-count' ||
          h.kind === 'option-summary' ||
          h.kind === 'readiness') &&
        h.status === 200,
    );
    const emptyState = bodyText.includes('暂无月度工资表');
    result.list = {
      ok: hasPageTitle && lastOk && (emptyState || hasEmployeeApi),
      hasPageTitle,
      lastOk,
      emptyState,
      hasEmployeeApi,
      apiHits: apiHits.slice(-8),
    };
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
const failed = results.filter((r) => !r.list?.ok || r.error);
process.exit(failed.length > 0 ? 1 : 0);
