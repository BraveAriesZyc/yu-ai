/**
 * HRM 考勤打卡记录三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-attendance-clock.mjs
 *
 * 验收：列表 Tab 文案 + 概况/明细分页接口
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-attendance-clock/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-attendance-clock/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-attendance-clock/ele'),
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
    if (
      url.includes('/hrm/attendance/statistics/month-daily-page') ||
      url.includes('/hrm/attendance/clock/page')
    ) {
      apiHits.push({ url, status: res.status() });
    }
  });

  const result = { app: app.key, overview: null, record: null };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/attendance/clock`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(2500);
    await page.screenshot({
      path: path.join(app.outDir, 'overview-tab.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasOverviewTab =
      bodyText.includes('打卡概况') && bodyText.includes('打卡明细');
    const overviewOk = apiHits.some(
      (h) =>
        h.url.includes('/hrm/attendance/statistics/month-daily-page') &&
        h.status === 200,
    );
    result.overview = {
      ok: hasOverviewTab && overviewOk,
      hasOverviewTab,
      overviewOk,
      apiHits: apiHits.slice(-8),
    };

    const recordTab = page
      .locator(
        '.ant-tabs-tab:has-text("打卡明细"), .el-tabs__item:has-text("打卡明细"), [role="tab"]:has-text("打卡明细")',
      )
      .first();
    if (await recordTab.count()) {
      await recordTab.click();
      await sleep(2000);
      await page.screenshot({
        path: path.join(app.outDir, 'record-tab.png'),
        fullPage: true,
      });
      const recordText = await page.locator('body').innerText();
      const recordPageOk = apiHits.some(
        (h) => h.url.includes('/hrm/attendance/clock/page') && h.status === 200,
      );
      result.record = {
        ok:
          (recordText.includes('打卡类型') ||
            recordText.includes('打卡时间')) &&
          recordPageOk,
        recordPageOk,
      };
    } else {
      result.record = { ok: false, reason: 'no record tab' };
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
  (r) => !r.overview?.ok || !r.record?.ok || r.error,
);
process.exit(failed.length > 0 ? 1 : 0);
