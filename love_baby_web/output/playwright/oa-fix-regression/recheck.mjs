import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-fix-regression';
fs.mkdirSync(DIR, { recursive: true });

const TARGETS = [
  { base: 'http://127.0.0.1:5666', tag: 'antd' },
  { base: 'http://127.0.0.1:5999', tag: 'antdv-next' },
  { base: 'http://127.0.0.1:5777', tag: 'ele' },
];

const results = [];
const record = (tag, name, ok, extra = {}) =>
  results.push({ tag, name, ok, ...extra });

async function runTarget({ base, tag }) {
  const browser = await chromium.launch({ headless: true });
  const page = await (
    await browser.newContext({ viewport: { width: 1680, height: 1000 } })
  ).newPage();

  let itemErrors = [];
  let itemHttpErrors = [];
  page.on('pageerror', (e) =>
    itemErrors.push(
      `${page.url().replace(base, '')} :: ${(e.stack || e.message).slice(0, 200)}`,
    ),
  );
  page.on('response', (res) => {
    if (res.status() >= 400) {
      itemHttpErrors.push(
        `${res.status()} ${res.url().replace(base, '').slice(0, 160)}`,
      );
    }
  });

  const shot = async (name) => {
    const file = `${tag}-${name}.png`;
    await page.screenshot({ path: `${DIR}/${file}`, fullPage: false });
    return file;
  };
  const beginItem = () => {
    itemErrors = [];
    itemHttpErrors = [];
  };
  const itemNote = (extra = '') => {
    const parts = [];
    if (extra) parts.push(extra);
    if (itemErrors.length > 0)
      parts.push(`pageerror: ${itemErrors.join(' | ').slice(0, 300)}`);
    if (itemHttpErrors.length > 0)
      parts.push(
        `http: ${[...new Set(itemHttpErrors)].join(' | ').slice(0, 300)}`,
      );
    return parts.join(' || ');
  };

  const visibleHeaders = () =>
    page.$$eval('.vxe-table--header-wrapper th', (els) =>
      els
        .filter((el) => el.offsetParent !== null)
        .map((el) => el.textContent.trim())
        .filter(Boolean),
    );

  try {
    // ==================== 登录 ====================
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
    record(tag, '00-登录', true);

    // ==================== 1. 通讯录「我共享的」列（三端复测） ====================
    beginItem();
    try {
      await page.goto(`${base}/oa/contact`, {
        waitUntil: 'domcontentloaded',
        timeout: 30_000,
      });
      await sleep(3500);
      await page
        .locator(
          '.ant-menu-item:has-text("我共享的"), .el-menu-item:has-text("我共享的")',
        )
        .first()
        .click();
      await sleep(4000);
      const headers = await visibleHeaders();
      const joined = headers.join('|');
      const need = ['接收人', '共享时间', '处理状态'];
      const missing = need.filter((h) => !joined.includes(h));
      const file = await shot('recheck-01-contact-shared');
      record(
        tag,
        '01-通讯录我共享的列',
        missing.length === 0 && itemErrors.length === 0,
        {
          screenshot: file,
          note: itemNote(
            missing.length > 0
              ? `缺少列: ${missing.join(',')}；实际列: ${joined.slice(0, 200)}`
              : `列头齐全: ${headers.join('|')}`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('recheck-01-contact-shared').catch(
        () => undefined,
      );
      record(tag, '01-通讯录我共享的列', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    if (tag !== 'antd') return;

    // ==================== 2. 工作汇报页签表头跟随切换（仅 antd） ====================
    beginItem();
    try {
      await page.goto(`${base}/oa/work-report/my`, {
        waitUntil: 'domcontentloaded',
        timeout: 30_000,
      });
      await sleep(3500);
      const headersDaily = await visibleHeaders();
      const fileDaily = await shot('recheck-02-workreport-daily');

      await page.locator('.ant-tabs-tab:has-text("工作周报")').first().click();
      await sleep(3000);
      const headersWeekly = await visibleHeaders();
      const fileWeekly = await shot('recheck-02-workreport-weekly');

      await page.locator('.ant-tabs-tab:has-text("工作月报")').first().click();
      await sleep(3000);
      const headersMonthly = await visibleHeaders();
      const fileMonthly = await shot('recheck-02-workreport-monthly');

      const ok =
        !headersDaily.includes('汇报周次') &&
        !headersDaily.includes('汇报月份') &&
        headersWeekly.includes('汇报周次') &&
        !headersWeekly.includes('汇报月份') &&
        headersMonthly.includes('汇报月份') &&
        !headersMonthly.includes('汇报周次') &&
        itemErrors.length === 0;
      record('antd', '02-工作汇报页签表头切换', ok, {
        screenshot: `${fileDaily}, ${fileWeekly}, ${fileMonthly}`,
        note: itemNote(
          `日报列=${headersDaily.join('|')} || 周报列=${headersWeekly.join('|')} || 月报列=${headersMonthly.join('|')}`,
        ),
      });
    } catch (error) {
      const file = await shot('recheck-02-workreport').catch(() => undefined);
      record('antd', '02-工作汇报页签表头切换', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 3. 考勤周报日期列跟随所选周（仅 antd） ====================
    beginItem();
    try {
      await page.goto(`${base}/oa/attendance/report`, {
        waitUntil: 'domcontentloaded',
        timeout: 30_000,
      });
      await sleep(3500);
      // 默认周报页签；提取日期列头中的 MM-DD
      const dayDates = (headers) =>
        headers
          .filter((h) => /^周[一二三四五六日] \d{2}-\d{2}$/.test(h))
          .map((h) => h.slice(3));
      const before = dayDates(await visibleHeaders());
      const fileBefore = await shot('recheck-03-week-report-current');

      // 验收路径：表单改「所在周」到另一周并点搜索
      const input = page
        .locator('label:has-text("所在周")')
        .locator('xpath=..')
        .locator('input');
      await input.click();
      await sleep(600);
      await input.fill('2026-09-07');
      await page.keyboard.press('Enter');
      await sleep(800);
      await page.keyboard.press('Escape');
      await sleep(500);
      await page
        .locator('button', { hasText: /^搜\s*索$/ })
        .first()
        .click();
      await sleep(3000);
      const after = dayDates(await visibleHeaders());
      const fileAfter = await shot('recheck-03-week-report-search');

      const expectDates = [
        '09-07',
        '09-08',
        '09-09',
        '09-10',
        '09-11',
        '09-12',
        '09-13',
      ];
      const followOk =
        before.length === 7 &&
        after.length === 7 &&
        after.join(',') === expectDates.join(',');

      // 附加检查：工具栏「上一周」按钮是否生效
      await page
        .locator('button', { hasText: /上一周/ })
        .first()
        .click();
      await sleep(3000);
      const afterBtn = dayDates(await visibleHeaders());
      const fileBtn = await shot('recheck-03-week-report-prevbtn');
      const btnOk =
        afterBtn.length === 7 && afterBtn.join(',') !== after.join(',');

      record(
        'antd',
        '03-考勤周报日期列跟随切换',
        followOk && itemErrors.length === 0,
        {
          screenshot: `${fileBefore}, ${fileAfter}, ${fileBtn}`,
          note: itemNote(
            `初始周=${before.join(',')} || 搜索 2026-09-07 周后=${after.join(',')}（跟随=${followOk}）|| 附加：点「上一周」后=${afterBtn.join(',')}（按钮生效=${btnOk}）`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('recheck-03-week-report').catch(() => undefined);
      record('antd', '03-考勤周报日期列跟随切换', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }
  } finally {
    await browser.close();
  }
}

for (const target of TARGETS) {
  console.log(`===== ${target.tag} ${target.base} =====`);
  try {
    await runTarget(target);
  } catch (error) {
    record(target.tag, '目标整体执行', false, {
      note: String(error).slice(0, 300),
    });
  }
}

fs.writeFileSync(
  `${DIR}/recheck-result.json`,
  JSON.stringify({ generatedAt: new Date().toISOString(), results }, null, 2),
);
for (const r of results) {
  console.log(
    `${r.ok ? 'PASS' : 'FAIL'} [${r.tag}] ${r.name}${r.note ? ` -- ${r.note}` : ''}`,
  );
}
