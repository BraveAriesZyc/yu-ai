import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://localhost:5999';
const TAG = process.argv[3] || 'antdv-next';
const out = { tag: TAG, steps: {}, failures: [] };
const step = (name, ok, extra) => {
  out.steps[name] = { ok, ...extra };
  if (!ok) out.failures.push(name);
};

const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(e.message.slice(0, 150)));

try {
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(800);
  // antd 端登录需要先选租户
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

  await page.goto(`${BASE}/fms/voucher/create`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4500);

  // 凭证号已自动获取
  const number1 = await page
    .locator('input.ant-input-number-input, .ant-input-number input')
    .first()
    .inputValue();
  step('凭证号自动获取', Number(number1) > 0, { number: number1 });

  // 第一行：摘要 + 科目 100101 + 借方 100
  await page.locator('.entry-digest input').nth(0).fill('功能验收测试凭证');
  await page.locator('.entry-subject .ant-select').nth(0).click();
  await sleep(800);
  await page.keyboard.type('100101');
  await sleep(800);
  await page.keyboard.press('Enter');
  await sleep(500);
  await page
    .locator('td[data-entry-direction="debit"] input')
    .nth(0)
    .fill('100');
  await page
    .locator('td[data-entry-direction="debit"] input')
    .nth(0)
    .press('Enter');
  await sleep(500);

  // 第二行：科目 1002 + 贷方 100
  await page.locator('.entry-subject .ant-select').nth(1).click();
  await sleep(800);
  await page.keyboard.type('1002');
  await sleep(800);
  await page.keyboard.press('Enter');
  await sleep(500);
  await page
    .locator('td[data-entry-direction="credit"] input')
    .nth(1)
    .fill('100');
  await page
    .locator('td[data-entry-direction="credit"] input')
    .nth(1)
    .press('Enter');
  await sleep(800);

  const summary = await page.evaluate(
    () => document.body.innerText.match(/合计：[^\n]*/)?.[0] ?? '',
  );
  step('合计大写', summary.includes('壹佰元整'), { summary });

  await page.screenshot({
    path: `output/playwright/fms-fix/func-${TAG}-filled.png`,
  });

  // 保存
  await page.locator('button:has-text("保 存")').first().click();
  await sleep(2500);
  const bodyText = await page.evaluate(() => document.body.innerText);
  const saved = bodyText.includes('新增成功') || bodyText.includes('保存成功');
  step('保存凭证', saved, {});
  await page.screenshot({
    path: `output/playwright/fms-fix/func-${TAG}-saved.png`,
  });

  // 查凭证列表验证
  await page.goto(`${BASE}/fms/voucher/list`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  const listText = await page.evaluate(() => document.body.innerText);
  const inList = listText.includes('功能验收测试凭证');
  step('列表出现新凭证', inList, {});
  await page.screenshot({
    path: `output/playwright/fms-fix/func-${TAG}-list.png`,
  });

  // 删除清理（行内 删除 → 确认）
  if (inList) {
    const row = page.locator('tr:has-text("功能验收测试凭证")').first();
    await row
      .locator('a:has-text("删除"), button:has-text("删除")')
      .first()
      .click();
    await sleep(1000);
    await page
      .locator(
        '.ant-popconfirm button.ant-btn-primary, .ant-popover button.ant-btn-primary, button:has-text("确 定"), button:has-text("确定")',
      )
      .first()
      .click()
      .catch(() => null);
    await sleep(2000);
    const afterDelete = await page.evaluate(() => document.body.innerText);
    step('删除清理', !afterDelete.includes('功能验收测试凭证'), {});
  }
} catch (error) {
  out.failures.push(`exception: ${error.message.slice(0, 200)}`);
}
out.pageErrors = pageErrors.slice(0, 5);
console.log(JSON.stringify(out, null, 2));
await browser.close();
