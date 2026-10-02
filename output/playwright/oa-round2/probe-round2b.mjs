import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(page, base) {
  await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(1500);
  const tenantSelect = page.locator(
    '.ant-select:has-text("请选择租户"), .el-select:has-text("请选择租户")',
  );
  if (await tenantSelect.count()) {
    await tenantSelect.click();
    await sleep(1200);
    await page
      .locator(
        '.ant-select-dropdown .ant-select-item-option, .el-select-dropdown__item',
      )
      .first()
      .click();
    await sleep(500);
  }
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
    timeout: 30_000,
  });
}

const browser = await chromium.launch({ headless: true });
const base = 'http://127.0.0.1:5666';
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await login(page, base);

// 转正表单
await page.goto(`${base}/oa/process/regular`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
await page
  .locator('button', { hasText: /新\s*增/ })
  .first()
  .click();
await sleep(2000);
const dlg = page.locator('[role="dialog"]:visible').last();
const taInfo = await dlg.locator('textarea').evaluateAll((els) =>
  els.map((el) => ({
    ph: el.placeholder,
    ml: el.getAttribute('maxlength'),
    html: el.outerHTML.slice(0, 160),
  })),
);
console.log('regular textareas:', JSON.stringify(taInfo, null, 1));

// vben modal 关闭控件 DOM
const headerBtns = await dlg
  .locator('header button, [class*="modal"] button')
  .evaluateAll((els) =>
    els.slice(0, 8).map((el) => ({
      cls: el.className.slice(0, 60),
      aria: el.getAttribute('aria-label'),
      text: el.textContent.trim().slice(0, 10),
    })),
  );
console.log('dialog buttons:', JSON.stringify(headerBtns, null, 1));
// 取消按钮
const cancelBtn = dlg.locator('button', { hasText: /取\s*消/ });
console.log('cancel count:', await cancelBtn.count());

// 请假表单 maxlength 验证
await page.goto(`${base}/oa/process/leave`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
await page
  .locator('button', { hasText: /新\s*增/ })
  .first()
  .click();
await sleep(2000);
const dlg2 = page.locator('[role="dialog"]:visible').last();
const ta2 = await dlg2
  .locator('textarea')
  .evaluateAll((els) =>
    els.map((el) => ({ ph: el.placeholder, ml: el.getAttribute('maxlength') })),
  );
console.log('leave textareas:', JSON.stringify(ta2));

await browser.close();
