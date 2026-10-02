import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-round2';
fs.mkdirSync(DIR, { recursive: true });

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
const itemErrors = [];
page.on('pageerror', (e) => itemErrors.push(e.message.slice(0, 200)));
await login(page, base);
await page.goto(`${base}/oa/seal/apply`, { waitUntil: 'domcontentloaded' });
await sleep(4000);

// 主表格区域（排除固定列克隆）的单据编号链接
const rowLinks = page.locator(
  '.vxe-table--body-wrapper.body--wrapper tbody tr td span.cursor-pointer:visible',
);
const rowCount = await rowLinks.count();
console.log('主表格可点击编号数:', rowCount);

const dialog = () => page.locator('[role="dialog"]:visible').last();
const checks = [];
const total = Math.min(rowCount, 6);
for (let i = 0; i < total; i++) {
  await rowLinks.nth(i).click({ timeout: 8000 });
  await sleep(2500);
  const dlg = dialog();
  await dlg.waitFor({ timeout: 8000 }).catch(() => {});
  // bordered 模式下 antd-vue Descriptions 无 .ant-descriptions-item 包裹，按行内 label/content 单元格配对
  const pairs = await dlg
    .locator('.ant-descriptions-view')
    .first()
    .evaluate((el) => {
      const labels = [...el.querySelectorAll('.ant-descriptions-item-label')];
      const contents = [
        ...el.querySelectorAll('.ant-descriptions-item-content'),
      ];
      return labels.map(
        (l, i) =>
          `${l.textContent.trim()}=${contents[i]?.textContent?.trim() ?? ''}`,
      );
    })
    .catch(() => []);
  const text = await dlg.evaluate((el) => el.innerText).catch(() => '');
  const get = (label) =>
    pairs
      .find((p) => p.startsWith(`${label}=`))
      ?.split('=')
      .slice(1)
      .join('=') ?? '';
  const typeLabel = get('用印类型') || get('申请类型');
  const modeLabel = get('用印方式');
  const hasContract = text.includes('合同金额') && text.includes('合同对方');
  const hasReturn =
    text.includes('预计归还时间') && text.includes('实际归还时间');
  const c1 = typeLabel ? hasContract === typeLabel.includes('合同') : null;
  const c2 = modeLabel ? hasReturn === modeLabel.includes('外借') : null;
  checks.push(
    `行${i + 1}: 类型=${typeLabel || '?'}(${hasContract ? '有' : '无'}合同字段,一致=${c1}) 方式=${modeLabel || '?'}(${hasReturn ? '有' : '无'}归还字段,一致=${c2})`,
  );
  if (i === 0 || c1 === false || c2 === false) {
    await page.screenshot({ path: `${DIR}/antd-05-seal-apply-detail.png` });
  }
  // 关闭详情弹窗（底部取消 → 头部图标 X → sr-only Close → Escape）
  const cancelBtn = dlg
    .locator('button', { hasText: /^\s*取\s*消\s*$/ })
    .first();
  if (await cancelBtn.count()) {
    await cancelBtn.click().catch(() => {});
  } else {
    await dlg
      .locator('button.flex-center')
      .last()
      .click()
      .catch(() => {});
  }
  await sleep(600);
  if (await dialog().count()) {
    await dlg
      .locator('button:has(span.sr-only)')
      .first()
      .click()
      .catch(() => {});
    await sleep(500);
  }
  if (await dialog().count()) {
    await page.keyboard.press('Escape');
    await sleep(600);
  }
}
console.log('checks:');
for (const c of checks) console.log('', c);
console.log('pageerror:', itemErrors.join(' | ') || '无');
await browser.close();
