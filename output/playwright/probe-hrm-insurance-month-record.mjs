/**
 * HRM 社保管理三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-insurance-month-record.mjs
 *
 * 验收：列表文案 + /hrm/insurance/month-record/list 与 /last 接口；有数据时验详情
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-insurance-month-record/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve(
      'output/playwright/hrm-insurance-month-record/antdv-next',
    ),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-insurance-month-record/ele'),
  },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function login(page, baseUrl) {
  await page.goto(`${baseUrl}/auth/login`, { waitUntil: 'domcontentloaded' });
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
    if (url.includes('/hrm/insurance/month-record/list')) {
      apiHits.push({ url, status: res.status(), kind: 'list' });
    }
    if (url.includes('/hrm/insurance/month-record/last')) {
      apiHits.push({ url, status: res.status(), kind: 'last' });
    }
    if (url.includes('/hrm/insurance/month-record/get')) {
      apiHits.push({ url, status: res.status(), kind: 'detail' });
    }
    if (url.includes('/hrm/insurance/month-employee-record/page')) {
      apiHits.push({ url, status: res.status(), kind: 'employee-page' });
    }
  });

  const result = {
    app: app.key,
    list: null,
    detail: null,
    url: `${app.baseUrl}/hrm/insurance/month-record`,
  };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/insurance/month-record`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(2500);
    await page.screenshot({
      path: path.join(app.outDir, 'list.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasListTitle =
      bodyText.includes('社保表') ||
      bodyText.includes('参保人数') ||
      bodyText.includes('新建首月社保表') ||
      bodyText.includes('新建次月社保表');
    const listOk = apiHits.some((h) => h.kind === 'list' && h.status === 200);
    const lastOk = apiHits.some((h) => h.kind === 'last' && h.status === 200);
    result.list = {
      ok: hasListTitle && listOk && lastOk,
      hasListTitle,
      listOk,
      lastOk,
      apiHits: apiHits
        .filter((h) => h.kind === 'list' || h.kind === 'last')
        .slice(-5),
    };

    const titleLink = page
      .locator(
        '.ant-table-row a, .el-table__row a, .vxe-body--row a, .vxe-body--row button',
      )
      .first();
    if (await titleLink.count()) {
      await titleLink.click();
      await page
        .waitForURL(/\/hrm\/insurance\/month-record\/detail\//, {
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
          (h.kind === 'detail' || h.kind === 'employee-page') &&
          h.status === 200,
      );
      result.detail = {
        ok:
          detailOk &&
          (detailText.includes('参保人员') ||
            detailText.includes('月度社保详情') ||
            detailText.includes('添加参保人员')),
        detailOk,
        hasEmployeeGrid: detailText.includes('参保人员'),
        hasSummary:
          detailText.includes('停保人数') && detailText.includes('个人社保'),
      };
    } else {
      result.detail = {
        ok: true,
        skipped: true,
        reason: 'no month record row',
      };
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
