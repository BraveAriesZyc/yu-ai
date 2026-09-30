/**
 * HRM 绩效模块 #16-#19 三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-performance.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-performance/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-performance/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-performance/ele'),
  },
];

const menus = [
  {
    key: 'plan',
    route: '/hrm/performance/plan',
    apiPattern: '/hrm/performance/plan/page',
    hints: ['KPI', '考核计划', '计划名称', '未开始'],
  },
  {
    key: 'assessment',
    route: '/hrm/performance/assessment',
    apiPattern: '/hrm/performance/assessment/archive-employee-page',
    hints: ['绩效档案', '员工姓名', '考核次数'],
  },
  {
    key: 'assessmentTemplate',
    route: '/hrm/performance/config/assessment-template',
    apiPattern: '/hrm/performance/assessment-template/page',
    hints: ['考核指标模板', '模板名称', '考核维度'],
  },
  {
    key: 'resultTemplate',
    route: '/hrm/performance/config/result-template',
    apiPattern: '/hrm/performance/result-template/page',
    hints: ['考核结果设置', '结果模板', '等级设置'],
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

async function probePage(page, baseUrl, menu) {
  const apiHits = [];
  const handler = (res) => {
    const url = res.url();
    if (url.includes(menu.apiPattern)) {
      apiHits.push({ url, status: res.status() });
    }
  };
  page.on('response', handler);
  await page.goto(`${baseUrl}${menu.route}`, {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  });
  await sleep(4000);
  page.off('response', handler);
  const bodyText = await page.locator('body').innerText();
  const hasTitle = menu.hints.some((hint) => bodyText.includes(hint));
  const apiOk = apiHits.some((h) => h.status === 200);
  return { hasTitle, apiOk, apiHits: apiHits.slice(-3) };
}

async function probeApp(app) {
  await fs.mkdir(app.outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const result = { app: app.key, menus: {}, error: null };

  try {
    await login(page, app.baseUrl);
    for (const menu of menus) {
      result.menus[menu.key] = await probePage(page, app.baseUrl, menu);
      result.menus[menu.key].ok =
        result.menus[menu.key].hasTitle && result.menus[menu.key].apiOk;
      await page.screenshot({
        path: path.join(app.outDir, `${menu.key}.png`),
        fullPage: true,
      });
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
  results.push(await probeApp(app));
}
console.log(JSON.stringify(results, null, 2));
const failed = results.filter(
  (r) => r.error || menus.some((m) => !r.menus?.[m.key]?.ok),
);
process.exit(failed.length > 0 ? 1 : 0);
