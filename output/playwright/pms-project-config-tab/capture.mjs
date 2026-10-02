import { chromium } from 'playwright';
import fs from 'node:fs';

const outputDir = 'output/playwright/pms-project-config-tab';
fs.mkdirSync(outputDir, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  colorScheme: 'light',
  viewport: { width: 1680, height: 1050 },
});
const page = await context.newPage();

await page.goto('http://127.0.0.1:5667/auth/login', {
  waitUntil: 'domcontentloaded',
});
const tenantSelect = page.locator('.ant-select:has-text("请选择租户")');
if (await tenantSelect.count()) {
  await tenantSelect.click();
  await page
    .locator('.ant-select-dropdown .ant-select-item-option')
    .first()
    .click();
}
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
  timeout: 30_000,
});

const tabLabels = page.locator('.tabs-chrome__item-main span');
const countTopTab = async (title) =>
  (await tabLabels.allTextContents()).filter((text) => text === title).length;

// 从无查询参数的菜单地址进入，页面补默认范围后仍应只保留一个“我的项目”页签
await page.goto('http://127.0.0.1:5667/pms/pm/project/list', {
  waitUntil: 'domcontentloaded',
});
await page.getByText('星标项目', { exact: true }).first().waitFor();
const projectListInitialCount = await countTopTab('我的项目');

// 连续切换项目范围，不应按查询参数新增应用页签
for (const tabName of ['全部项目', '我负责的', '我参与的']) {
  await page.getByRole('tab', { name: tabName, exact: true }).click();
  await page.waitForTimeout(100);
}
const projectListAfterSceneChangeCount = await countTopTab('我的项目');

// 分别打开两个项目的设置页，第二次应复用第一次的页签
const moreButtons = page.getByRole('button', { name: '更多', exact: true });
await moreButtons.first().click();
await page.getByRole('menuitem', { name: '项目设置', exact: true }).click();
await page.getByRole('heading', { name: '项目设置', exact: true }).waitFor();
const firstProjectConfigUrl = page.url();
const firstProjectConfigCount = await countTopTab('项目设置');

await page
  .locator('.tabs-chrome__item-main')
  .filter({ hasText: '我的项目' })
  .click();
await page.getByText('星标项目', { exact: true }).first().waitFor();
await moreButtons.nth(1).click();
await page.getByRole('menuitem', { name: '项目设置', exact: true }).click();
await page.getByRole('heading', { name: '项目设置', exact: true }).waitFor();
const secondProjectConfigUrl = page.url();
const secondProjectConfigCount = await countTopTab('项目设置');

// 设置页内部查询参数切换也应复用同一个应用页签
await page.getByRole('tab', { name: '项目公告', exact: true }).click();
await page.waitForURL((url) => url.searchParams.get('tabs') === 'announcement');
const afterInternalTabChangeCount = await countTopTab('项目设置');

await page.screenshot({
  path: `${outputDir}/project-config-single-tab-headless.png`,
});

const result = {
  afterInternalTabChangeCount,
  firstProjectConfigCount,
  firstProjectConfigUrl,
  projectListAfterSceneChangeCount,
  projectListInitialCount,
  secondProjectConfigCount,
  secondProjectConfigUrl,
  topTabs: await tabLabels.allTextContents(),
};
fs.writeFileSync(`${outputDir}/result.json`, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));

await browser.close();
