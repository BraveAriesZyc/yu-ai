import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-round2';
fs.mkdirSync(DIR, { recursive: true });

const TARGETS = [
  { base: 'http://127.0.0.1:5666', tag: 'antd', full: true },
  { base: 'http://127.0.0.1:5999', tag: 'antdv-next', full: false },
  { base: 'http://127.0.0.1:5777', tag: 'ele', full: false },
];

const results = [];
const record = (tag, name, ok, extra = {}) =>
  results.push({ tag, name, ok, ...extra });

async function runTarget({ base, tag, full }) {
  const isEle = tag === 'ele';
  const browser = await chromium.launch({ headless: true });
  const page = await (
    await browser.newContext({
      viewport: { width: 1680, height: 1000 },
      acceptDownloads: true,
    })
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
  /** vben Modal/Drawer 与组件库原生弹窗统一选取最后一个可见弹窗 */
  const dialog = () =>
    page
      .locator(
        '.ant-modal:visible, .el-overlay:visible .el-dialog, [role="dialog"]:visible',
      )
      .last();
  const fieldControl = (dlg, label, control) =>
    dlg
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

    const goto = async (path) => {
      await page.goto(`${base}${path}`, {
        waitUntil: 'domcontentloaded',
        timeout: 30_000,
      });
      await sleep(3500);
    };
    const closeTopDialog = async () => {
      const dlg = dialog();
      if (!(await dlg.count())) {
        return;
      }
      // 优先点底部「取消」，其次右上角 X（sr-only Close），最后 Escape
      const cancelBtn = dlg
        .locator('button', { hasText: /^\s*取\s*消\s*$/ })
        .first();
      if (await cancelBtn.count()) {
        await cancelBtn.click().catch(() => {});
      } else {
        await dlg
          .locator(
            '.ant-modal-close, .el-dialog__close, button:has(span.sr-only)',
          )
          .first()
          .click()
          .catch(() => {});
      }
      await sleep(400);
      if (await dialog().count()) {
        await page.keyboard.press('Escape');
        await sleep(400);
      }
    };
    /** 读取 textarea 的最大长度：原生 maxlength 属性、antd showCount 的 data-count、ele 计数文本 */
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
      const eleCount = await ta
        .locator('xpath=ancestor::*[contains(@class,"el-textarea")][1]')
        .locator('.el-input__count-inner')
        .textContent()
        .catch(() => null);
      if (eleCount) {
        const m = eleCount.match(/\/(\d+)/);
        if (m) return m[1];
      }
      return null;
    };

    // ==================== 1. 用车申请：车辆选择弹窗（三端） ====================
    beginItem();
    try {
      await goto('/oa/vehicle/apply');
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const formDlg = dialog();
      await formDlg.waitFor({ timeout: 10_000 });
      await sleep(1200);

      const vehicleInput = formDlg
        .locator('input[placeholder*="请选择车辆"]')
        .first();
      const readonly = (await vehicleInput.getAttribute('readonly')) !== null;
      await vehicleInput.click();
      await sleep(2500);

      const selDlg = isEle
        ? page
            .locator('.el-overlay:visible .el-dialog:has-text("选择车辆")')
            .last()
        : page.locator('.ant-modal:visible:has-text("选择车辆")').last();
      await selDlg.waitFor({ timeout: 8000 });
      const titleText = await selDlg
        .locator('.ant-modal-title, .el-dialog__title')
        .first()
        .textContent()
        .catch(() => '');
      const searchLabels = (
        await selDlg.locator('.vxe-grid--form-wrapper label').allTextContents()
      ).join('|');
      const needSearch = ['车牌号', '品牌型号', '车型', '车辆分类'];
      const missingSearch = needSearch.filter((l) => !searchLabels.includes(l));
      // 表头列
      const colTexts = await selDlg
        .locator('.vxe-table--header-wrapper th')
        .evaluateAll((els) =>
          els
            .filter((el) => el.offsetParent !== null)
            .map((el) => el.textContent.trim())
            .filter(Boolean),
        )
        .catch(() => []);
      const needCols = ['车牌号', '品牌型号', '车型', '车辆分类'];
      const missingCols = needCols.filter((c) => !colTexts.includes(c));

      const okBtn = selDlg.locator(
        '.ant-modal-footer .ant-btn-primary, .el-dialog__footer .el-button--primary',
      );
      const disabledBefore = await okBtn.isDisabled().catch(() => null);

      const rowCount = await selDlg
        .locator('.vxe-table--body-wrapper tbody tr')
        .count();
      let refillOk = null;
      let clearOk = null;
      let okEnabledAfter = null;
      let file;
      if (rowCount > 0) {
        await selDlg
          .locator('.vxe-table--body-wrapper tbody tr')
          .first()
          .click();
        await sleep(800);
        okEnabledAfter = !(await okBtn.isDisabled().catch(() => true));
        file = await shot('01-vehicle-apply-select');
        await okBtn.click();
        await sleep(1200);
        const filled = await vehicleInput.inputValue().catch(() => '');
        refillOk = !!filled && filled.trim().length > 0;
        // 清空：suffix 处的清除图标
        const clearIcon = formDlg
          .locator('input[placeholder*="请选择车辆"]')
          .locator('xpath=..')
          .locator('.ant-input-clear-icon, .cursor-pointer, .el-input__clear')
          .first();
        if (await clearIcon.count()) {
          await clearIcon.click({ force: true });
          await sleep(600);
          const afterClear = await vehicleInput.inputValue().catch(() => '?');
          clearOk = afterClear === '';
        }
      } else {
        file = await shot('01-vehicle-apply-select');
      }

      const ok =
        readonly &&
        titleText.includes('选择车辆') &&
        missingSearch.length === 0 &&
        missingCols.length === 0 &&
        disabledBefore === true &&
        (rowCount === 0 ||
          (okEnabledAfter === true && refillOk === true && clearOk === true));
      record(tag, '01-用车申请车辆选择弹窗', ok, {
        screenshot: file,
        note: itemNote(
          `只读=${readonly}，弹窗标题=${titleText?.trim()}，缺搜索项=${missingSearch.join(',') || '无'}，缺列=${missingCols.join(',') || '无'}，确定禁用(未选)=${disabledBefore}，行数=${rowCount}${
            rowCount > 0
              ? `，选中后可用=${okEnabledAfter}，回填=${refillOk}，清空=${clearOk}`
              : '，无可用车辆数据，回填/清空未验'
          }`,
        ),
      });
      await closeTopDialog();
    } catch (error) {
      const file = await shot('01-vehicle-apply-select').catch(() => undefined);
      record(tag, '01-用车申请车辆选择弹窗', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 2. 请假/加班表单与详情天数（三端） ====================
    const checkLeaveLike = async (path, key, name) => {
      beginItem();
      try {
        await goto(path);
        await page
          .locator('button', { hasText: /新\s*增/ })
          .first()
          .click();
        const dlg = dialog();
        await dlg.waitFor({ timeout: 10_000 });
        await sleep(1200);

        const reasonTa = dlg.locator('textarea[placeholder*="申请原因"]');
        const reasonCount = await reasonTa.count();
        const reasonMax = reasonCount
          ? await textareaMaxLength(reasonTa.first())
          : null;
        // 天数：只读 + 「天」后缀
        const daysWrap = dlg
          .locator('label:has-text("天数")')
          .locator('xpath=..');
        const daysInput = daysWrap.locator('input').first();
        const daysDisabled = await daysInput.isDisabled().catch(() => false);
        const daysAddon = (
          (await daysWrap
            .locator('.ant-input-group-addon, .el-input-group__append')
            .first()
            .textContent()
            .catch(() => '')) || ''
        ).trim();
        const fileForm = await shot(`02-${key}-form`);

        await closeTopDialog();
        // 详情：点击首行标题（过滤固定列隐藏副本）
        const titleLink = page
          .locator(
            '.vxe-table--body-wrapper tbody tr span.cursor-pointer:visible',
          )
          .first();
        let detailOk = null;
        let fileDetail;
        if (await titleLink.count()) {
          await titleLink.click();
          await sleep(2500);
          const detailDlg = dialog();
          await detailDlg.waitFor({ timeout: 8000 }).catch(() => {});
          const detailText = await detailDlg
            .evaluate((el) => el.innerText)
            .catch(() => '');
          detailOk = /\d+(\.\d+)?\s*天/.test(detailText);
          fileDetail = await shot(`02-${key}-detail`);
          await closeTopDialog();
        }

        const ok =
          reasonMax === '5000' &&
          daysDisabled &&
          daysAddon === '天' &&
          detailOk === true;
        record(tag, name, ok, {
          screenshot: [fileForm, fileDetail].filter(Boolean).join(', '),
          note: itemNote(
            `申请原因textarea=${reasonCount ? `maxlength=${reasonMax}` : '未渲染'}，天数禁用=${daysDisabled}，天数后缀=${daysAddon || '无'}，详情天数文本=${detailOk === null ? '无数据未验' : detailOk}`,
          ),
        });
      } catch (error) {
        const file = await shot(`02-${key}`).catch(() => undefined);
        record(tag, name, false, {
          screenshot: file,
          note: itemNote(String(error).slice(0, 200)),
        });
      }
    };
    await checkLeaveLike('/oa/process/leave', 'leave', '02-请假表单与详情天数');
    await checkLeaveLike(
      '/oa/process/overtime',
      'overtime',
      '02-加班表单与详情天数',
    );

    if (!full) return;

    // ==================== 3. 转正申请 textarea maxlength=255 ====================
    beginItem();
    try {
      await goto('/oa/process/regular');
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1200);
      const tas = dlg.locator('textarea');
      const taCount = await tas.count();
      const maxLengths = [];
      for (let i = 0; i < taCount; i++) {
        maxLengths.push((await textareaMaxLength(tas.nth(i))) ?? 'null');
      }
      const file = await shot('03-regular-form');
      const ok =
        maxLengths.length === 6 && maxLengths.every((v) => v === '255');
      record('antd', '03-转正申请textarea长度限制', ok, {
        screenshot: file,
        note: itemNote(
          `textarea数量=${maxLengths.length}（预期6），maxlength=${maxLengths.join(',') || '无'}`,
        ),
      });
      await closeTopDialog();
    } catch (error) {
      const file = await shot('03-regular-form').catch(() => undefined);
      record('antd', '03-转正申请textarea长度限制', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 4. 发文表单「第几号文」 ====================
    beginItem();
    try {
      await goto('/oa/officialdoc/send');
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1500);
      const seqInput = fieldControl(dlg, '第几号文', 'input').first();
      const seqValue = await seqInput.inputValue().catch(() => '?');
      const seqMin =
        (await seqInput.getAttribute('aria-valuemin')) ??
        (await seqInput.getAttribute('min'));
      const file = await shot('04-officialdoc-sequence');
      record('antd', '04-发文表单第几号文', seqValue === '' && seqMin === '1', {
        screenshot: file,
        note: itemNote(
          `新建时值=${seqValue === '' ? '空' : seqValue}，min=${seqMin}`,
        ),
      });
      await closeTopDialog();
    } catch (error) {
      const file = await shot('04-officialdoc-sequence').catch(() => undefined);
      record('antd', '04-发文表单第几号文', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 5. 用印申请详情条件显隐 ====================
    beginItem();
    try {
      await goto('/oa/seal/apply');
      const rowLinks = page.locator(
        '.vxe-table--body-wrapper tbody tr td span.cursor-pointer:visible',
      );
      const rowCount = await rowLinks.count();
      if (rowCount === 0) {
        const file = await shot('05-seal-apply-detail');
        record('antd', '05-用印申请详情条件显隐', true, {
          screenshot: file,
          note: itemNote('列表无数据，详情条件显隐未验，仅确认页面渲染'),
        });
      } else {
        const checks = [];
        let file;
        const total = Math.min(rowCount, 4);
        for (let i = 0; i < total; i++) {
          await rowLinks.nth(i).click();
          await sleep(2500);
          const dlg = dialog();
          await dlg.waitFor({ timeout: 8000 }).catch(() => {});
          // 描述项 label → 内容 映射（兼容 ant-descriptions 结构）
          const pairs = await dlg
            .locator(
              '.ant-descriptions-item, [class*="description"] [class*="item"]',
            )
            .evaluateAll((els) =>
              els.map((el) => {
                const label =
                  el
                    .querySelector(
                      '.ant-descriptions-item-label, [class*="label"]',
                    )
                    ?.textContent?.trim() ?? '';
                const content =
                  el
                    .querySelector(
                      '.ant-descriptions-item-content, [class*="content"]',
                    )
                    ?.textContent?.trim() ?? '';
                return `${label}=${content}`;
              }),
            )
            .catch(() => []);
          const text = await dlg.evaluate((el) => el.innerText).catch(() => '');
          const get = (label) =>
            pairs
              .find((p) => p.startsWith(`${label}=`))
              ?.split('=')
              .slice(1)
              .join('=') ?? '';
          const typeLabel = get('申请类型');
          const modeLabel = get('用印方式');
          const hasContract =
            text.includes('合同金额') && text.includes('合同对方');
          const hasReturn =
            text.includes('预计归还时间') && text.includes('实际归还时间');
          const c1 = typeLabel
            ? hasContract === typeLabel.includes('合同')
            : null;
          const c2 = modeLabel
            ? hasReturn === modeLabel.includes('外借')
            : null;
          checks.push(
            `行${i + 1}: 申请类型=${typeLabel || '?'}(${hasContract ? '有' : '无'}合同字段,一致=${c1}) 用印方式=${modeLabel || '?'}(${hasReturn ? '有' : '无'}归还字段,一致=${c2})`,
          );
          if (i === 0) file = await shot('05-seal-apply-detail');
          await closeTopDialog();
          await sleep(400);
        }
        const allConsistent = checks.every((c) => !c.includes('一致=false'));
        record('antd', '05-用印申请详情条件显隐', allConsistent, {
          screenshot: file,
          note: itemNote(checks.join('；').slice(0, 400)),
        });
      }
    } catch (error) {
      const file = await shot('05-seal-apply-detail').catch(() => undefined);
      record('antd', '05-用印申请详情条件显隐', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 6a. 工作汇报表单按钮 ====================
    beginItem();
    try {
      await goto('/oa/work-report/my');
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1500);
      const btnWork = dlg.locator('button', { hasText: '新增工作项' }).first();
      const btnPlan = dlg.locator('button', { hasText: '新增计划项' }).first();
      const workExists = (await btnWork.count()) > 0;
      const planExists = (await btnPlan.count()) > 0;
      const workDisabled = workExists ? await btnWork.isDisabled() : null;
      const planDisabled = planExists ? await btnPlan.isDisabled() : null;
      // 点击新增工作项应能增加一行（真实数据行带 ant-table-row 类）
      let addRowOk = null;
      if (workExists && !workDisabled) {
        const realRows = () =>
          dlg.locator('table tbody tr.ant-table-row').count();
        const rowsBefore = await realRows();
        await btnWork.click();
        await sleep(800);
        const rowsAfter = await realRows();
        addRowOk = rowsAfter > rowsBefore;
      }
      const file = await shot('06-workreport-form');
      record(
        'antd',
        '06-工作汇报表单新增按钮',
        workExists &&
          planExists &&
          workDisabled === false &&
          planDisabled === false &&
          addRowOk === true,
        {
          screenshot: file,
          note: itemNote(
            `新增工作项=${workExists}(禁用=${workDisabled})，新增计划项=${planExists}(禁用=${planDisabled})，点击加行=${addRowOk}`,
          ),
        },
      );
      await closeTopDialog();
    } catch (error) {
      const file = await shot('06-workreport-form').catch(() => undefined);
      record('antd', '06-工作汇报表单新增按钮', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 6b. 工作汇报统计明细「已填 N」 ====================
    beginItem();
    try {
      await goto('/oa/work-report/statistics');
      await page.locator('.ant-tree-node-content-wrapper').first().click();
      await sleep(3500);
      const submittedCell = page
        .locator('span.cursor-pointer.text-green-500')
        .first();
      if (await submittedCell.count()) {
        const countText = (await submittedCell.textContent())?.trim();
        await submittedCell.click();
        await sleep(2500);
        const dlg = dialog();
        await dlg.waitFor({ timeout: 8000 }).catch(() => {});
        const text = await dlg.evaluate((el) => el.innerText).catch(() => '');
        const m = text.match(/已填\s*(\d+)/);
        const tabOk = !!m && String(countText) === m[1];
        const file = await shot('06-workreport-statistics-detail');
        record('antd', '06-工作汇报统计已填明细', tabOk, {
          screenshot: file,
          note: itemNote(
            `列表已填计数=${countText}，弹窗tab=${m ? m[0] : '未找到'}`,
          ),
        });
        await closeTopDialog();
      } else {
        const file = await shot('06-workreport-statistics-detail');
        record('antd', '06-工作汇报统计已填明细', false, {
          screenshot: file,
          note: itemNote('部门表格无已填计数入口（无数据？）'),
        });
      }
    } catch (error) {
      const file = await shot('06-workreport-statistics-detail').catch(
        () => undefined,
      );
      record('antd', '06-工作汇报统计已填明细', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 7. 我的公告转发 ====================
    beginItem();
    try {
      await goto('/oa/announcement/my');
      const forwardBtn = page
        .locator('button', { hasText: /^\s*转\s*发\s*$/ })
        .first();
      if (await forwardBtn.count()) {
        await forwardBtn.click();
        await sleep(1000);
        // 二次确认弹窗
        const confirmDlg = page
          .locator('[role="dialog"]:visible, [role="alertdialog"]:visible')
          .last();
        await confirmDlg.waitFor({ timeout: 5000 }).catch(() => {});
        const confirmText = await confirmDlg
          .evaluate((el) => el.innerText)
          .catch(() => '');
        const hasConfirmText = confirmText.includes('转发给自己的下属');
        await confirmDlg
          .locator('button', { hasText: /确\s*定/ })
          .first()
          .click()
          .catch(() => {});
        await sleep(2500);
        // 观察全局提示
        const msgText = await page
          .locator('.ant-message')
          .last()
          .textContent()
          .catch(() => '');
        const file = await shot('07-announcement-my');
        record(
          'antd',
          '07-我的公告转发',
          hasConfirmText && itemErrors.length === 0,
          {
            screenshot: file,
            note: itemNote(
              `确认文案=${hasConfirmText}，点击后提示=${(msgText || '无').slice(0, 60)}（旧后端接口缺失时报错提示属预期）`,
            ),
          },
        );
      } else {
        const file = await shot('07-announcement-my');
        record('antd', '07-我的公告转发', true, {
          screenshot: file,
          note: itemNote(
            '无可见转发按钮（可能均已转发或无数据），仅确认页面渲染',
          ),
        });
      }
    } catch (error) {
      const file = await shot('07-announcement-my').catch(() => undefined);
      record('antd', '07-我的公告转发', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 8a. 云盘权限表单提示图标 ====================
    beginItem();
    try {
      await goto('/oa/file');
      const shareBtn = page
        .locator('button', { hasText: /^\s*共\s*享\s*$/ })
        .first();
      if (await shareBtn.count()) {
        await shareBtn.click();
        await sleep(2000);
        const listDlg = dialog();
        await listDlg
          .locator('button', { hasText: /新\s*增/ })
          .first()
          .click();
        await sleep(1500);
        const formDlg = dialog();
        // label 文本与图标检测
        const labelInfo = await formDlg.locator('label').evaluateAll((els) =>
          els.map((el) => ({
            text: el.textContent.replaceAll(/[\s*:]/g, ''),
            hasIcon: !!el.querySelector('svg'),
          })),
        );
        const levelLabel = labelInfo.find((l) => l.text === '权限');
        const inheritLabel = labelInfo.find((l) => l.text === '继承权限');
        // 悬停「权限」label 的图标出现 tooltip
        let tooltipOk = null;
        let tooltipText = '';
        if (levelLabel?.hasIcon) {
          const icon = formDlg
            .locator('label')
            .filter({ hasText: /^[\s*]*权限[\s:]*$/ })
            .first()
            .locator('svg')
            .first();
          await icon.hover().catch(() => {});
          await sleep(1000);
          const tip = page.locator('[role="tooltip"]:visible').last();
          if (await tip.count()) {
            tooltipText = ((await tip.textContent()) || '').trim();
            tooltipOk = tooltipText.includes('查看权限');
          } else {
            tooltipOk = false;
          }
        }
        const file = await shot('08-file-permission-form');
        record(
          'antd',
          '08-云盘权限表单提示',
          !!levelLabel?.hasIcon &&
            !!inheritLabel?.hasIcon &&
            tooltipOk === true,
          {
            screenshot: file,
            note: itemNote(
              `权限图标=${!!levelLabel?.hasIcon}，继承权限图标=${!!inheritLabel?.hasIcon}，悬停tooltip=${tooltipOk}（${tooltipText.slice(0, 40)}）；labels=${labelInfo.map((l) => l.text + (l.hasIcon ? '(i)' : '')).join('|')}`,
            ),
          },
        );
        await closeTopDialog();
        await closeTopDialog();
      } else {
        const file = await shot('08-file-permission');
        record('antd', '08-云盘权限表单提示', false, {
          screenshot: file,
          note: itemNote('无可见共享按钮（需要管理权限的文件行）'),
        });
      }
    } catch (error) {
      const file = await shot('08-file-permission-form').catch(() => undefined);
      record('antd', '08-云盘权限表单提示', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 8b. 云盘下载走 blob ====================
    beginItem();
    try {
      await goto('/oa/file');
      const downloadBtn = page
        .locator('button', { hasText: /^\s*下\s*载\s*$/ })
        .first();
      if (await downloadBtn.count()) {
        let downloadOk = null;
        let filename = '';
        try {
          const [dl] = await Promise.all([
            page.waitForEvent('download', { timeout: 15_000 }),
            downloadBtn.click(),
          ]);
          filename = dl.suggestedFilename();
          downloadOk = !!filename;
        } catch {
          downloadOk = false;
        }
        const file = await shot('08-file-download');
        record('antd', '08-云盘下载blob', downloadOk === true, {
          screenshot: file,
          note: itemNote(`下载事件=${downloadOk}，文件名=${filename || '无'}`),
        });
      } else {
        const file = await shot('08-file-download');
        record('antd', '08-云盘下载blob', false, {
          screenshot: file,
          note: itemNote('无可见下载按钮（无可下载文件行）'),
        });
      }
    } catch (error) {
      const file = await shot('08-file-download').catch(() => undefined);
      record('antd', '08-云盘下载blob', false, {
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
