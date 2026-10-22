/**
 * HRM 淘汰原因三端冒烟探针（antd 5666 / antdv-next 5999 / ele 5777）
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-eliminate.mjs
 *
 * 验收：列表文案+list 接口、点击新增出现空行输入框
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  {
    key: 'antd',
    baseUrl: 'http://127.0.0.1:5666',
    outDir: path.resolve('output/playwright/hrm-eliminate/antd'),
  },
  {
    key: 'antdv-next',
    baseUrl: 'http://127.0.0.1:5999',
    outDir: path.resolve('output/playwright/hrm-eliminate/antdv-next'),
  },
  {
    key: 'ele',
    baseUrl: 'http://127.0.0.1:5777',
    outDir: path.resolve('output/playwright/hrm-eliminate/ele'),
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
    if (url.includes('/hrm/recruit/config/eliminate-reason/')) {
      apiHits.push({ url, status: res.status() });
    }
  });

  const result = { app: app.key, list: null, addRow: null };

  try {
    await login(page, app.baseUrl);
    await page.goto(`${app.baseUrl}/hrm/recruit/settings/eliminate-reason`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(2500);
    await page.screenshot({
      path: path.join(app.outDir, 'list.png'),
      fullPage: true,
    });

    const bodyText = await page.locator('body').innerText();
    const hasTitle =
      bodyText.includes('原因列表') || bodyText.includes('淘汰原因');
    const listOk = apiHits.some(
      (h) =>
        h.url.includes('/hrm/recruit/config/eliminate-reason/list') &&
        h.status === 200,
    );
    result.list = {
      ok: hasTitle && listOk,
      hasTitle,
      listOk,
      apiHits: apiHits.slice(-8),
      url: page.url(),
    };

    const createBtn = page
      .locator('button:has-text("新增"), .vxe-button:has-text("新增")')
      .first();
    if (await createBtn.count()) {
      const beforeInputs = await page
        .locator('input[placeholder="请输入淘汰原因"]')
        .count();
      await createBtn.click();
      await sleep(500);
      const afterInputs = await page
        .locator('input[placeholder="请输入淘汰原因"]')
        .count();
      result.addRow = {
        ok: afterInputs >= beforeInputs && afterInputs > 0,
        beforeInputs,
        afterInputs,
      };
      await page.screenshot({
        path: path.join(app.outDir, 'after-add.png'),
        fullPage: true,
      });
    } else {
      result.addRow = { ok: false, reason: 'no create button' };
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
const failed = results.filter((r) => !r.list?.ok || !r.addRow?.ok || r.error);
process.exit(failed.length > 0 ? 1 : 0);
