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

const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await login(page, base);
await page.goto(`${base}/oa/seal/apply`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
await page
  .locator(
    '.vxe-table--body-wrapper.body--wrapper tbody tr td span.cursor-pointer:visible',
  )
  .first()
  .click();
await sleep(2500);
const dlg = page.locator('[role="dialog"]:visible').last();
console.log('dlg count:', await dlg.count());
console.log(
  'sr-only close btn count:',
  await dlg.locator('button:has(span.sr-only)').count(),
);
console.log(
  'all buttons in dlg:',
  await dlg.locator('button').evaluateAll((els) =>
    els.map((el) => ({
      cls: el.className.slice(0, 80),
      text: el.textContent.trim().slice(0, 15),
    })),
  ),
);
// 尝试点击关闭
const closeBtn = dlg.locator('button:has(span.sr-only)').first();
if (await closeBtn.count()) {
  await closeBtn
    .click({ timeout: 5000 })
    .catch((error) =>
      console.log('close click err:', String(error).slice(0, 120)),
    );
  await sleep(800);
  console.log(
    'after close, visible dialogs:',
    await page.locator('[role="dialog"]:visible').count(),
  );
}
await browser.close();
