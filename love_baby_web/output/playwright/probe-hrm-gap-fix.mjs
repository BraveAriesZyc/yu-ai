/**
 * HRM 差距修复冒烟：绩效计划详情日志、参保方案、考勤组
 * 用法：cd yudao-ui-admin-vben && node output/playwright/probe-hrm-gap-fix.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const apps = [
  { key: 'antd', baseUrl: 'http://127.0.0.1:5666' },
  { key: 'antdv-next', baseUrl: 'http://127.0.0.1:5999' },
  { key: 'ele', baseUrl: 'http://127.0.0.1:5777' },
];

const outRoot = path.resolve('output/playwright/hrm-gap-fix');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(page, baseUrl) {
  await page.goto(`${baseUrl}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(800);
  const loginBtn = page.locator('button[aria-label="login"]');
  if (await loginBtn.count()) await loginBtn.click();
  else {
    await page.locator('input[type="password"]').first().fill('admin123');
    await page
      .locator('button:has-text("登录"), button[type="submit"]')
      .first()
      .click();
  }
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
    timeout: 45_000,
  });
  await sleep(1200);
}

async function waitText(page, hints, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const text = await page.locator('body').innerText();
    if (hints.some((h) => text.includes(h))) return text;
    await sleep(800);
  }
  return page.locator('body').innerText();
}

async function probeApp(app) {
  const outDir = path.join(outRoot, app.key);
  await fs.mkdir(outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
  });
  const apiHits = [];
  page.on('response', (res) => {
    const url = res.url();
    if (url.includes('/admin-api/hrm/')) {
      apiHits.push({ url, status: res.status() });
    }
  });
  const result = { app: app.key, checks: {} };

  try {
    await login(page, app.baseUrl);

    // #16 plan list
    await page.goto(`${app.baseUrl}/hrm/performance/plan`, {
      waitUntil: 'domcontentloaded',
    });
    let text = await waitText(page, [
      'KPI',
      '考核计划',
      '未开始',
      '进行中',
      '新增考核计划',
      '计划名称',
    ]);
    const planListOk =
      (text.includes('考核') ||
        text.includes('KPI') ||
        text.includes('计划名称') ||
        text.includes('未开始')) &&
      apiHits.some(
        (h) => h.url.includes('/performance/plan/') && h.status === 200,
      );
    result.checks.planList = { ok: planListOk };
    await page.screenshot({
      path: path.join(outDir, 'plan-list.png'),
      fullPage: true,
    });

    // open first plan detail if link exists
    const planLink = page.locator('a.text-primary, .vxe-body--row a').first();
    if (await planLink.count()) {
      await planLink.click();
      await sleep(2500);
      text = await waitText(page, ['操作日志', '详细资料', '参评员工', '启动']);
      const detailOk =
        page.url().includes('/hrm/performance/plan') &&
        (text.includes('操作日志') ||
          text.includes('详细资料') ||
          text.includes('参评'));
      result.checks.planDetail = {
        ok: detailOk,
        hasOperateLog: text.includes('操作日志'),
        url: page.url(),
      };
      await page.screenshot({
        path: path.join(outDir, 'plan-detail.png'),
        fullPage: true,
      });
    } else {
      result.checks.planDetail = { ok: false, reason: 'no plan row' };
    }

    // #24 scheme
    await page.goto(`${app.baseUrl}/hrm/insurance/config/scheme`, {
      waitUntil: 'domcontentloaded',
    });
    text = await waitText(page, [
      '参保方案',
      '方案名称',
      '新建参保方案',
      '参保城市',
    ]);
    const schemeOk =
      (text.includes('方案') ||
        text.includes('参保') ||
        text.includes('新建')) &&
      apiHits.some(
        (h) =>
          (h.url.includes('/hrm/insurance/scheme/list') ||
            h.url.includes('/hrm/insurance/scheme/page') ||
            h.url.includes('/hrm/insurance/scheme/simple-list')) &&
          h.status === 200,
      );
    result.checks.schemeList = {
      ok: schemeOk,
      textHit: text.includes('方案') || text.includes('参保'),
    };
    const createBtn = page
      .locator(
        'button:has-text("新建参保方案"), button:has-text("新增"), .vxe-button:has-text("新建")',
      )
      .first();
    if (await createBtn.count()) {
      await createBtn.click();
      await sleep(1800);
      text = await page.locator('body').innerText();
      result.checks.schemeCreate = {
        ok:
          text.includes('参保城市') ||
          text.includes('标准参保') ||
          text.includes('养老保险') ||
          text.includes('公积金') ||
          text.includes('项目'),
      };
      await page.screenshot({
        path: path.join(outDir, 'scheme-create.png'),
        fullPage: true,
      });
      await page.keyboard.press('Escape');
      await sleep(400);
    } else {
      result.checks.schemeCreate = { ok: false, reason: 'no create' };
    }

    // #25 attendance group
    await page.goto(`${app.baseUrl}/hrm/attendance/config/group`, {
      waitUntil: 'domcontentloaded',
    });
    text = await waitText(page, ['考勤组', '新增']);
    const groupOk =
      text.includes('考勤组') &&
      apiHits.some(
        (h) => h.url.includes('/hrm/attendance/group/page') && h.status === 200,
      );
    result.checks.groupList = { ok: groupOk };
    const groupCreate = page.locator('button:has-text("新增")').first();
    if (await groupCreate.count()) {
      await groupCreate.click();
      await sleep(1500);
      text = await page.locator('body').innerText();
      result.checks.groupCreate = {
        ok:
          text.includes('特殊日期') ||
          text.includes('定位打卡') ||
          text.includes('WiFi') ||
          text.includes('扣款') ||
          text.includes('班次'),
      };
      await page.screenshot({
        path: path.join(outDir, 'group-create.png'),
        fullPage: true,
      });
      await page.keyboard.press('Escape');
    } else {
      result.checks.groupCreate = { ok: false, reason: 'no create' };
    }
  } catch (error) {
    result.error = String(error);
  } finally {
    await browser.close();
  }

  await fs.writeFile(
    path.join(outDir, 'results.json'),
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
const failed = results.filter((r) => {
  if (r.error) return true;
  return Object.values(r.checks || {}).some((c) => !c?.ok);
});
process.exit(failed.length > 0 ? 1 : 0);
