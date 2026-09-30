import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-round2';
fs.mkdirSync(DIR, { recursive: true });
const base = 'http://127.0.0.1:5999';

const results = [];
const record = (name, ok, extra = {}) =>
  results.push({ tag: 'antdv-next', name, ok, ...extra });

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
  if (res.status() >= 400)
    itemHttpErrors.push(
      `${res.status()} ${res.url().replace(base, '').slice(0, 160)}`,
    );
});
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
const shot = async (name) => {
  const file = `antdv-next-recheck-${name}.png`;
  await page.screenshot({ path: `${DIR}/${file}`, fullPage: false });
  return file;
};
const dialog = () =>
  page.locator('.ant-modal:visible, [role="dialog"]:visible').last();

/** 读取 textarea 的最大长度：原生 maxlength 属性或 showCount 的 data-count */
const textareaMaxLength = async (ta) => {
  const attr = await ta.getAttribute('maxlength');
  if (attr) return attr;
  const dataCount = await ta
    .locator('xpath=..')
    .dataset.count.catch(() => null);
  if (dataCount) {
    const m = dataCount.match(/\/\s*(\d+)/);
    if (m) return m[1];
  }
  return null;
};

const goto = async (path) => {
  await page.goto(`${base}${path}`, {
    waitUntil: 'domcontentloaded',
    timeout: 30_000,
  });
  await sleep(3500);
};
const closeTopDialog = async () => {
  const dlg = dialog();
  if (!(await dlg.count())) return;
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
  await sleep(500);
  if (await dialog().count()) {
    await page.keyboard.press('Escape');
    await sleep(400);
  }
};

try {
  // ==================== 登录 ====================
  await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(1500);
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
  record('00-登录', true);

  /**
   * 通用：打开列表「新增」弹窗，校验 textarea 渲染与 maxlength
   * expects: [{ placeholder 包含, maxlength 期望(null=无限制) }]
   */
  const checkForm = async (path, name, shotName, expects) => {
    beginItem();
    try {
      await goto(path);
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1500);
      const details = [];
      let allOk = true;
      for (const exp of expects) {
        const ta = dlg
          .locator(`textarea[placeholder*="${exp.placeholder}"]`)
          .first();
        const rendered = (await ta.count()) > 0;
        let ml = null;
        let inputOk = null;
        if (rendered) {
          ml = await textareaMaxLength(ta);
          // 有 maxlength 期望时实测超长输入截断
          if (exp.maxlength !== null) {
            await ta.fill('a'.repeat(Number(exp.maxlength) + 50));
            await sleep(300);
            const len = (await ta.inputValue()).length;
            inputOk = len === Number(exp.maxlength);
            await ta.fill('');
          }
        }
        const mlOk =
          exp.maxlength === null ? true : ml === String(exp.maxlength);
        const itemOk =
          rendered && mlOk && (exp.maxlength === null || inputOk === true);
        allOk = allOk && itemOk;
        details.push(
          `${exp.placeholder}: 渲染=${rendered}，maxlength=${ml ?? '无'}(期望${exp.maxlength ?? '无'})${inputOk === null ? '' : `，截断实测=${inputOk}`}`,
        );
      }
      const file = await shot(shotName);
      record(name, allOk, {
        screenshot: file,
        note: itemNote(details.join('；')),
      });
      await closeTopDialog();
    } catch (error) {
      const file = await shot(shotName).catch(() => undefined);
      record(name, false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }
  };

  // ==================== 1. OA 各流程表单 textarea ====================
  await checkForm('/oa/process/leave', '01-请假表单textarea', 'leave-form', [
    { placeholder: '请输入申请原因', maxlength: 5000 },
  ]);
  await checkForm(
    '/oa/process/overtime',
    '01-加班表单textarea',
    'overtime-form',
    [{ placeholder: '请输入申请原因', maxlength: 5000 }],
  );
  await checkForm(
    '/oa/process/reimbursement',
    '01-报销表单textarea',
    'reimbursement-form',
    [{ placeholder: '请输入申请原因', maxlength: 5000 }],
  );
  await checkForm('/oa/process/resign', '01-离职表单textarea', 'resign-form', [
    { placeholder: '请输入未完成事宜', maxlength: null },
    { placeholder: '请输入申请原因', maxlength: 5000 },
  ]);
  await checkForm(
    '/oa/process/regular',
    '01-转正表单六个textarea',
    'regular-form',
    [
      { placeholder: '请输入试用期心得', maxlength: 255 },
      { placeholder: '请输入岗位职责理解', maxlength: 255 },
      { placeholder: '请输入试用期成长', maxlength: 255 },
      { placeholder: '请输入目前不足', maxlength: 255 },
      { placeholder: '请输入工作改进', maxlength: 255 },
      { placeholder: '请输入产品意见建议', maxlength: 255 },
    ],
  );

  // ==================== 2. 其他 OA 表单 textarea ====================
  await checkForm(
    '/oa/officialdoc/send',
    '02-发文附注textarea',
    'officialdoc-send-form',
    [{ placeholder: '请输入附注', maxlength: null }],
  );
  await checkForm(
    '/oa/meetingroom/booking',
    '02-会议室预订textarea',
    'meetingroom-booking-form',
    [
      { placeholder: '请输入会议内容', maxlength: 500 },
      { placeholder: '请输入备注', maxlength: 500 },
    ],
  );

  // ==================== 3. infra/config 使用 TextArea 键的回归 ====================
  beginItem();
  try {
    await goto('/infra/config');
    const editBtn = page
      .locator('.vxe-table--body-wrapper tbody tr button', {
        hasText: /修\s*改/,
      })
      .first();
    await editBtn.click();
    const dlg = dialog();
    await dlg.waitFor({ timeout: 10_000 });
    await sleep(1500);
    const ta = dlg.locator('textarea').first();
    const rendered = (await ta.count()) > 0;
    let inputOk = null;
    if (rendered) {
      await ta.fill('回归测试备注');
      await sleep(300);
      inputOk = (await ta.inputValue()) === '回归测试备注';
    }
    const file = await shot('infra-config-form');
    record('03-infra配置TextArea回归', rendered && inputOk === true, {
      screenshot: file,
      note: itemNote(`备注textarea渲染=${rendered}，可输入=${inputOk}`),
    });
    await closeTopDialog();
  } catch (error) {
    const file = await shot('infra-config-form').catch(() => undefined);
    record('03-infra配置TextArea回归', false, {
      screenshot: file,
      note: itemNote(String(error).slice(0, 200)),
    });
  }
} finally {
  await browser.close();
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
