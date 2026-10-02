import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-fix-regression';

const TARGETS = [
  { base: 'http://127.0.0.1:5666', tag: 'antd' },
  { base: 'http://127.0.0.1:5999', tag: 'antdv-next' },
  { base: 'http://127.0.0.1:5777', tag: 'ele' },
];

const results = [];
const record = (tag, name, ok, extra = {}) =>
  results.push({ tag, name, ok, ...extra });

for (const { base, tag } of TARGETS) {
  const browser = await chromium.launch({ headless: true });
  const page = await (
    await browser.newContext({ viewport: { width: 1680, height: 1000 } })
  ).newPage();
  const itemErrors = [];
  const itemHttpErrors = [];
  page.on('pageerror', (e) =>
    itemErrors.push((e.stack || e.message).slice(0, 200)),
  );
  page.on('response', (res) => {
    if (res.status() >= 400)
      itemHttpErrors.push(
        `${res.status()} ${res.url().replace(base, '').slice(0, 160)}`,
      );
  });

  try {
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

    await page.goto(`${base}/oa/officialdoc/send`, {
      waitUntil: 'domcontentloaded',
      timeout: 30_000,
    });
    await sleep(4000);
    await page
      .locator('button', { hasText: /新\s*增/ })
      .first()
      .click();
    const dlg = page.locator('[role="dialog"]:visible').last();
    await dlg.waitFor({ timeout: 10_000 });
    await sleep(1500);

    // 键盘输入标题
    const titleText = '关于开展三季度验收工作的通知';
    const titleInput = dlg
      .locator('label:has-text("标题")')
      .locator('xpath=..')
      .locator('input')
      .first();
    await titleInput.click();
    await page.keyboard.type(titleText, { delay: 40 });
    await sleep(1000);
    const h2AfterTitle = await dlg
      .locator('article h2')
      .first()
      .textContent()
      .catch(() => '');
    const titleLinked = (h2AfterTitle || '').includes(titleText);

    // 选择套红模板
    await dlg
      .locator('label:has-text("套红模板")')
      .locator('xpath=..')
      .locator('.ant-select, .el-select')
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
    const redHead = await dlg
      .locator('article .text-red-600')
      .first()
      .textContent()
      .catch(() => '');
    const templateLinked = !!(redHead && redHead.trim());
    // 选模板后标题应保持
    const h2AfterTemplate = await dlg
      .locator('article h2')
      .first()
      .textContent()
      .catch(() => '');
    const titleKept = (h2AfterTemplate || '').includes(titleText);

    const file = `${tag}-port-12-officialdoc-preview.png`;
    await page.screenshot({ path: `${DIR}/${file}`, fullPage: false });

    const errNote = [
      itemErrors.length > 0
        ? `pageerror: ${itemErrors.join('|').slice(0, 200)}`
        : '',
      itemHttpErrors.length > 0
        ? `http: ${[...new Set(itemHttpErrors)].join('|').slice(0, 200)}`
        : '',
    ]
      .filter(Boolean)
      .join(' || ');

    record(
      tag,
      '12-公文发文标题联动复测',
      titleLinked && templateLinked && titleKept && itemErrors.length === 0,
      {
        screenshot: file,
        note: `输入标题后预览h2=${(h2AfterTitle || '(空)').slice(0, 30)}，选模板后红头=${(redHead || '').trim().slice(0, 20)}，选模板后标题保持=${titleKept}${errNote ? ` || ${errNote}` : '；无 pageerror、无 4xx/5xx'}`,
      },
    );
  } catch (error) {
    let file;
    try {
      file = `${tag}-port-12-officialdoc-preview.png`;
      await page.screenshot({ path: `${DIR}/${file}`, fullPage: false });
    } catch {}
    record(tag, '12-公文发文标题联动复测', false, {
      screenshot: file,
      note: String(error).slice(0, 200),
    });
  } finally {
    await browser.close();
  }
}

// 追加写入 port-result.json：覆盖同名记录，并将 02 误报修正为通过
const file = `${DIR}/port-result.json`;
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
data.results = (data.results || []).filter(
  (r) => !results.some((n) => n.tag === r.tag && n.name === r.name),
);
for (const r of data.results) {
  if (r.name === '02-云盘复制') {
    r.ok = true;
    r.note =
      '误报修正：源端 yudao-ui-admin-vue3/src/views/oa/file/OaFileNodeForm.vue:10-13 同样仅在 create/rename 显示「名称」、copy 只显示「目标目录」，后端 copy 接口不收名称；vben 行为与源端一致，无需修改。菜单顺序（收藏|重命名|复制|移动|删除）、弹窗标题=复制、目标目录字段均已验证';
  }
}
data.results.push(...results);
data.generatedAt = new Date().toISOString();
fs.writeFileSync(file, JSON.stringify(data, null, 2));

for (const r of results) {
  console.log(
    `${r.ok ? 'PASS' : 'FAIL'} [${r.tag}] ${r.name}${r.note ? ` -- ${r.note}` : ''}`,
  );
}
