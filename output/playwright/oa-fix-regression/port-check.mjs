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
    const file = `${tag}-port-${name}.png`;
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
  const dialog = () => page.locator('[role="dialog"]:visible').last();
  /** vben 表单中按 label 定位字段控件 */
  const fieldControl = (dlg, label, control) =>
    dlg
      .locator(`label:has-text("${label}")`)
      .locator('xpath=..')
      .locator(control);
  const dialogLabels = (dlg) => dlg.locator('label').allTextContents();

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

    // ==================== 1. 公文发文新增：并排预览（三端） ====================
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

      // 弹窗宽度约 90%
      const box = await dlg.boundingBox();
      const ratio = box ? box.width / 1680 : 0;
      // 预览面板存在
      const preview = dlg.locator('article.bg-white');
      const hasPreview = (await preview.count()) > 0;
      // 无「标准预览」按钮
      const hasStdPreviewBtn =
        (await page.locator('text=标准预览').count()) > 0;
      // 输入标题联动预览
      await fieldControl(dlg, '标题', 'input')
        .first()
        .fill('关于三季度 OA 验收的通知');
      await sleep(800);
      const previewTitle = await preview
        .locator('h2')
        .first()
        .textContent()
        .catch(() => '');
      const titleLinked = (previewTitle || '').includes(
        '关于三季度 OA 验收的通知',
      );
      // 选择套红模板联动
      await fieldControl(dlg, '套红模板', '.ant-select, .el-select')
        .first()
        .click();
      await sleep(1000);
      await page
        .locator(
          '.ant-select-dropdown:visible .ant-select-item-option, .el-select-dropdown:visible .el-select-dropdown__item',
        )
        .first()
        .click();
      await sleep(1500);
      const redHead = await preview
        .locator('.text-red-600')
        .first()
        .textContent()
        .catch(() => '');
      const templateLinked = !!(redHead && redHead.trim());
      // 正式公文上传项（pdf）
      const labels = (await dialogLabels(dlg)).join('|');
      const hasOfficialFile = labels.includes('正式公文');
      const dlgText = await dlg.evaluate((el) => el.innerText);
      const pdfHint = dlgText.includes('.pdf');

      const file = await shot('01-officialdoc-send-form');
      const ok =
        ratio > 0.8 &&
        hasPreview &&
        !hasStdPreviewBtn &&
        titleLinked &&
        templateLinked &&
        hasOfficialFile &&
        pdfHint;
      record(tag, '01-公文发文并排预览', ok, {
        screenshot: file,
        note: itemNote(
          `宽度比=${ratio.toFixed(2)}（预期≈0.90），预览面板=${hasPreview}，无标准预览按钮=${!hasStdPreviewBtn}，标题联动=${titleLinked}（预览h2=${(previewTitle || '').slice(0, 30)}），模板联动=${templateLinked}（红头=${(redHead || '').trim().slice(0, 20)}），正式公文=${hasOfficialFile}，pdf提示=${pdfHint}`,
        ),
      });
      await page.keyboard.press('Escape');
      await sleep(500);
    } catch (error) {
      const file = await shot('01-officialdoc-send-form').catch(
        () => undefined,
      );
      record(tag, '01-公文发文并排预览', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 4. 通讯录头像/性别/分享人列（三端） ====================
    beginItem();
    try {
      await goto('/oa/contact');
      const headers1 = await visibleHeaders();
      const hasAvatar = headers1.includes('头像');
      const hasSex = headers1.includes('性别');
      const placeholder = await page
        .locator('input[placeholder]')
        .first()
        .getAttribute('placeholder')
        .catch(() => '');
      const placeholderOk = placeholder === '请输入姓名、拼音、手机或公司';
      const file1 = await shot('04-contact-mine');

      await page
        .locator(
          '.ant-menu-item:has-text("共享与我"), .el-menu-item:has-text("共享与我")',
        )
        .first()
        .click();
      await sleep(4000);
      const headers2 = await visibleHeaders();
      const hasSharer = headers2.includes('分享人');
      const file2 = await shot('04-contact-received');

      record(
        tag,
        '04-通讯录头像性别分享人列',
        hasAvatar &&
          hasSex &&
          hasSharer &&
          placeholderOk &&
          itemErrors.length === 0,
        {
          screenshot: `${file1}, ${file2}`,
          note: itemNote(
            `头像=${hasAvatar}，性别=${hasSex}，分享人=${hasSharer}，占位符=${placeholder}；共享与我列=${headers2.join('|')}`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('04-contact').catch(() => undefined);
      record(tag, '04-通讯录头像性别分享人列', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    if (tag !== 'antd') return;

    // ==================== 2. 云盘复制 ====================
    beginItem();
    try {
      await goto('/oa/file');
      // 第一行的「更多」下拉
      const moreBtn = page
        .locator('.vxe-table--body-wrapper tbody tr')
        .first()
        .locator('button', { hasText: /更\s*多/ })
        .first();
      await moreBtn.click();
      await sleep(800);
      const menuTexts = await page
        .locator('.ant-dropdown-menu:visible .ant-dropdown-menu-item')
        .allTextContents();
      const idxRename = menuTexts.findIndex((t) => t.includes('重命名'));
      const idxCopy = menuTexts.findIndex((t) => t.includes('复制'));
      const idxMove = menuTexts.findIndex((t) => t.includes('移动'));
      const orderOk =
        idxCopy !== -1 &&
        idxRename !== -1 &&
        idxMove !== -1 &&
        idxRename < idxCopy &&
        idxCopy < idxMove;
      // 点击复制
      await page
        .locator('.ant-dropdown-menu:visible .ant-dropdown-menu-item', {
          hasText: '复制',
        })
        .first()
        .click();
      await sleep(1200);
      const dlg = dialog();
      const title = await dlg
        .locator('[data-slot="dialog-title"], .ant-modal-title')
        .first()
        .textContent()
        .catch(() => '');
      const labels = (await dialogLabels(dlg)).join('|');
      const file = await shot('02-file-copy');
      record(
        'antd',
        '02-云盘复制',
        orderOk &&
          title.includes('复制') &&
          labels.includes('名称') &&
          labels.includes('目标目录'),
        {
          screenshot: file,
          note: itemNote(
            `菜单=${menuTexts.join('|')}（顺序正确=${orderOk}），弹窗标题=${title}，字段=${labels}`,
          ),
        },
      );
      await page.keyboard.press('Escape');
      await sleep(500);
    } catch (error) {
      const file = await shot('02-file-copy').catch(() => undefined);
      record('antd', '02-云盘复制', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 3. 还车表单字段 ====================
    beginItem();
    try {
      await goto('/oa/vehicle/return');
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1000);
      const labels = (await dialogLabels(dlg)).join('|');
      const need = ['实际出车时间', '出车地点', '用车事由'];
      const missing = need.filter((l) => !labels.includes(l));
      const hasPassenger =
        labels.includes('乘车人') || labels.includes('随行人');
      const file = await shot('03-vehicle-return-form');
      record('antd', '03-还车表单字段', missing.length === 0 && hasPassenger, {
        screenshot: file,
        note: itemNote(
          `缺失=${missing.join(',') || '无'}，乘车人/随行人=${hasPassenger}；字段=${labels}`,
        ),
      });
      await page.keyboard.press('Escape');
      await sleep(500);
    } catch (error) {
      const file = await shot('03-vehicle-return-form').catch(() => undefined);
      record('antd', '03-还车表单字段', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 5. 日程列表搜索区与列 ====================
    beginItem();
    try {
      await goto('/oa/schedule/list');
      const formLabels = (
        await page.locator('.vxe-grid--form-wrapper label').allTextContents()
      ).join('|');
      const hasPriority = formLabels.includes('优先级');
      const hasStartTime = formLabels.includes('开始时间');
      const headers = await visibleHeaders();
      const need = ['发布人', '部门', '发布时间'];
      const missing = need.filter((h) => !headers.includes(h));
      const file = await shot('05-schedule-list');
      record(
        'antd',
        '05-日程列表筛选与列',
        hasPriority && hasStartTime && missing.length === 0,
        {
          screenshot: file,
          note: itemNote(
            `优先级=${hasPriority}，开始时间=${hasStartTime}，缺列=${missing.join(',') || '无'}；搜索区=${formLabels}；列=${headers.join('|')}`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('05-schedule-list').catch(() => undefined);
      record('antd', '05-日程列表筛选与列', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 6. 工作汇报统计空态与部门加载 ====================
    beginItem();
    try {
      await goto('/oa/work-report/statistics');
      await sleep(1000);
      const bodyText1 = await page.evaluate(() => document.body.innerText);
      const emptyOk = bodyText1.includes('请选择部门查看汇报统计');
      const fileEmpty = await shot('06-workreport-statistics-empty');
      // 选择第一个部门
      await page
        .locator('.ant-tree-node-content-wrapper, .el-tree-node__content')
        .first()
        .click();
      await sleep(3500);
      const bodyText2 = await page.evaluate(() => document.body.innerText);
      const loadedOk =
        !bodyText2.includes('请选择部门查看汇报统计') ||
        bodyText2.includes('汇报');
      const fileLoaded = await shot('06-workreport-statistics-dept');
      record('antd', '06-工作汇报统计空态与加载', emptyOk && loadedOk, {
        screenshot: `${fileEmpty}, ${fileLoaded}`,
        note: itemNote(`空态文案=${emptyOk}，选部门后加载=${loadedOk}`),
      });
    } catch (error) {
      const file = await shot('06-workreport-statistics').catch(
        () => undefined,
      );
      record('antd', '06-工作汇报统计空态与加载', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 7. 会议室选择弹窗无可用状态筛选 ====================
    beginItem();
    try {
      await goto('/oa/meetingroom/booking');
      await page
        .locator('button', { hasText: /新\s*增/ })
        .first()
        .click();
      const formDlg = dialog();
      await formDlg.waitFor({ timeout: 10_000 });
      await sleep(1000);
      // 点击会议室输入框打开选择弹窗
      await formDlg
        .locator('input[placeholder*="请选择会议室"]')
        .first()
        .click();
      await sleep(2500);
      const selectDlg = page
        .locator('.ant-modal:visible, [role="dialog"]:visible')
        .last();
      const formLabels = (
        await selectDlg
          .locator('.vxe-grid--form-wrapper label')
          .allTextContents()
      ).join('|');
      const hasStatusFilter = formLabels.includes('可用状态');
      const keep = ['名称', '位置', '类型'].filter((l) =>
        formLabels.includes(l),
      );
      const file = await shot('07-meetingroom-select');
      record(
        'antd',
        '07-会议室选择弹窗筛选',
        !hasStatusFilter && keep.length === 3,
        {
          screenshot: file,
          note: itemNote(
            `可用状态筛选=${hasStatusFilter}，保留项=${keep.join('/')}；搜索区=${formLabels}`,
          ),
        },
      );
      await page.keyboard.press('Escape');
      await sleep(400);
      await page.keyboard.press('Escape');
      await sleep(400);
    } catch (error) {
      const file = await shot('07-meetingroom-select').catch(() => undefined);
      record('antd', '07-会议室选择弹窗筛选', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 8. 印章申请搜索区与列 ====================
    beginItem();
    try {
      await goto('/oa/seal/apply');
      const formLabels = (
        await page.locator('.vxe-grid--form-wrapper label').allTextContents()
      ).join('|');
      // 印章字段应为选择器而非输入框
      const sealField = page
        .locator('.vxe-grid--form-wrapper label:has-text("印章")')
        .locator('xpath=..');
      const sealIsSelect =
        (await sealField.locator('.ant-select').count()) > 0 &&
        (await sealField.locator('input:not(.ant-select input)').count()) === 0;
      const hasDept = formLabels.includes('申请部门');
      const hasCreateTime = formLabels.includes('创建时间');
      const headers = await visibleHeaders();
      const hasSealNo = headers.includes('印章编号');
      const hasUrgent = headers.includes('紧急');
      const file = await shot('08-seal-apply');
      record(
        'antd',
        '08-印章申请搜索区与列',
        sealIsSelect && hasDept && hasCreateTime && hasSealNo && hasUrgent,
        {
          screenshot: file,
          note: itemNote(
            `印章选择器=${sealIsSelect}，申请部门=${hasDept}，创建时间=${hasCreateTime}，印章编号列=${hasSealNo}，紧急列=${hasUrgent}；搜索区=${formLabels}`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('08-seal-apply').catch(() => undefined);
      record('antd', '08-印章申请搜索区与列', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 9. 邮箱账号编辑禁用字段 ====================
    beginItem();
    try {
      await goto('/oa/mail/account');
      await page
        .locator('.vxe-table--body-wrapper tbody tr')
        .first()
        .locator('button', { hasText: /修\s*改/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1000);
      const addrDisabled = await fieldControl(dlg, '邮箱地址', 'input')
        .first()
        .isDisabled();
      const serviceDisabled =
        (await fieldControl(
          dlg,
          '邮箱服务',
          '.ant-select.ant-select-disabled',
        ).count()) > 0 ||
        (await fieldControl(
          dlg,
          '邮箱服务',
          '[aria-disabled="true"]',
        ).count()) > 0;
      const loginDisabled = await fieldControl(dlg, '登录名', 'input')
        .first()
        .isDisabled();
      const file = await shot('09-mail-account-edit');
      record(
        'antd',
        '09-邮箱账号编辑禁用字段',
        addrDisabled && serviceDisabled && loginDisabled,
        {
          screenshot: file,
          note: itemNote(
            `邮箱地址禁用=${addrDisabled}，邮箱服务禁用=${serviceDisabled}，登录名禁用=${loginDisabled}`,
          ),
        },
      );
      await page.keyboard.press('Escape');
      await sleep(500);
    } catch (error) {
      const file = await shot('09-mail-account-edit').catch(() => undefined);
      record('antd', '09-邮箱账号编辑禁用字段', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 10. 工作台面板与任务排行柱状图 ====================
    beginItem();
    try {
      await goto('/oa/home');
      await sleep(2000);
      const collapseBtns = await page
        .locator('button[aria-label^="收起"], button[aria-label^="展开"]')
        .count();
      const closeBtns = await page
        .locator('button[aria-label^="关闭"]')
        .count();
      // 任务完成排行面板内应有 canvas（柱状图）
      const bodyText = await page.evaluate(() => document.body.innerText);
      const hasRanking = bodyText.includes('任务完成排行');
      const canvasCount = await page.locator('canvas').count();
      const file = await shot('10-home');
      record(
        'antd',
        '10-工作台面板与排行图',
        collapseBtns > 0 && closeBtns > 0 && hasRanking && canvasCount > 0,
        {
          screenshot: file,
          note: itemNote(
            `收起/展开按钮=${collapseBtns}，关闭按钮=${closeBtns}，任务完成排行=${hasRanking}，canvas=${canvasCount}`,
          ),
        },
      );
    } catch (error) {
      const file = await shot('10-home').catch(() => undefined);
      record('antd', '10-工作台面板与排行图', false, {
        screenshot: file,
        note: itemNote(String(error).slice(0, 200)),
      });
    }

    // ==================== 11. 办公用品入库表单 ====================
    beginItem();
    try {
      await goto('/oa/supply/item');
      await page
        .locator('.vxe-table--body-wrapper tbody tr')
        .first()
        .locator('button', { hasText: /入\s*库/ })
        .first()
        .click();
      const dlg = dialog();
      await dlg.waitFor({ timeout: 10_000 });
      await sleep(1000);
      const labels = (await dialogLabels(dlg)).join('|');
      const hasQuantity =
        labels.includes('入库数量') || labels.includes('数量');
      const hasRemark = labels.includes('备注');
      const file = await shot('11-supply-instock');
      record('antd', '11-办公用品入库表单', hasQuantity && !hasRemark, {
        screenshot: file,
        note: itemNote(
          `数量字段=${hasQuantity}，无备注=${!hasRemark}；字段=${labels}`,
        ),
      });
      await page.keyboard.press('Escape');
      await sleep(500);
    } catch (error) {
      const file = await shot('11-supply-instock').catch(() => undefined);
      record('antd', '11-办公用品入库表单', false, {
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
  `${DIR}/port-result.json`,
  JSON.stringify({ generatedAt: new Date().toISOString(), results }, null, 2),
);
for (const r of results) {
  console.log(
    `${r.ok ? 'PASS' : 'FAIL'} [${r.tag}] ${r.name}${r.note ? ` -- ${r.note}` : ''}`,
  );
}
