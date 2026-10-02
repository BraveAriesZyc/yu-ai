/**
 * HRM 员工管理三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-employee.mjs
 *
 * 验收：列表文案+page/status-count、打开新增弹窗、进入详情
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-employee/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-employee/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-employee/ele'),
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

async function waitForListReady(page, apiHits) {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const hit = apiHits.some(
      (h) => h.url.includes('/admin-api/hrm/employee/page') && h.status === 200,
    );
    const text = await page.locator('body').innerText();
    if (
      (hit || text.includes('员工姓名') || text.includes('全职')) &&
      text.includes('新增')
    ) {
      return true;
    }
    await sleep(1000);
  }
  return false;
}

async function probeApp(app) {
  await fs.mkdir(app.outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  const apiHits = [];
  let firstEmployeeId;
  page.on('response', async (res) => {
    const url = res.url();
    if (!url.includes('/hrm/employee/')) return;
    apiHits.push({ url, status: res.status() });
    if (
      url.includes('/admin-api/hrm/employee/page') &&
      res.status() === 200 &&
      !firstEmployeeId
    ) {
      try {
        const json = await res.json();
        firstEmployeeId = json?.data?.list?.[0]?.id;
      } catch {
        /* ignore */
      }
    }
  });

  const result = {
    app: app.key,
    list: null,
    createModal: null,
    detail: null,
  };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/employee/list`, {
      waitUntil: 'domcontentloaded',
    });
    await waitForListReady(page, apiHits);
    await sleep(800);
    await page.screenshot({
      path: path.join(app.outDir, 'list.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasTitle =
      bodyText.includes('员工姓名') ||
      bodyText.includes('全职') ||
      bodyText.includes('在职');
    const pageOk = apiHits.some(
      (h) => h.url.includes('/hrm/employee/page') && h.status === 200,
    );
    const countOk = apiHits.some(
      (h) => h.url.includes('/hrm/employee/status-count') && h.status === 200,
    );
    result.list = {
      ok: hasTitle && pageOk,
      hasTitle,
      pageOk,
      countOk,
      url: page.url(),
      firstEmployeeId,
      apiHits: apiHits.slice(-8),
    };

    const createBtn = page
      .locator('button:has-text("新增"), .vxe-button:has-text("新增")')
      .first();
    if (await createBtn.count()) {
      await createBtn.click();
      await sleep(1500);
      await page.screenshot({
        path: path.join(app.outDir, 'create-modal.png'),
        fullPage: true,
      });
      const modalText = await page.locator('body').innerText();
      result.createModal = {
        ok:
          modalText.includes('员工姓名') ||
          modalText.includes('个人信息') ||
          modalText.includes('入职信息') ||
          modalText.includes('新增员工'),
      };
      await page.keyboard.press('Escape');
      await sleep(300);
      await page.keyboard.press('Escape');
      await sleep(500);
      const cancel = page.locator('button:has-text("取消")').last();
      if (await cancel.count()) {
        await cancel.click({ force: true }).catch(() => undefined);
        await sleep(400);
      }
    } else {
      result.createModal = { ok: false, reason: 'no create button' };
    }

    if (firstEmployeeId) {
      await page.goto(`${app.baseUrl}/hrm/employee/detail/${firstEmployeeId}`, {
        waitUntil: 'domcontentloaded',
      });
      await sleep(2500);
      const detailUrl = page.url();
      const detailText = await page.locator('body').innerText();
      result.detail = {
        ok:
          detailUrl.includes('/hrm/employee/detail/') &&
          (detailText.includes('岗位信息') ||
            detailText.includes('基本信息') ||
            detailText.includes('操作记录') ||
            detailText.includes('材料附件')),
        url: detailUrl,
        getOk: apiHits.some(
          (h) =>
            h.url.includes('/hrm/employee/get') &&
            h.status === 200 &&
            !h.url.includes('template'),
        ),
      };
      await page.screenshot({
        path: path.join(app.outDir, 'detail.png'),
        fullPage: true,
      });
    } else {
      result.detail = { ok: false, reason: 'no employee id from page api' };
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
  (r) => !r.list?.ok || !r.createModal?.ok || !r.detail?.ok || r.error,
);
process.exit(failed.length > 0 ? 1 : 0);
