import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://localhost:5666';
const TAG = process.argv[3] || 'antd';
const DIR = `output/playwright/pms-test/${TAG}`;
fs.mkdirSync(DIR, { recursive: true });

const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(String(e).slice(0, 150)));

try {
  await page.goto(`${BASE}/auth/login`, {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  });
  await sleep(1500);
  const t = page.locator('.ant-select:has-text("请选择租户")');
  if (await t.count()) {
    await t.click();
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

  // 项目列表取第一个真实项目
  await page.goto(`${BASE}/pms/pm/project/list`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  await page.locator('table .truncate').first().click();
  await sleep(4000);
  const m = page.url().match(/project\/detail\/(\d+)/);
  if (!m) throw new Error('未找到项目');
  const projectId = m[1];

  // 项目设置 → 成员 → 新增成员 → 人员选择弹窗
  await page.goto(`${BASE}/pms/pm/project/config/${projectId}`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4500);
  await page
    .locator('[role="tab"]:has-text("成员"), .el-tabs__item:has-text("成员")')
    .first()
    .click();
  await sleep(3000);
  await page.locator('button:has-text("新增成员")').first().click();
  await sleep(2000);
  await page.locator('input[placeholder="请选择需要加入项目的用户"]').click();
  await sleep(4500);

  const rows = await page.evaluate(() =>
    [...document.querySelectorAll('.vxe-body--row')].map((r) => ({
      text: r.innerText.replaceAll(/\s+/g, ' ').slice(0, 50),
      disabled: !!r.querySelector('.is--disabled, .is-disabled'),
    })),
  );
  const disabledCount = rows.filter((r) => r.disabled).length;
  await page.screenshot({ path: `${DIR}/v5-member-select-disabled.png` });
  console.log(
    JSON.stringify({
      tag: TAG,
      projectId,
      totalRows: rows.length,
      disabledCount,
      disabledRows: rows.filter((r) => r.disabled).map((r) => r.text),
      pageErrors: pageErrors.slice(0, 2),
      ok: disabledCount > 0 && pageErrors.length === 0,
    }),
  );
} catch (error) {
  console.log(
    JSON.stringify({ tag: TAG, ok: false, fatal: String(error).slice(0, 200) }),
  );
}
await browser.close();
