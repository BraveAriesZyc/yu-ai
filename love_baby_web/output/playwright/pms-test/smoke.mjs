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
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
const pageErrors = [];
page.on('pageerror', (e) =>
  pageErrors.push(
    `${page.url().replace(BASE, '')} :: ${(e.stack || e.message).slice(0, 300)}`,
  ),
);
const consoleErrors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 200));
});

async function shot(name) {
  await page.screenshot({ path: `${DIR}/${name}.png`, fullPage: false });
}

async function visitPage(name, path, opts = {}) {
  const { wait = 4000, check, screenshot = true } = opts;
  try {
    await page.goto(`${BASE}${path}`, {
      waitUntil: 'domcontentloaded',
      timeout: 30_000,
    });
    await sleep(wait);
    const url = page.url();
    if (url.includes('/auth/login')) {
      record(name, false, { note: '被重定向到登录页', path });
      return false;
    }
    let ok = true;
    let note = '';
    if (check) {
      const found = await page.locator(check).first().count();
      ok = found > 0;
      note = ok ? '' : `未找到元素: ${check}`;
    }
    if (screenshot) await shot(name);
    record(name, ok, { path, note });
    return ok;
  } catch (error) {
    record(name, false, { path, note: String(error).slice(0, 200) });
    return false;
  }
}

try {
  // 登录
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
  record('00-登录', true);

  // ===== PM =====
  await visitPage('01-工作台', '/pms/pm/workbench', {
    wait: 3500,
    check: 'text=全部事项',
  });
  await visitPage('02-项目列表', '/pms/pm/project/list', {
    wait: 3500,
    check: 'text=星标项目',
  });

  // 项目详情：点击列表第一个项目名称
  const projectName = page
    .locator('.el-table__row .truncate, table .truncate')
    .first();
  const hasProject = (await projectName.count()) > 0;
  if (hasProject) {
    await projectName.click();
    await sleep(4000);
    record('03-项目详情-打开', page.url().includes('/pms/pm/project/detail/'), {
      path: page.url(),
    });
    await shot('03-项目详情-任务');
    // 切换到 项目概况
    const overviewTab = page.locator('[role="tab"]:has-text("项目概况")');
    if (await overviewTab.count()) {
      await overviewTab.click();
      await sleep(3000);
      await shot('04-项目详情-概况');
      record(
        '04-项目详情-概况',
        (await page.locator('text=项目基本信息').count()) > 0,
      );
    }
    // 切换到 甘特图
    const ganttTab = page.locator('[role="tab"]:has-text("甘特图")');
    if (await ganttTab.count()) {
      await ganttTab.click();
      await sleep(3000);
      await shot('05-项目详情-甘特图');
      record('05-项目详情-甘特图', true);
    }
  } else {
    record('03-项目详情-打开', false, { note: '项目列表为空' });
  }

  // 工作项详情抽屉：回到工作台点击第一条工作项
  await page.goto(`${BASE}/pms/pm/workbench`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(3500);
  const workItemLink = page
    .locator('table .ant-btn-link, table .el-button.is-link, table .el-link')
    .first();
  if ((await workItemLink.count()) > 0) {
    await workItemLink.click();
    await sleep(3500);
    await shot('06-工作项详情抽屉');
    const drawerText = await page.locator('text=活动日志').count();
    record('06-工作项详情抽屉', drawerText > 0);
    await page.keyboard.press('Escape');
    await sleep(500);
  } else {
    record('06-工作项详情抽屉', false, { note: '工作台无工作项' });
  }

  await visitPage('07-项目模板', '/pms/pm/project/template', {
    check: 'table',
  });
  await visitPage('08-项目归档', '/pms/pm/project/archive', { check: 'table' });
  await visitPage('09-项目回收站', '/pms/pm/project/recycle', {
    check: 'table',
  });

  // ===== KB =====
  await visitPage('10-知识库列表', '/pms/kb/library', {
    wait: 3500,
    check: 'text=新建知识库',
  });

  // 知识库文档中心：点击第一个知识库
  const libraryLink = page
    .locator('table .ant-btn-link, table .el-button.is-link, table .el-link')
    .first();
  if ((await libraryLink.count()) > 0) {
    await libraryLink.click();
    await sleep(4000);
    record('11-知识库文档中心', page.url().includes('/pms/kb/library/'), {
      path: page.url(),
    });
    await shot('11-知识库文档中心');
  } else {
    record('11-知识库文档中心', false, { note: '知识库列表为空' });
  }

  await visitPage('12-知识库模板', '/pms/kb/library-template', {
    wait: 5000,
    check: 'table',
  });
  await visitPage('13-文档标签', '/pms/kb/label', { check: 'text=文档标签' });
  await visitPage('14-我的收藏', '/pms/kb/favorite', {
    check: 'text=所属知识库',
  });
  await visitPage('15-最近浏览', '/pms/kb/recent', {
    check: 'text=最近 30 天',
  });
  await visitPage('16-文档搜索', '/pms/kb/search', { check: 'text=关键字' });
  await visitPage('17-知识库回收站', '/pms/kb/recycle', { check: 'table' });
} catch (error) {
  record('fatal', false, { note: String(error).slice(0, 300) });
}

record('zz-页面JS错误', pageErrors.length === 0, {
  count: pageErrors.length,
  items: pageErrors.slice(0, 5),
});

const summary = {
  tag: TAG,
  base: BASE,
  pass: results.filter((r) => r.ok).length,
  fail: results.filter((r) => !r.ok).length,
  results,
  consoleErrors: consoleErrors.slice(0, 10),
};
fs.writeFileSync(`${DIR}/result.json`, JSON.stringify(summary, null, 2));
console.log(
  JSON.stringify(
    {
      tag: TAG,
      pass: summary.pass,
      fail: summary.fail,
      failed: results.filter((r) => !r.ok).map((r) => r.name),
    },
    null,
    2,
  ),
);
await browser.close();
