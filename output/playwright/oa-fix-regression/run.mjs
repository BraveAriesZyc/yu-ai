import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-fix-regression';
fs.mkdirSync(DIR, { recursive: true });

const TARGETS = [
  { base: 'http://127.0.0.1:5666', tag: 'antd', lib: 'antd' },
  { base: 'http://127.0.0.1:5999', tag: 'antdv-next', lib: 'antd' },
  { base: 'http://127.0.0.1:5777', tag: 'ele', lib: 'ele' },
];

const results = [];
const record = (tag, name, ok, extra = {}) =>
  results.push({ tag, name, ok, ...extra });

async function runTarget({ base, tag, lib }) {
  const browser = await chromium.launch({ headless: true });
  const page = await (
    await browser.newContext({ viewport: { width: 1680, height: 1000 } })
  ).newPage();

  // 记录当前回归项内的页面异常与接口错误
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

  async function ensureLogin() {
    if (!page.url().includes('/auth/login')) return;
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

  async function goto(path) {
    await page.goto(`${base}${path}`, {
      waitUntil: 'domcontentloaded',
      timeout: 30_000,
    });
    await sleep(3500);
    await ensureLogin();
    if (!page.url().includes(`${path}`)) {
      await page.goto(`${base}${path}`, {
        waitUntil: 'domcontentloaded',
        timeout: 30_000,
      });
      await sleep(3500);
    }
  }

  const isAntdUi = lib === 'antd';
  /** vben 表单中按 label 定位字段控件 */
  const fieldControl = (dialog, label, control) =>
    dialog
      .locator(`label:has-text("${label}")`)
      .locator('xpath=..')
      .locator(control);

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

    // ==================== 1. 会议室「预定信息」弹窗（三端） ====================
    beginItem();
    try {
      await goto('/oa/meetingroom/room');
      const btn = page.locator('button', { hasText: /预定信息/ }).first();
      await btn.waitFor({ timeout: 15_000 });
      await btn.click();
      await page.locator('text=预约情况').first().waitFor({ timeout: 10_000 });
      await sleep(1000);
      // 统计半小时占用格数量（两个 24 格的网格）
      const slotCount = await page.evaluate(() => {
        const grids = [...document.querySelectorAll('div')].filter((el) =>
          String(el.className).includes('grid-cols-[repeat(24'),
        );
        return grids.reduce((sum, g) => sum + g.children.length, 0);
      });
      const http400 = itemHttpErrors.some((e) =>
        e.includes('/oa/meeting-room-booking/schedule'),
      );
      const file = await shot('01-meetingroom-schedule');
      record(tag, '01-会议室预定信息弹窗', slotCount === 48 && !http400, {
        screenshot: file,
        note: itemNote(`半小时格数量=${slotCount}（预期 48）`),
      });
      await page.keyboard.press('Escape');
      await sleep(500);
    } catch (error) {
      const file = await shot('01-meetingroom-schedule').catch(() => undefined);
      record(tag, '01-会议室预定信息弹窗', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 2. 考勤月报「旷工天数」列（三端） ====================
    beginItem();
    try {
      await goto('/oa/attendance/report');
      await page
        .locator(
          '.ant-tabs-tab:has-text("月报"), .el-tabs__item:has-text("月报")',
        )
        .first()
        .click();
      await sleep(3000);
      // 只看可见的（当前页签）表格，避免周报隐藏表格干扰
      const extract = (sel) =>
        page.$$eval(sel, (els) =>
          els
            .filter((el) => el.offsetParent !== null)
            .map((el) => el.textContent.trim()),
        );
      const headers = await extract('.vxe-table--header-wrapper th');
      const lastHeader = headers.filter(Boolean).pop() || '';
      const lastColValues = await page.$$eval(
        '.vxe-table--body-wrapper tbody tr',
        (rows) =>
          rows
            .filter((r) => r.offsetParent !== null)
            .map((r) => {
              const cells = [...r.querySelectorAll('td')].filter(
                (c) => c.offsetParent !== null,
              );
              return cells.length > 0
                ? cells[cells.length - 1].textContent.trim()
                : '';
            }),
      );
      const nonEmpty = lastColValues.filter((v) => v !== '' && v !== '-');
      const file = await shot('02-attendance-month-report');
      const ok = lastHeader === '旷工天数' && itemErrors.length === 0;
      record(tag, '02-考勤月报旷工天数列', ok, {
        screenshot: file,
        note: itemNote(
          `末列列头=${lastHeader}；数据行=${lastColValues.length}，非空值=${nonEmpty.length}${
            nonEmpty.length > 0
              ? `（样例 ${nonEmpty.slice(0, 5).join(',')}）`
              : ''
          }`,
        ),
      });
    } catch (error) {
      const file = await shot('02-attendance-month-report').catch(
        () => undefined,
      );
      record(tag, '02-考勤月报旷工天数列', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 3. 日程日历视图切换（三端） ====================
    beginItem();
    try {
      await goto('/oa/schedule/calendar');
      const radioScope = isAntdUi ? '.ant-radio-group' : '.el-radio-group';
      const radioTexts = await page.$$eval(
        `${radioScope} label, ${radioScope} .el-radio-button`,
        (els) => els.map((el) => el.textContent.trim()),
      );
      const hasViewSwitch = ['月', '周', '日'].every((t) =>
        radioTexts.includes(t),
      );
      const hasPrev = await page.locator('button', { hasText: /上一/ }).count();
      const hasToday = await page
        .locator('button', { hasText: /今\s*天/ })
        .count();
      const hasNext = await page.locator('button', { hasText: /下一/ }).count();
      const fileMonth = await shot('03-schedule-calendar-month');

      // 切周视图
      await page
        .locator(
          isAntdUi
            ? '.ant-radio-group label.ant-radio-button-wrapper'
            : '.el-radio-group .el-radio-button',
          { hasText: /^周$/ },
        )
        .first()
        .click();
      await sleep(2500);
      const weekCols = await page.evaluate(
        () =>
          [...document.querySelectorAll('.border-border.min-h-60')].filter(
            (el) => el.offsetParent !== null,
          ).length,
      );
      const fileWeek = await shot('03-schedule-calendar-week');

      // 切日视图
      await page
        .locator(
          isAntdUi
            ? '.ant-radio-group label.ant-radio-button-wrapper'
            : '.el-radio-group .el-radio-button',
          { hasText: /^日$/ },
        )
        .first()
        .click();
      await sleep(2500);
      const fileDay = await shot('03-schedule-calendar-day');

      // 日程条优先级着色：日程按钮带内联 style
      const coloredBars = await page.evaluate(
        () =>
          [...document.querySelectorAll('button[style]')].filter(
            (el) => el.offsetParent !== null,
          ).length,
      );
      const ok =
        hasViewSwitch &&
        hasPrev > 0 &&
        hasToday > 0 &&
        hasNext > 0 &&
        weekCols === 7 &&
        itemErrors.length === 0;
      record(tag, '03-日程日历视图切换', ok, {
        screenshot: `${fileMonth}, ${fileWeek}, ${fileDay}`,
        note: itemNote(
          `视图切换=${hasViewSwitch}，上一=${hasPrev}，今天=${hasToday}，下一=${hasNext}，周视图列数=${weekCols}，带内联着色日程条=${coloredBars}`,
        ),
      });
    } catch (error) {
      const file = await shot('03-schedule-calendar').catch(() => undefined);
      record(tag, '03-日程日历视图切换', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 仅 web-antd 的回归项 ====================
    if (tag === 'antd') {
      // 4. 写信附件与字数统计
      beginItem();
      try {
        await goto('/oa/mail/inbox');
        await page
          .locator('button', { hasText: /写\s*信/ })
          .first()
          .click();
        await sleep(2000);
        const bodyText = await page.evaluate(() => document.body.innerText);
        const hasAttachBtn = /添加附件/.test(bodyText);
        const hasTip = bodyText.includes(
          '单个文件不超过 16 MB，总请求不超过 32 MB',
        );
        const wordMatch = bodyText.match(/(\d+)\s*字/);
        const file = await shot('04-mail-compose');
        record(
          'antd',
          '04-写信附件与字数统计',
          hasAttachBtn && hasTip && !!wordMatch,
          {
            screenshot: file,
            note: itemNote(
              `添加附件按钮=${hasAttachBtn}，大小提示=${hasTip}，字数统计=${wordMatch ? wordMatch[0] : '未找到'}`,
            ),
          },
        );
      } catch (error) {
        const file = await shot('04-mail-compose').catch(() => undefined);
        record('antd', '04-写信附件与字数统计', false, {
          screenshot: file,
          note: itemNote(String(error).slice(0, 200)),
        });
      }

      // 5. 请假表单计算天数
      beginItem();
      try {
        await goto('/oa/process/leave');
        await page
          .locator('button', { hasText: /新\s*增/ })
          .first()
          .click();
        const dialog = page.locator('[role="dialog"]:visible').last();
        await dialog.waitFor({ timeout: 10_000 });
        await sleep(1000);

        // 请假类型
        await fieldControl(dialog, '请假类型', '.ant-select').click();
        await sleep(800);
        await page
          .locator('.ant-select-dropdown:visible .ant-select-item-option')
          .first()
          .click();
        await sleep(500);

        // 开始时间、结束时间
        for (const [label, value] of [
          ['开始时间', '2026-09-21 09:00:00'],
          ['结束时间', '2026-09-22 18:00:00'],
        ]) {
          const input = fieldControl(dialog, label, 'input').first();
          await input.click();
          await sleep(600);
          await input.fill(value);
          await page.keyboard.press('Enter');
          await sleep(600);
          await page.keyboard.press('Escape');
          await sleep(400);
        }

        const daysValue = await fieldControl(dialog, '天数', 'input')
          .first()
          .inputValue();
        const file = await shot('05-leave-days');
        record('antd', '05-请假表单计算天数', Number(daysValue) === 2, {
          screenshot: file,
          note: itemNote(`天数输入框值=${daysValue || '(空)'}（预期 2）`),
        });
        await page.keyboard.press('Escape');
        await sleep(500);
      } catch (error) {
        const file = await shot('05-leave-days').catch(() => undefined);
        record('antd', '05-请假表单计算天数', false, {
          screenshot: file,
          note: itemNote(String(error).slice(0, 200)),
        });
      }

      // 7. 车辆管理「所属部门」下拉
      beginItem();
      try {
        await goto('/oa/vehicle/list');
        await page
          .locator('button', { hasText: /新\s*增/ })
          .first()
          .click();
        const dialog = page.locator('[role="dialog"]:visible').last();
        await dialog.waitFor({ timeout: 10_000 });
        await sleep(1500);
        await fieldControl(dialog, '所属部门', '.ant-select').click();
        await sleep(2000);
        const nodeCount = await page
          .locator('.ant-select-dropdown:visible .ant-select-tree-treenode')
          .count();
        const file = await shot('07-vehicle-dept-tree');
        record('antd', '07-车辆所属部门下拉', nodeCount > 0, {
          screenshot: file,
          note: itemNote(`部门树节点数=${nodeCount}`),
        });
        await page.keyboard.press('Escape');
        await sleep(500);
      } catch (error) {
        const file = await shot('07-vehicle-dept-tree').catch(() => undefined);
        record('antd', '07-车辆所属部门下拉', false, {
          screenshot: file,
          note: itemNote(String(error).slice(0, 200)),
        });
      }
    }

    // ==================== 6. 通讯录「我共享的」列（三端） ====================
    beginItem();
    try {
      await goto('/oa/contact');
      await page
        .locator(
          '.ant-menu-item:has-text("我共享的"), .el-menu-item:has-text("我共享的")',
        )
        .first()
        .click();
      await sleep(4000);
      const headers = await page.$$eval(
        '.vxe-table--header-wrapper th',
        (els) =>
          els
            .filter((el) => el.offsetParent !== null)
            .map((el) => el.textContent.trim()),
      );
      const joined = headers.join('|');
      const need = ['接收人', '共享时间', '处理状态'];
      const missing = need.filter((h) => !joined.includes(h));
      const file = await shot('06-contact-shared');
      record(
        tag,
        '06-通讯录我共享的列',
        missing.length === 0 && itemErrors.length === 0,
        {
          screenshot: file,
          note: itemNote(
            missing.length > 0
              ? `缺少列: ${missing.join(',')}；实际列: ${joined.slice(0, 200)}`
              : `列头齐全: ${need.join('/')}`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('06-contact-shared').catch(() => undefined);
      record(tag, '06-通讯录我共享的列', false, {
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
  `${DIR}/result.json`,
  JSON.stringify({ generatedAt: new Date().toISOString(), results }, null, 2),
);
for (const r of results) {
  console.log(
    `${r.ok ? 'PASS' : 'FAIL'} [${r.tag}] ${r.name}${r.note ? ` -- ${r.note}` : ''}`,
  );
}
