import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-dept/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-dept/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-dept/ele'),
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
      url.includes('/system/dept/') ||
      url.includes('/hrm/employee/dept-statistics') ||
      url.includes('/hrm/employee/page')
    ) {
      apiHits.push({ url, status: res.status() });
    }
  });

  const result = {
    app: app.key,
    list: null,
    detail: null,
  };

  try {
    await login(page, app.baseUrl);

    apiHits.length = 0;
    await page.goto(`${app.baseUrl}/hrm/dept`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(4500);
    const listText = await page
      .locator('body')
      .innerText()
      .catch(() => '');
    const listShot = path.join(app.outDir, 'list.png');
    await page.screenshot({ path: listShot, fullPage: true }).catch(() => {});

    const expectListTexts = [
      '组织列表',
      '部门名称',
      '在职员工',
      '全职员工',
      '非全职人数',
      '新建部门',
      '人数格式为：直属人数（包含下级部门人数）',
    ];
    const listTextHits = Object.fromEntries(
      expectListTexts.map((t) => [t, listText.includes(t)]),
    );
    const listApis = [
      '/system/dept/simple-list',
      '/hrm/employee/dept-statistics',
    ].map((apiPath) => {
      const hit = apiHits.find((item) => item.url.includes(apiPath));
      return { apiPath, hit: !!hit, status: hit?.status ?? null };
    });

    // 打开「研发部门」详情（antd: a/ant-btn-link；ele: el-button.is-link）
    let detailOpened = false;
    const nameLink = page
      .locator(
        [
          '.vxe-body--row a',
          '.vxe-body--row .ant-btn-link',
          '.vxe-body--row .el-button.is-link',
        ].join(', '),
      )
      .filter({ hasText: '研发部门' })
      .first();
    if ((await nameLink.count()) > 0) {
      await Promise.all([
        page
          .waitForURL(/\/hrm\/dept\/detail\//, { timeout: 15_000 })
          .catch(() => {}),
        nameLink.click({ timeout: 10_000 }),
      ]);
      await sleep(2000);
      detailOpened = page.url().includes('/hrm/dept/detail/');
    }
    // 兜底：直接跳转种子数据「研发部门」id=100
    if (!detailOpened) {
      await page.goto(`${app.baseUrl}/hrm/dept/detail/100`, {
        waitUntil: 'domcontentloaded',
        timeout: 60_000,
      });
      await sleep(2500);
      detailOpened = page.url().includes('/hrm/dept/detail/');
    }

    const detailText = await page
      .locator('body')
      .innerText()
      .catch(() => '');
    const detailShot = path.join(app.outDir, 'detail.png');
    await page.screenshot({ path: detailShot, fullPage: true }).catch(() => {});

    // switch to employee tab if visible
    let employeeTabOk = false;
    const empTab = page
      .locator('.ant-tabs-tab, .el-tabs__item')
      .filter({ hasText: '员工列表' });
    if (await empTab.count()) {
      await empTab
        .first()
        .click()
        .catch(() => {});
      await sleep(2500);
      const after = await page.locator('body').innerText();
      employeeTabOk =
        after.includes('员工搜索') ||
        after.includes('员工姓名') ||
        after.includes('暂无数据') ||
        apiHits.some((item) => item.url.includes('/hrm/employee/page'));
    }

    const detailApis = [
      '/system/dept/get',
      '/system/dept/simple-list',
      '/hrm/employee/dept-statistics',
    ].map((apiPath) => {
      const hit = apiHits.find((item) => item.url.includes(apiPath));
      return { apiPath, hit: !!hit, status: hit?.status ?? null };
    });

    result.list = {
      path: '/hrm/dept',
      actualPath: await page.evaluate(() => location.pathname),
      textHits: listTextHits,
      allExpectedFound: Object.values(listTextHits).every(Boolean),
      matchedApis: listApis,
      allApisOk: listApis.every((item) => item.hit && item.status === 200),
      screenshot: listShot,
    };
    result.detail = {
      opened: detailOpened,
      employeeTabOk,
      textHasDetails: detailText.includes('详细资料'),
      textHasEmployees: detailText.includes('员工列表'),
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
  employeeTabOk: !!r.detail?.employeeTabOk,
}));
console.log('\n=== SUMMARY ===');
console.log(JSON.stringify(summary, null, 2));
const failed = summary.filter(
  (r) => !r.listOk || !r.detailOpened || !r.detailApisOk,
);
process.exit(failed.length > 0 ? 1 : 0);
