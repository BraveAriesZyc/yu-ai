import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://localhost:5999';
const TAG = process.argv[3] || 'antdv-next';
const DIR = `output/playwright/pms-test/${TAG}`;
fs.mkdirSync(DIR, { recursive: true });

const results = [];
const record = (name, ok, extra = {}) => results.push({ name, ok, ...extra });

const browser = await chromium.launch({ headless: true });

async function login(context) {
  const page = await context.newPage();
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(1000);
  const tenantSelect = page.locator('.ant-select:has-text("请选择租户")');
  if (await tenantSelect.count()) {
    await tenantSelect.click();
    await sleep(1200);
    await page
      .locator('.ant-select-dropdown .ant-select-item-option')
      .first()
      .click();
    await sleep(500);
  }
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
    timeout: 30_000,
  });
  return page;
}

try {
  // 1. 未登录访问分享页：不应跳登录（应展示分享页或错误提示，而非登录框）
  const anonContext = await browser.newContext({
    viewport: { width: 1400, height: 900 },
  });
  const anonPage = await anonContext.newPage();
  await anonPage.goto(`${BASE}/pms/kb/document/share/not-a-real-token`, {
    waitUntil: 'domcontentloaded',
    timeout: 30_000,
  });
  await sleep(5000);
  const anonUrl = anonPage.url();
  record('A-分享页未登录不跳登录', !anonUrl.includes('/auth/login'), {
    url: anonUrl,
  });
  await anonPage.screenshot({ path: `${DIR}/v1-share-anonymous.png` });
  await anonContext.close();

  // 2. 登录态：项目分组路由不再 404
  const context = await browser.newContext({
    viewport: { width: 1680, height: 1000 },
  });
  const page = await login(context);
  await page.goto(`${BASE}/pms/pm/project/list/group`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  const groupUrl = page.url();
  const notFound = (await page.locator('text=未找到页面').count()) > 0;
  record('B-项目分组路由可达', !notFound, { url: groupUrl });
  await page.screenshot({ path: `${DIR}/v2-project-group-route.png` });

  // 3. 项目列表「更多」下拉菜单可弹出
  await page.goto(`${BASE}/pms/pm/project/list`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  const moreBtn = page.locator('table >> text=更多').first();
  if ((await moreBtn.count()) > 0) {
    await moreBtn.click();
    await sleep(1500);
    const menuItem = page.locator(
      '.ant-dropdown-menu-item, [role="menu"] [role="menuitem"]',
    );
    const menuCount = await menuItem.count();
    record('C-项目更多下拉弹出', menuCount > 0, { count: menuCount });
    await page.screenshot({ path: `${DIR}/v3-project-more-dropdown.png` });
    await page.keyboard.press('Escape');
  } else {
    // 卡片/列表内查找“更多”按钮的备选路径
    const altBtn = page.locator('text=更多').first();
    if ((await altBtn.count()) > 0) {
      await altBtn.click();
      await sleep(1500);
      const menuCount = await page
        .locator('.ant-dropdown-menu-item, [role="menu"] [role="menuitem"]')
        .count();
      record('C-项目更多下拉弹出', menuCount > 0, {
        count: menuCount,
        via: 'alt',
      });
      await page.screenshot({ path: `${DIR}/v3-project-more-dropdown.png` });
      await page.keyboard.press('Escape');
    } else {
      record('C-项目更多下拉弹出', false, { note: '未找到更多按钮' });
    }
  }

  // 4. 迭代详情「最近活动」时间线渲染
  await page.goto(`${BASE}/pms/pm/iteration/detail/2`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  const timelineItems = await page
    .locator('.ant-timeline-item, .el-timeline-item')
    .count();
  record('D-迭代详情时间线渲染', timelineItems > 0, { count: timelineItems });
  await page.screenshot({ path: `${DIR}/v4-iteration-timeline.png` });

  await context.close();
} catch (error) {
  record('fatal', false, { note: String(error).slice(0, 300) });
}

const summary = {
  tag: TAG,
  base: BASE,
  pass: results.filter((r) => r.ok).length,
  fail: results.filter((r) => !r.ok).length,
  results,
};
fs.writeFileSync(`${DIR}/verify-result.json`, JSON.stringify(summary, null, 2));
console.log(JSON.stringify(summary, null, 2));
await browser.close();
