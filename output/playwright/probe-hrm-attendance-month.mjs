/**
 * HRM 考勤月度汇总三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-attendance-month.mjs
 *
 * 验收：列表文案 + /hrm/attendance/statistics/month-record-page 接口
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-attendance-month/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-attendance-month/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-attendance-month/ele'),
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
    if (url.includes('/hrm/attendance/statistics/month-record-page')) {
      apiHits.push({ url, status: res.status() });
    }
    if (url.includes('/hrm/attendance/statistics/month-detail')) {
      apiHits.push({ url, status: res.status(), kind: 'detail' });
    }
  });

  const result = { app: app.key, list: null, detail: null };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/attendance/month`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(2500);
    await page.screenshot({
      path: path.join(app.outDir, 'list.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasListTitle = bodyText.includes('月度考勤汇总');
    const hasFilters =
      bodyText.includes('月份') && bodyText.includes('是否全勤');
    const pageOk = apiHits.some(
      (h) =>
        h.url.includes('/hrm/attendance/statistics/month-record-page') &&
        h.status === 200,
    );
    result.list = {
      ok: hasListTitle && hasFilters && pageOk,
      hasListTitle,
      hasFilters,
      pageOk,
      apiHits: apiHits.filter((h) => !h.kind).slice(-5),
    };

    const employeeLink = page
      .locator(
        '.vxe-body--column a, .vxe-body--column button, .vxe-body--column .ant-btn-link, .vxe-body--column .el-button.is-link',
      )
      .first();
    if (await employeeLink.count()) {
      await employeeLink.click();
      await sleep(2500);
      await page.screenshot({
        path: path.join(app.outDir, 'detail.png'),
        fullPage: true,
      });
      const detailText = await page.locator('body').innerText();
      const detailOk = apiHits.some(
        (h) =>
          h.kind === 'detail' &&
          h.url.includes('/hrm/attendance/statistics/month-detail') &&
          h.status === 200,
      );
      result.detail = {
        ok:
          detailOk &&
          (detailText.includes('应出勤') || detailText.includes('请假记录')),
        detailOk,
        hasSummary: detailText.includes('应出勤'),
        hasLeaveSection: detailText.includes('请假记录'),
      };
    } else {
      result.detail = { ok: true, skipped: true, reason: 'no employee row' };
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
