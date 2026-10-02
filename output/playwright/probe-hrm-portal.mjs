/**
 * HRM 员工端 portal #28-#33 三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-portal.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-portal/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-portal/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-portal/ele'),
  },
];

const menus = [
  {
    key: 'home',
    route: '/hrm/portal/home',
    apiPattern: '/hrm/portal/employee/get',
    hints: ['Hi', '日历', '工作台'],
  },
  {
    key: 'employee',
    route: '/hrm/portal/employee',
    apiPattern: '/hrm/portal/employee/get',
    hints: ['我的档案', '基本信息', '岗位信息'],
  },
  {
    key: 'insurance',
    route: '/hrm/portal/insurance',
    apiPattern: '/hrm/portal/insurance/record/list',
    hints: ['社保', '所属月份'],
  },
  {
    key: 'salarySlip',
    route: '/hrm/portal/salary/slip',
    apiPattern: '/hrm/portal/salary/slip/list',
    hints: ['工资条', '按最近发放'],
  },
  {
    key: 'attendance',
    route: '/hrm/portal/attendance/report',
    apiPattern: '/hrm/portal/attendance/statistics/month-detail',
    hints: ['考勤报表', '应出勤天数', '请假'],
  },
  {
    key: 'performanceAssessment',
    route: '/hrm/portal/performance/assessment',
    apiPattern: '/hrm/portal/performance/assessment/task-count',
    hints: ['指标制定', '目标确认', '评分'],
  },
  {
    key: 'performanceHistory',
    route: '/hrm/portal/performance/history',
    apiPattern: '/hrm/portal/performance/assessment/page',
    hints: ['考核名称', '绩效得分', '绩效等级'],
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
  const hintHits = menu.hints.filter((h) => bodyText.includes(h));
  const apiOk = apiHits.some((h) => h.status >= 200 && h.status < 400);
  const redirectedToGuide = bodyText.includes('尚未开通员工端');

  return {
    route: menu.route,
    apiOk: apiOk || redirectedToGuide,
    hintHits,
    redirectedToGuide,
    apiHits: apiHits.slice(0, 3),
  };
}

async function probeApp(app) {
  await fs.mkdir(app.outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const results = [];

  try {
    await login(page, app.baseUrl);
    for (const menu of menus) {
      const result = await probePage(page, app.baseUrl, menu);
      const pass =
        result.apiOk &&
        (result.hintHits.length > 0 || result.redirectedToGuide);
      results.push({ menu: menu.key, pass, ...result });
      await page.screenshot({
        path: path.join(app.outDir, `${menu.key}.png`),
        fullPage: true,
      });
    }
  } finally {
    await browser.close();
  }

  await fs.writeFile(
    path.join(app.outDir, 'results.json'),
    JSON.stringify(results, null, 2),
  );
  return results;
}

const summary = [];
for (const app of apps) {
  let results;
  try {
    results = await probeApp(app);
  } catch (error) {
    console.error(`[${app.key}] FAIL startup:`, error.message);
    summary.push({ app: app.key, pass: false, error: error.message });
    continue;
  }
  const passCount = results.filter((r) => r.pass).length;
  const total = results.length;
  const ok = passCount === total;
  summary.push({ app: app.key, pass: ok, passCount, total, results });
  console.log(
    `[${app.key}] ${ok ? 'PASS' : 'FAIL'} ${passCount}/${total}`,
    results.map((r) => `${r.menu}:${r.pass ? 'OK' : 'NG'}`).join(' '),
  );
}

const allPass = summary.every((s) => s.pass);
console.log('\n=== HRM Portal Probe Summary ===');
console.log(JSON.stringify(summary, null, 2));
process.exit(allPass ? 0 : 1);
