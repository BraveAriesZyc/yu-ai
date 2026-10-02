/**
 * HRM 招聘候选人三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-candidate.mjs
 *
 * 验收：列表文案+status-count/page 接口、打开详情、Tab 切换
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-candidate/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-candidate/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-candidate/ele'),
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
      url.includes('/hrm/recruit/candidate/') ||
      url.includes('/hrm/recruit/interview/') ||
      url.includes('/hrm/operate-log/')
    ) {
      apiHits.push({ url, status: res.status() });
    }
  });

  const result = { app: app.key, list: null, detail: null };

  try {
    await login(page, app.baseUrl);
    apiHits.length = 0;
    await page.goto(`${app.baseUrl}/hrm/recruit/candidate`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(5000);
    const listText = await page
      .locator('body')
      .innerText()
      .catch(() => '');
    const listShot = path.join(app.outDir, 'list.png');
    await page.screenshot({ path: listShot, fullPage: true }).catch(() => {});

    const expectListTexts = ['候选人', '新增', '应聘职位'];
    const listTextHits = Object.fromEntries(
      expectListTexts.map((t) => [t, listText.includes(t)]),
    );
    const listApis = [
      '/hrm/recruit/candidate/page',
      '/hrm/recruit/candidate/status-count',
    ].map((apiPath) => {
      const hit = apiHits.find((item) => item.url.includes(apiPath));
      return { apiPath, hit: !!hit, status: hit?.status ?? null };
    });

    let detailOpened = false;
    apiHits.length = 0;
    const nameLink = page
      .locator(
        [
          '.vxe-body--row a',
          '.vxe-body--row .ant-btn-link',
          '.vxe-body--row .el-button.is-link',
        ].join(', '),
      )
      .first();
    if ((await nameLink.count()) > 0) {
      await Promise.all([
        page
          .waitForURL(/\/hrm\/recruit\/candidate\/detail\//, {
            timeout: 15_000,
          })
          .catch(() => {}),
        nameLink.click({ timeout: 10_000 }).catch(() => {}),
      ]);
      await sleep(3500);
      detailOpened = page.url().includes('/hrm/recruit/candidate/detail/');
    }

    if (detailOpened) {
      // 再等一轮接口
      await sleep(1500);
    } else {
      await page.goto(`${app.baseUrl}/hrm/recruit/candidate/detail/149`, {
        waitUntil: 'domcontentloaded',
        timeout: 60_000,
      });
      await sleep(3500);
      detailOpened = page.url().includes('/hrm/recruit/candidate/detail/');
    }

    const detailText = await page
      .locator('body')
      .innerText()
      .catch(() => '');
    const detailShot = path.join(app.outDir, 'detail.png');
    await page.screenshot({ path: detailShot, fullPage: true }).catch(() => {});

    let detailTabsOk = false;
    if (detailOpened) {
      const tabTexts = ['详细资料', '材料附件', '面试记录', '操作日志'];
      detailTabsOk = tabTexts.every((t) => detailText.includes(t));
      const interviewTab = page
        .locator('.ant-tabs-tab, .el-tabs__item')
        .filter({ hasText: '面试记录' });
      if (await interviewTab.count()) {
        await interviewTab
          .first()
          .click()
          .catch(() => {});
        await sleep(1500);
      }
    }

    const detailApis = ['/hrm/recruit/candidate/get'].map((apiPath) => {
      const hit = apiHits.find((item) => item.url.includes(apiPath));
      return { apiPath, hit: !!hit, status: hit?.status ?? null };
    });

    result.list = {
      path: '/hrm/recruit/candidate',
      textHits: listTextHits,
      allExpectedFound: Object.values(listTextHits).every(Boolean),
      matchedApis: listApis,
      allApisOk: listApis.every((item) => item.hit && item.status === 200),
      screenshot: listShot,
    };
    result.detail = {
      opened: detailOpened,
      detailTabsOk,
      matchedApis: detailApis,
      allApisOk: detailApis.every((item) => item.hit && item.status === 200),
      screenshot: detailShot,
      actualUrl: page.url(),
    };
  } finally {
    await context.close();
    await browser.close();
  }

  const outFile = path.join(app.outDir, 'results.json');
  await fs.writeFile(outFile, JSON.stringify(result, null, 2));
  return { outFile, result };
}

const all = [];
for (const app of apps) {
  console.log(`\n=== probing ${app.key} @ ${app.baseUrl} ===`);
  const { outFile, result } = await probeApp(app);
  all.push(result);
  console.log(JSON.stringify(result, null, 2));
  console.log(`Wrote ${outFile}`);
}

const summary = all.map((r) => ({
  app: r.app,
  listOk: !!r.list?.allExpectedFound && !!r.list?.allApisOk,
  detailOpened: !!r.detail?.opened,
  detailApisOk: !!r.detail?.allApisOk,
  detailTabsOk: !!r.detail?.detailTabsOk,
}));
console.log('\n=== SUMMARY ===');
console.log(JSON.stringify(summary, null, 2));
const failed = summary.filter(
  (r) => !r.listOk || !r.detailOpened || !r.detailApisOk,
);
process.exit(failed.length > 0 ? 1 : 0);
