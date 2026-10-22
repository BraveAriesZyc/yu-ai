import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-home-antdv-next'),
    dialogSelector: '.ant-modal, [role="dialog"]',
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-home-ele'),
    dialogSelector: '.el-overlay, [role="dialog"]',
  },
];

const routes = [
  {
    key: 'hr',
    path: '/hrm/home',
    expectTexts: [
      'HR 工作台',
      '人事概况',
      '招聘动态',
      '上月薪资概况',
      '待办提醒',
      '日历',
    ],
    expectApis: ['/hrm/home/hr-statistics-summary', '/hrm/home/hr-calendar'],
  },
  {
    key: 'team',
    path: '/hrm/team-home',
    expectTexts: ['团队工作台', '我的团队', '团队概况', '日历'],
    expectApis: [
      '/hrm/home/team-statistics-summary',
      '/hrm/home/team-calendar',
    ],
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
  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('/hrm/home/')) {
      apiHits.push({ url, status: res.status() });
    }
  });

  const results = [];
  try {
    await login(page, app.baseUrl);
    await page.screenshot({
      path: path.join(app.outDir, 'after-login.png'),
      fullPage: true,
    });

    for (const route of routes) {
      apiHits.length = 0;
      await page.goto(`${app.baseUrl}${route.path}`, {
        waitUntil: 'domcontentloaded',
      });
      await sleep(5000);
      const bodyText = await page
        .locator('body')
        .innerText()
        .catch(() => '');
      const actualPath = await page.evaluate(
        () => location.pathname + location.search + location.hash,
      );
      const textHits = Object.fromEntries(
        route.expectTexts.map((t) => [t, bodyText.includes(t)]),
      );
      const shot = path.join(app.outDir, `${route.key}.png`);
      await page.screenshot({ path: shot, fullPage: true }).catch(() => {});

      const calendarInteract = { tried: false };
      const todayBtn = page.locator('button').filter({ hasText: /今.?天/ });
      if (await todayBtn.count()) {
        calendarInteract.tried = true;
        await todayBtn
          .first()
          .click()
          .catch(() => {});
        await sleep(800);
        const textAfter = await page.locator('body').innerText();
        calendarInteract.afterClickHasDayEvents =
          textAfter.includes('当天事项');
        const addNoteBtn = page
          .locator('button')
          .filter({ hasText: /添加备忘录/ });
        if (await addNoteBtn.count()) {
          await addNoteBtn
            .first()
            .click()
            .catch(() => {});
          await sleep(800);
          calendarInteract.noteModalOpened =
            (await page.locator(app.dialogSelector).count()) > 0 ||
            (await page.locator('body').innerText()).includes('新增备忘');
          await page.keyboard.press('Escape').catch(() => {});
        }
      }

      const matchedApis = (route.expectApis || []).map((apiPath) => {
        const hit = apiHits.find((item) => item.url.includes(apiPath));
        return {
          apiPath,
          hit: !!hit,
          status: hit?.status ?? null,
        };
      });

      results.push({
        app: app.key,
        route: route.path,
        actualPath,
        notFound:
          bodyText.includes('哎呀！未找到页面') ||
          bodyText.includes('页面不存在'),
        textHits,
        allExpectedFound: Object.values(textHits).every(Boolean),
        matchedApis,
        allApisOk: matchedApis.every((item) => item.hit && item.status === 200),
        calendarInteract,
        screenshot: shot,
        snippet: bodyText.slice(0, 300),
      });
    }
  } finally {
    await context.close();
    await browser.close();
  }

  const outFile = path.join(app.outDir, 'results.json');
  await fs.writeFile(outFile, JSON.stringify(results, null, 2));
  return { outFile, results };
}

const all = [];
for (const app of apps) {
  console.log(`\n=== probing ${app.key} @ ${app.baseUrl} ===`);
  const { outFile, results } = await probeApp(app);
  all.push(...results);
  console.log(JSON.stringify(results, null, 2));
  console.log(`Wrote ${outFile}`);
}

const summary = all.map((r) => ({
  app: r.app,
  route: r.route,
  allExpectedFound: r.allExpectedFound,
  allApisOk: r.allApisOk,
  noteModalOpened: r.calendarInteract?.noteModalOpened ?? null,
  notFound: r.notFound,
}));
console.log('\n=== SUMMARY ===');
console.log(JSON.stringify(summary, null, 2));
const failed = summary.filter(
  (r) => !r.allExpectedFound || !r.allApisOk || r.notFound,
);
process.exit(failed.length > 0 ? 1 : 0);
