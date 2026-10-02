import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-round2';

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

// ---------- 探针 1: antd 请假列表标题链接 ----------
{
  const base = 'http://127.0.0.1:5666';
  const page = await (
    await browser.newContext({ viewport: { width: 1680, height: 1000 } })
  ).newPage();
  await login(page, base);
  await page.goto(`${base}/oa/process/leave`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  const all = await page
    .locator('.vxe-table--body-wrapper tbody tr span.cursor-pointer')
    .evaluateAll((els) =>
      els.map((el) => ({
        text: el.textContent.slice(0, 20),
        visible: !!(el.offsetParent && el.getBoundingClientRect().width > 0),
        wrapper: el
          .closest('.vxe-table--body-wrapper')
          ?.className?.slice(0, 80),
      })),
    );
  console.log('leave title spans:', JSON.stringify(all.slice(0, 6), null, 1));
  // 可见性过滤后点击
  const vis = page
    .locator('.vxe-table--body-wrapper tbody tr span.cursor-pointer:visible')
    .first();
  console.log(
    'visible count:',
    await page
      .locator('.vxe-table--body-wrapper tbody tr span.cursor-pointer:visible')
      .count(),
  );
  if (await vis.count()) {
    await vis.click();
    await sleep(2500);
    const dlg = page.locator('[role="dialog"]:visible').last();
    console.log(
      'detail dialog:',
      await dlg.count(),
      (await dlg.evaluate((el) => el.innerText).catch(() => '')).slice(0, 200),
    );
    const m = (await dlg.evaluate((el) => el.innerText).catch(() => '')).match(
      /天数[\s\S]{0,40}/,
    );
    console.log('天数附近:', m ? m[0] : '未找到');
  }
  await page.close();
}

// ---------- 探针 2: antd 工作汇报表单加行 ----------
{
  const base = 'http://127.0.0.1:5666';
  const page = await (
    await browser.newContext({ viewport: { width: 1680, height: 1000 } })
  ).newPage();
  page.on('pageerror', (e) =>
    console.log('PAGEERROR:', e.message.slice(0, 150)),
  );
  await login(page, base);
  await page.goto(`${base}/oa/work-report/my`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(3500);
  await page
    .locator('button', { hasText: /新\s*增/ })
    .first()
    .click();
  await sleep(2000);
  const dlg = page.locator('[role="dialog"]:visible').last();
  console.log('dlg count:', await dlg.count());
  const btnWork = dlg.locator('button', { hasText: '新增工作项' }).first();
  console.log(
    'btnWork count:',
    await btnWork.count(),
    'disabled:',
    await btnWork.isDisabled().catch(() => '?'),
  );
  const tblInfo = await dlg.locator('table').evaluateAll((els) =>
    els.map((t) => ({
      cls: t.className.slice(0, 60),
      rows: t.querySelectorAll('tbody tr').length,
    })),
  );
  console.log('tables before:', JSON.stringify(tblInfo));
  await btnWork.click();
  await sleep(800);
  const tblInfo2 = await dlg.locator('table').evaluateAll((els) =>
    els.map((t) => ({
      cls: t.className.slice(0, 60),
      rows: t.querySelectorAll('tbody tr').length,
    })),
  );
  console.log('tables after:', JSON.stringify(tblInfo2));
  await page.screenshot({ path: `${DIR}/probe-06-form.png` });
  await page.close();
}

// ---------- 探针 3: antdv-next 请假表单 ----------
{
  const base = 'http://127.0.0.1:5999';
  const page = await (
    await browser.newContext({ viewport: { width: 1680, height: 1000 } })
  ).newPage();
  page.on('pageerror', (e) =>
    console.log('PAGEERROR:', e.message.slice(0, 150)),
  );
  await login(page, base);
  await page.goto(`${base}/oa/process/leave`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  await page
    .locator('button', { hasText: /新\s*增/ })
    .first()
    .click();
  await sleep(2500);
  const dlg = page
    .locator('.ant-modal:visible, [role="dialog"]:visible')
    .last();
  console.log('antdv-next dlg count:', await dlg.count());
  const tas = await dlg
    .locator('textarea')
    .evaluateAll((els) =>
      els.map((el) => ({
        ph: el.getAttribute('placeholder'),
        ml: el.getAttribute('maxlength'),
      })),
    )
    .catch((error) => String(error).slice(0, 100));
  console.log('antdv-next textareas:', JSON.stringify(tas));
  await page.screenshot({ path: `${DIR}/probe-02-antdv-next-leave.png` });
  await page.close();
}

await browser.close();
