import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://localhost:5777';
const out = { tag: 'ele', steps: {}, failures: [] };
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
  await sleep(1000);
  await page
    .locator('button[aria-label="login"], button:has-text("登录")')
    .first()
    .click();
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
    timeout: 30_000,
  });

  await page.goto(`${BASE}/fms/voucher/create`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(5000);

  const number1 = await page
    .locator('input')
    .nth(1)
    .inputValue()
    .catch(() => '');
  const numberAlt = await page.evaluate(() => {
    const row = [...document.querySelectorAll('span')].find(
      (s) => s.innerText.trim() === '凭证字',
    );
    const wrap = row?.closest('div');
    return wrap ? wrap.innerText.replaceAll('\n', '|').slice(0, 40) : '';
  });
  step('凭证号自动获取', Number(number1) > 0 || /\d+/.test(numberAlt), {
    number: number1,
    alt: numberAlt,
  });

  await page.locator('.entry-digest input').nth(0).fill('功能验收测试凭证');
  await page.locator('.entry-subject .el-select').nth(0).click();
  await sleep(800);
  await page.keyboard.type('100101');
  await sleep(800);
  await page
    .locator('.el-select-dropdown__item:has-text("100101 人民币现金"):visible')
    .first()
    .click();
  await sleep(600);
  await page
    .locator('td[data-entry-direction="debit"] input')
    .nth(0)
    .fill('100');
  await page
    .locator('td[data-entry-direction="debit"] input')
    .nth(0)
    .press('Enter');
  await sleep(500);

  await page.locator('.entry-subject .el-select').nth(1).click();
  await sleep(800);
  await page.keyboard.type('1002');
  await sleep(800);
  await page
    .locator('.el-select-dropdown__item:has-text("1002 银行存款"):visible')
    .first()
    .click();
  await sleep(600);
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
    path: 'output/playwright/fms-fix/func-ele-filled.png',
  });

  // 科目回显确认
  const subjectsShown = await page.evaluate(() =>
    [...document.querySelectorAll('.entry-subject')]
      .slice(0, 2)
      .map((c) => c.innerText.trim().slice(0, 30)),
  );
  step(
    '科目回显',
    subjectsShown.every((s) => s.length > 0),
    { subjectsShown },
  );
  await page
    .locator('button')
    .filter({ hasText: /^保\s*存$/ })
    .first()
    .click();
  await sleep(2500);
  const bodyText = await page.evaluate(() => document.body.innerText);
  step(
    '保存凭证',
    bodyText.includes('新增成功') || bodyText.includes('保存成功'),
    {},
  );
} catch (error) {
  out.failures.push(`exception: ${error.message.slice(0, 200)}`);
}
out.pageErrors = pageErrors.slice(0, 5);
console.log(JSON.stringify(out, null, 2));
await browser.close();
