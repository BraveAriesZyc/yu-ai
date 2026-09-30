/**
 * HRM 历史工资三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-salary-history.mjs
 *
 * 验收：列表文案 + /hrm/salary/month-record/page 接口；有数据时验详情
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-salary-history/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-salary-history/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-salary-history/ele'),
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
    if (url.includes('/hrm/salary/month-record/page')) {
      apiHits.push({ url, status: res.status(), kind: 'page' });
    }
    if (url.includes('/hrm/salary/month-record/get')) {
      apiHits.push({ url, status: res.status(), kind: 'detail' });
    }
    if (url.includes('/hrm/salary/month-employee-record/page')) {
      apiHits.push({ url, status: res.status(), kind: 'employee-page' });
    }
    if (url.includes('/hrm/salary/month-record/option-summary')) {
      apiHits.push({ url, status: res.status(), kind: 'option-summary' });
    }
  });

  const result = {
    app: app.key,
    list: null,
    detail: null,
    url: `${app.baseUrl}/hrm/salary/history`,
  };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/salary/history`, {
      waitUntil: 'domcontentloaded',
      timeout: 60_000,
    });
    await sleep(2500);
    await page.screenshot({
      path: path.join(app.outDir, 'list.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasListTitle =
      bodyText.includes('历史工资表') ||
      bodyText.includes('工资表') ||
      bodyText.includes('应发工资');
    const pageOk = apiHits.some((h) => h.kind === 'page' && h.status === 200);
    result.list = {
      ok: hasListTitle && pageOk,
      hasListTitle,
      pageOk,
      apiHits: apiHits.filter((h) => h.kind === 'page').slice(-3),
    };

    const titleLink = page
      .locator(
        '.vxe-body--row button, .vxe-body--row a, .ant-table-row a, .el-table__row a, .el-table__row button',
      )
      .first();
    if (await titleLink.count()) {
      await titleLink.click();
      await page
        .waitForURL(/\/hrm\/salary\/history\/detail\//, {
          timeout: 15_000,
        })
        .catch(() => {});
      await sleep(2500);
      await page.screenshot({
        path: path.join(app.outDir, 'detail.png'),
        fullPage: true,
      });
      const detailText = await page.locator('body').innerText();
      const detailOk = apiHits.some(
        (h) =>
          (h.kind === 'detail' ||
            h.kind === 'employee-page' ||
            h.kind === 'option-summary') &&
          h.status === 200,
      );
      result.detail = {
        ok:
          detailOk &&
          (detailText.includes('历史工资表详情') ||
            detailText.includes('员工工资明细') ||
            detailText.includes('应发工资')),
        detailOk,
        hasSummary:
          detailText.includes('计薪人数') && detailText.includes('实发工资'),
        hasEmployeeGrid: detailText.includes('员工工资明细'),
      };
    } else {
      result.detail = { ok: true, skipped: true, reason: 'no history row' };
    }
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
  (r) =>
    !r.list?.ok || (r.detail && !r.detail.ok && !r.detail.skipped) || r.error,
);
process.exit(failed.length > 0 ? 1 : 0);
