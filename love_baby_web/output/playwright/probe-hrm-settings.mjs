/**
 * HRM 设置菜单 #20–#27 三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-settings.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-settings/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-settings/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-settings/ele'),
  },
];

/** @type {Array<{ id: string; paths: string[]; apiPattern: string; textHints: string[]; hasCreate?: boolean }>} */
const pages = [
  {
    id: '#20-group',
    paths: ['/hrm/salary/config/group'],
    apiPattern: '/hrm/salary/group/page',
    textHints: ['薪资组名称', '计薪标准', '计税规则'],
    hasCreate: true,
  },
  {
    id: '#20-tax-rule',
    paths: ['/hrm/salary/config/tax-rule'],
    apiPattern: '/hrm/salary/tax-rule/list',
    textHints: ['计税规则', '税率', '起征点'],
    hasCreate: true,
  },
  {
    id: '#21',
    paths: ['/hrm/salary/config/config'],
    apiPattern: '/hrm/salary/config/get',
    textHints: ['计薪', '社保月', '周期'],
    hasCreate: false,
  },
  {
    id: '#22',
    paths: ['/hrm/salary/config/option'],
    apiPattern: '/hrm/salary/option/list',
    textHints: ['企业可选项', '系统默认项', '薪资项', '同步标准薪资项'],
    hasCreate: false,
  },
  {
    id: '#23',
    paths: ['/hrm/salary/config/change-template'],
    apiPattern: '/hrm/salary/change-template/list',
    textHints: ['调薪模板', '模板名称'],
    hasCreate: true,
  },
  {
    id: '#24',
    paths: ['/hrm/insurance/config/scheme', '/hrm/insurance/scheme'],
    apiPattern: '/hrm/insurance/scheme/list',
    textHints: ['方案名称', '参保城市', '个人社保'],
    hasCreate: true,
  },
  {
    id: '#25-group',
    paths: ['/hrm/attendance/config/group'],
    apiPattern: '/hrm/attendance/group/page',
    textHints: ['考勤组', '班次', '打卡'],
    hasCreate: true,
  },
  {
    id: '#25-holiday',
    paths: ['/hrm/attendance/config/holiday'],
    apiPattern: '/hrm/attendance/holiday/page',
    textHints: ['节假日', '假期类型', '日期'],
    hasCreate: true,
  },
  {
    id: '#26-channel',
    paths: ['/hrm/recruit/settings/channel'],
    apiPattern: '/hrm/recruit/channel/page',
    textHints: ['渠道', '招聘渠道'],
    hasCreate: true,
  },
  {
    id: '#26-eliminate',
    paths: ['/hrm/recruit/settings/eliminate-reason'],
    apiPattern: '/hrm/recruit/config/eliminate-reason/list',
    textHints: ['原因列表', '淘汰原因'],
    hasCreate: false,
  },
  {
    id: '#27',
    paths: ['/hrm/employee/config'],
    apiPattern: 'admin-api/hrm/employee/config/create-field/list',
    textHints: ['新建员工字段设置', '员工档案设置', '字段名称'],
    hasCreate: false,
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

async function probePage(page, app, spec) {
  const apiHits = [];
  const onResponse = (res) => {
    const url = res.url();
    if (url.includes(spec.apiPattern)) {
      apiHits.push({ url, status: res.status() });
    }
  };
  page.on('response', onResponse);

  let navigatedPath = null;
  let bodyText = '';
  let pageOk = false;
  let hasTitle = false;

  for (const routePath of spec.paths) {
    apiHits.length = 0;
    await page.goto(`${app.baseUrl}${routePath}`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(3500);
    bodyText = await page.locator('body').innerText();
    const not404 =
      !bodyText.includes('404') &&
      !bodyText.includes('页面不存在') &&
      !bodyText.includes('Not Found');
    hasTitle = spec.textHints.some((hint) => bodyText.includes(hint));
    pageOk = apiHits.some((h) => h.status === 200);
    if (not404 && (hasTitle || pageOk)) {
      navigatedPath = routePath;
      break;
    }
  }

  page.off('response', onResponse);

  const safeName = spec.id.replaceAll(/[^a-z0-9-]/gi, '_');
  if (navigatedPath) {
    await page.screenshot({
      path: path.join(app.outDir, `${safeName}.png`),
      fullPage: true,
    });
  }

  let createModal = null;
  if (spec.hasCreate && navigatedPath) {
    const createBtn = page
      .locator(
        'button:has-text("新增"), .vxe-button:has-text("新增"), button:has-text("新建"), .vxe-button:has-text("新建")',
      )
      .first();
    if (await createBtn.count()) {
      await createBtn.click();
      await sleep(1000);
      const modalText = await page.locator('body').innerText();
      createModal = { ok: modalText.length > bodyText.length / 2 };
      const cancel = page
        .locator(
          'button:has-text("取消"), button:has-text("取 消"), .ant-modal-close, .el-dialog__headerbtn',
        )
        .first();
      if (await cancel.count()) await cancel.click();
    } else {
      createModal = { ok: false, reason: 'no create button' };
    }
  }

  return {
    id: spec.id,
    path: navigatedPath,
    ok: !!navigatedPath && hasTitle && pageOk,
    hasTitle,
    pageOk,
    createModal,
    apiHits: apiHits.slice(-5),
  };
}

async function probeApp(app) {
  await fs.mkdir(app.outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const result = { app: app.key, pages: [], error: null };

  try {
    await login(page, app.baseUrl);
    for (const spec of pages) {
      // eslint-disable-next-line no-await-in-loop
      result.pages.push(await probePage(page, app, spec));
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

const matrix = {};
for (const spec of pages) {
  matrix[spec.id] = {};
  for (const r of results) {
    const p = r.pages.find((x) => x.id === spec.id);
    matrix[spec.id][r.app] = p
      ? {
          ok: p.ok,
          path: p.path,
          create: p.createModal?.ok ?? 'n/a',
        }
      : { ok: false, error: r.error };
  }
}
console.log('\n=== MATRIX ===');
console.log(JSON.stringify(matrix, null, 2));

const failed = results.flatMap((r) =>
  r.error
    ? [{ app: r.app, id: 'login', ok: false }]
    : r.pages.filter((p) => !p.ok),
);
process.exit(failed.length > 0 ? 1 : 0);
