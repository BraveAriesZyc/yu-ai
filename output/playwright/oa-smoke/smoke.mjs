import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://127.0.0.1:5666';
const TAG = process.argv[3] || 'antd';
const DIR = `output/playwright/oa-smoke/${TAG}`;
fs.mkdirSync(DIR, { recursive: true });

const results = [];
const record = (name, ok, extra = {}) => results.push({ name, ok, ...extra });

// OA 菜单页面（路径取自后端 system_menu SQL）
const PAGES = [
  ['home-工作台', '/oa/home'],
  ['announcement-list-我发布的', '/oa/announcement/list'],
  ['announcement-my-我收到的', '/oa/announcement/my'],
  ['attendance-list-考勤记录', '/oa/attendance/list'],
  ['attendance-my-我的考勤', '/oa/attendance/my'],
  ['attendance-report-考勤报表', '/oa/attendance/report'],
  ['contact-通讯录', '/oa/contact'],
  ['discussion-manage-讨论管理', '/oa/discussion/manage'],
  ['discussion-list-讨论区', '/oa/discussion/list'],
  ['file-企业云盘', '/oa/file'],
  ['mail-inbox-收件箱', '/oa/mail/inbox'],
  ['mail-account-邮箱账号', '/oa/mail/account'],
  ['mail-provider-邮箱服务', '/oa/mail/provider'],
  ['meetingroom-room-会议室', '/oa/meetingroom/room'],
  ['meetingroom-booking-会议室预定', '/oa/meetingroom/booking'],
  ['note-笔记', '/oa/note'],
  ['officialdoc-template-套红模板', '/oa/officialdoc/template'],
  ['officialdoc-send-公文发文', '/oa/officialdoc/send'],
  ['officialdoc-receive-公文收文', '/oa/officialdoc/receive'],
  ['plan-list-计划管理', '/oa/plan/list'],
  ['plan-report-计划报表', '/oa/plan/report'],
  ['process-leave-请假', '/oa/process/leave'],
  ['process-overtime-加班', '/oa/process/overtime'],
  ['process-regular-转正', '/oa/process/regular'],
  ['process-resign-离职', '/oa/process/resign'],
  ['process-reimbursement-报销', '/oa/process/reimbursement'],
  ['schedule-list-日程管理', '/oa/schedule/list'],
  ['schedule-calendar-我的日历', '/oa/schedule/calendar'],
  ['seal-list-印章信息', '/oa/seal/list'],
  ['seal-apply-用印申请', '/oa/seal/apply'],
  ['supply-item-用品管理', '/oa/supply/item'],
  ['supply-apply-领用申请', '/oa/supply/apply'],
  ['supply-issue-发放管理', '/oa/supply/issue'],
  ['task-list-任务管理', '/oa/task/list'],
  ['task-my-我的任务', '/oa/task/my'],
  ['travel-apply-出差申请', '/oa/travel/apply'],
  ['travel-reimbursement-差旅报销', '/oa/travel/reimbursement'],
  ['vehicle-list-车辆信息', '/oa/vehicle/list'],
  ['vehicle-apply-用车申请', '/oa/vehicle/apply'],
  ['vehicle-return-还车申请', '/oa/vehicle/return'],
  ['workreport-my-我的汇报', '/oa/work-report/my'],
  ['workreport-statistics-汇报统计', '/oa/work-report/statistics'],
];

const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
const pageErrors = [];
page.on('pageerror', (e) =>
  pageErrors.push(
    `${page.url().replace(BASE, '')} :: ${(e.stack || e.message).slice(0, 300)}`,
  ),
);

async function shot(name) {
  await page.screenshot({ path: `${DIR}/${name}.png`, fullPage: false });
}

try {
  // 登录（开发环境表单预填默认账号）
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
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
  record('00-登录', true);

  for (const [name, path] of PAGES) {
    const before = pageErrors.length;
    try {
      await page.goto(`${BASE}${path}`, {
        waitUntil: 'domcontentloaded',
        timeout: 30_000,
      });
      await sleep(3500);
      const url = page.url();
      if (url.includes('/auth/login')) {
        record(name, false, { path, note: '被重定向到登录页' });
        continue;
      }
      if (url.includes('/404') || url.includes('exception')) {
        record(name, false, {
          path,
          note: `路由异常: ${url.replace(BASE, '')}`,
        });
        await shot(name);
        continue;
      }
      const newErrors = pageErrors.slice(before);
      await shot(name);
      record(name, newErrors.length === 0, {
        path,
        note: newErrors.join(' | ').slice(0, 300),
      });
    } catch (error) {
      record(name, false, { path, note: String(error).slice(0, 200) });
    }
  }
} finally {
  fs.writeFileSync(
    `${DIR}/result.json`,
    JSON.stringify({ base: BASE, results, pageErrors }, null, 2),
  );
  const pass = results.filter((r) => r.ok).length;
  console.log(`PASS ${pass}/${results.length}`);
  for (const r of results.filter((r) => !r.ok)) {
    console.log(`FAIL ${r.name} ${r.path || ''} ${r.note || ''}`);
  }
  await browser.close();
}
