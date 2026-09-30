import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium, request as playwrightRequest } from 'playwright';

const outDir = path.resolve('output/playwright/vben-p0-targeted');
await fs.mkdir(outDir, { recursive: true });

const apps = [
  { key: 'antdv-next', baseUrl: 'http://127.0.0.1:5999' },
  { key: 'ele', baseUrl: 'http://127.0.0.1:5777' },
];

const cases = [];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function saveJson(name, data) {
  await fs.writeFile(path.join(outDir, name), JSON.stringify(data, null, 2));
}

async function login(page, app) {
  await page.goto(`${app.baseUrl}/auth/login`, {
    waitUntil: 'domcontentloaded',
  });
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 25_000,
  });
  await page.waitForLoadState('domcontentloaded');
  await sleep(1500);
}

async function readAuth(page) {
  return page.evaluate(() => {
    const local = { ...localStorage };
    const session = { ...sessionStorage };
    return { local, session };
  });
}

function findToken(storage) {
  const all = { ...storage.local, ...storage.session };
  for (const [key, value] of Object.entries(all)) {
    if (key.toLowerCase().includes('access') && /[a-f0-9]{32}/i.test(value)) {
      return value.match(/[a-f0-9]{32}/i)?.[0];
    }
    if (typeof value === 'string' && value.includes('accessToken')) {
      try {
        const json = JSON.parse(value);
        const token = JSON.stringify(json).match(/[a-f0-9]{32}/i)?.[0];
        if (token) return token;
      } catch {}
    }
  }
  return '';
}

async function createFreshPayOrder(page, app) {
  const token = findToken(await readAuth(page));
  const api = await playwrightRequest.newContext({
    baseURL: app.baseUrl,
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'tenant-id': '1',
    },
  });
  try {
    const createResp = await api.post('/admin-api/pay/demo-order/create', {
      data: { spuId: 1 },
    });
    const createBody = await createResp.json();
    const demoOrderId = createBody.data;
    const pageResp = await api.get(
      '/admin-api/pay/demo-order/page?pageNo=1&pageSize=1',
    );
    const pageBody = await pageResp.json();
    const demoOrder =
      pageBody.data?.list?.find((item) => item.id === demoOrderId) ||
      pageBody.data?.list?.[0];
    return {
      createBody,
      demoOrderId,
      pageBody,
      payOrderId: demoOrder?.payOrderId,
    };
  } finally {
    await api.dispose();
  }
}

async function runAppCase(app, name, fn) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  page.setDefaultTimeout(12_000);
  page.setDefaultNavigationTimeout(25_000);

  const requests = [];
  const consoleMessages = [];
  page.on('console', (msg) => {
    const text = msg.text();
    if (
      text.includes('menu name duplicate:') ||
      text.includes('[StorageManager] empty prefix') ||
      text.includes('Download the Vue Devtools')
    ) {
      return;
    }
    consoleMessages.push({ type: msg.type(), text });
  });
  page.on('pageerror', (error) => {
    consoleMessages.push({ type: 'pageerror', text: error.message });
  });
  page.on('requestfinished', async (request) => {
    const response = await request.response();
    const url = request.url();
    if (!url.includes('/admin-api/')) return;
    let body = undefined;
    try {
      const contentType = response?.headers()['content-type'] || '';
      if (contentType.includes('application/json')) {
        body = await response?.json();
      }
    } catch {
      body = undefined;
    }
    requests.push({
      method: request.method(),
      postData: request.postData(),
      status: response?.status(),
      url,
      body,
    });
  });
  page.on('requestfailed', (request) => {
    const url = request.url();
    if (!url.includes('/admin-api/')) return;
    requests.push({
      method: request.method(),
      failure: request.failure()?.errorText,
      postData: request.postData(),
      url,
    });
  });

  const record = {
    app: app.key,
    name,
    status: 'unknown',
    notes: [],
    console: consoleMessages,
    requests: [],
    screenshot: path.join(outDir, `${app.key}-${name}.png`),
  };

  try {
    await login(page, app);
    await fn({ app, page, record, requests });
    if (record.status === 'unknown') record.status = 'pass';
  } catch (error) {
    record.status = 'fail';
    record.notes.push(error.stack || error.message);
  } finally {
    record.console = consoleMessages;
    await page
      .screenshot({ fullPage: true, path: record.screenshot })
      .catch(() => {});
    cases.push(record);
    await saveJson('results.json', cases);
    await context.close();
    await browser.close();
  }
}

async function gotoRoute(page, app, route) {
  await page.goto(`${app.baseUrl}${route}`, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('domcontentloaded');
  await sleep(1800);
}

function pickRequests(requests, fragment) {
  return requests.filter((item) => item.url.includes(fragment));
}

async function clickText(page, text) {
  const locator = page.getByText(text, { exact: false }).first();
  await locator.waitFor({ state: 'visible' });
  await locator.click();
}

async function testPayCashier({ app, page, record, requests }) {
  const payOrder = await createFreshPayOrder(page, app);
  if (!payOrder.payOrderId) {
    record.status = 'blocked';
    record.notes.push(
      `无法创建有效 demo 支付单：${JSON.stringify(payOrder.createBody || payOrder.pageBody || {})}`,
    );
    return;
  }
  record.notes.push(
    `demoOrderId=${payOrder.demoOrderId}, payOrderId=${payOrder.payOrderId}`,
  );
  await gotoRoute(page, app, `/pay/cashier?id=${payOrder.payOrderId}`);
  const bodyText = await page.locator('body').innerText();
  const detailsRendered =
    bodyText.includes('支付单号') &&
    bodyText.includes('商品标题') &&
    (await page
      .locator('.ant-descriptions-item, .el-descriptions__cell')
      .count()) > 0;
  if (!detailsRendered) {
    record.status = 'fail';
    record.notes.push('支付详情 Descriptions 未渲染');
    return;
  }

  await clickText(page, '微信扫码支付');
  await sleep(1800);
  record.requests = pickRequests(requests, '/pay/order/submit');
  const submitBody = record.requests.at(-1)?.body;
  const modalText = await page.locator('body').innerText();
  const hasQrElement =
    (await page
      .locator('canvas, .ant-qrcode, img[src^="data:image"]')
      .count()) > 0;

  if (submitBody?.code && submitBody.code !== 0) {
    record.status = 'blocked';
    record.notes.push(
      `后端支付提交返回错误：${submitBody.msg || submitBody.code}`,
    );
    return;
  }
  if (
    submitBody?.data?.displayMode === 'qr_code' ||
    submitBody?.data?.displayMode === 'QR_CODE'
  ) {
    record.status =
      modalText.includes('请使用微信') && hasQrElement ? 'pass' : 'fail';
    if (record.status === 'fail') {
      record.notes.push('后端返回二维码展示模式，但页面未显示二维码');
    }
    return;
  }
  record.status = 'blocked';
  record.notes.push(
    `当前测试订单/渠道未返回二维码模式：${JSON.stringify(submitBody?.data || submitBody || {})}`,
  );
}

async function testMpMessage({ app, page, record, requests }) {
  await gotoRoute(page, app, '/mp/message');
  const pageRequests = pickRequests(requests, '/mp/message/page');
  record.requests = pageRequests;
  const bodyText = await page.locator('body').innerText();
  const hasVoiceMapping = await page
    .locator('body')
    .evaluate(() => document.body.innerHTML.includes('WxVoicePlayer'));
  const hasVideoMapping = await page
    .locator('body')
    .evaluate(() => document.body.innerHTML.includes('WxVideoPlayer'));
  record.notes.push(
    `component markers in DOM: voice=${hasVoiceMapping}, video=${hasVideoMapping}`,
  );
  if (pageRequests.length === 0) {
    record.status = 'fail';
    record.notes.push('未触发 mp/message/page 请求');
    return;
  }
  if (bodyText.includes('未知消息类型') || bodyText.includes('未知事件类型')) {
    record.status = 'blocked';
    record.notes.push('页面数据存在未知消息/事件类型，需要结合数据判断');
    return;
  }
  record.status = 'pass';
}

async function testErpStock({ app, page, record, requests }, route) {
  await gotoRoute(page, app, route);
  await clickText(page, '新增');
  await sleep(2200);
  const bodyText = await page.locator('body').innerText();
  const productRequests = pickRequests(requests, '/erp/product/simple-list');
  const warehouseRequests = pickRequests(
    requests,
    '/erp/warehouse/simple-list',
  );
  record.requests = [...productRequests, ...warehouseRequests];

  const productData = productRequests.at(-1)?.body?.data || [];
  const warehouseData = warehouseRequests.at(-1)?.body?.data || [];
  const placeholderOk =
    bodyText.includes('请选择产品') &&
    (bodyText.includes('请选择仓库') ||
      bodyText.includes('请选择调出仓库') ||
      bodyText.includes('请选择调入仓库'));

  if (!placeholderOk) {
    record.status = 'fail';
    record.notes.push('新增弹窗未显示产品/仓库下拉占位');
    return;
  }
  if (productData.length === 0 || warehouseData.length === 0) {
    record.status = 'blocked';
    record.notes.push(
      `接口数据不足：product=${productData.length}, warehouse=${warehouseData.length}`,
    );
    return;
  }

  const selectableCount = await page
    .getByText(/请选择(调出仓库|调入仓库|仓库|产品)/)
    .count();
  record.notes.push(
    `接口数据：product=${productData.length}, warehouse=${warehouseData.length}; 下拉占位数=${selectableCount}`,
  );
  if (selectableCount === 0) {
    record.status = 'fail';
    record.notes.push('页面没有渲染产品/仓库下拉占位');
    return;
  }
  record.status = 'pass';
}

async function testSystemMenu({ app, page, record }) {
  await gotoRoute(page, app, '/system/menu');
  await clickText(page, '新增');
  await sleep(1200);
  const bodyText = await page.locator('body').innerText();
  if (!bodyText.includes('组件名称')) {
    record.status = 'fail';
    record.notes.push('菜单新增表单未显示组件名称');
    return;
  }
  const result = await page.evaluate(() => {
    const labels = [
      ...document.querySelectorAll(
        'label, .ant-form-item-label, .el-form-item__label',
      ),
    ];
    const componentLabel = labels.find((node) =>
      node.textContent?.includes('组件名称'),
    );
    const container =
      componentLabel?.closest(
        '.ant-form-item, .form-item, [data-slot="form-item"], .el-form-item',
      ) || componentLabel?.parentElement?.parentElement;
    const input = container?.querySelector('input');
    if (!(input instanceof HTMLInputElement)) return { found: false };
    input.focus();
    input.value = 'system/menu';
    input.dispatchEvent(
      new InputEvent('input', { bubbles: true, data: 'system/menu' }),
    );
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return { found: true, value: input.value };
  });
  await sleep(1000);
  const popupText = await page.locator('body').innerText();
  if (!result.found) {
    record.status = 'fail';
    record.notes.push('未能定位组件名称输入框');
    return;
  }
  record.status = popupText.includes('system/menu/index') ? 'pass' : 'fail';
  if (record.status === 'fail') {
    record.notes.push('输入 system/menu 后没有出现 system/menu/index 候选');
  }
}

async function testImFacePack({ app, page, record, requests }) {
  await gotoRoute(page, app, '/im/face/pack');
  await page
    .waitForResponse(
      (resp) => resp.url().includes('/im/manager/face-pack/page'),
      {
        timeout: 20_000,
      },
    )
    .catch(() => {});
  const bodyText = await page.locator('body').innerText();
  if (!bodyText.includes('表情包列表')) {
    record.status = 'fail';
    record.notes.push('表情包列表未渲染');
    return;
  }
  const tableManage = page
    .locator('.vxe-table--body, .vxe-table')
    .getByText('管理表情', { exact: false })
    .first();
  if ((await tableManage.count()) === 0) {
    record.status = 'blocked';
    record.notes.push('表格内未找到“管理表情”操作');
    return;
  }
  await tableManage.click();
  await page
    .waitForResponse(
      (resp) => resp.url().includes('/im/manager/face-pack-item/page'),
      {
        timeout: 20_000,
      },
    )
    .catch(() => {});
  await sleep(1000);
  record.requests = pickRequests(requests, '/im/manager/face-pack-item/page');
  const afterText = await page.locator('body').innerText();
  if (record.requests.length === 0) {
    record.status = 'fail';
    record.notes.push('打开抽屉后未触发表情列表请求');
    return;
  }
  record.status = afterText.includes('表情列表') ? 'pass' : 'fail';
  if (record.status === 'fail') {
    record.notes.push('已触发请求，但抽屉未显示表情列表');
  }
}

async function testInfraFileConfig({ app, page, record, requests }) {
  await gotoRoute(page, app, '/infra/file/file-config');
  await page
    .waitForResponse((resp) => resp.url().includes('/infra/file-config/page'), {
      timeout: 20_000,
    })
    .catch(() => {});
  const bodyText = await page.locator('body').innerText();
  if (!bodyText.includes('文件配置')) {
    record.status = 'fail';
    record.notes.push('文件配置页面未渲染');
    return;
  }
  const clicked = await page.evaluate(() => {
    const buttons = [...document.querySelectorAll('button, a, span')].filter(
      (node) => node.textContent?.trim().includes('主配置'),
    );
    const target = buttons.find((node) => {
      const el = node.closest('button, a') || node;
      const disabled =
        el.hasAttribute('disabled') ||
        el.getAttribute('aria-disabled') === 'true' ||
        el.className.includes('disabled');
      return !disabled;
    });
    if (!(target instanceof HTMLElement)) return false;
    const clickable = target.closest('button, a') || target;
    clickable.dispatchEvent(
      new MouseEvent('click', { bubbles: true, cancelable: true }),
    );
    return true;
  });
  if (!clicked) {
    record.status = 'blocked';
    record.notes.push(
      '未找到可点击的主配置按钮，可能当前数据首屏均为主配置或权限隐藏',
    );
    return;
  }
  await sleep(800);
  await page
    .locator('.ant-popconfirm button, .el-popconfirm button, .el-popper button')
    .filter({ hasText: /确定|确认/ })
    .last()
    .click({ timeout: 5000 })
    .catch(() => {});
  await sleep(1800);
  record.requests = pickRequests(requests, '/infra/file-config/update-master');
  const loadingVisible = await page
    .locator('.el-loading-mask:visible, .ant-spin-spinning:visible')
    .count();
  if (record.requests.length === 0) {
    record.status = 'blocked';
    record.notes.push(
      '未捕获 update-master 请求，可能 PopConfirm 未确认或当前行不可设置',
    );
    return;
  }
  record.status = loadingVisible === 0 ? 'pass' : 'fail';
  if (loadingVisible > 0) {
    record.notes.push('update-master 后 loading 仍可见');
  }
}

for (const app of apps) {
  await runAppCase(app, 'pay-cashier', testPayCashier);
  await runAppCase(app, 'mp-message', testMpMessage);
  for (const route of [
    '/erp/stock/move',
    '/erp/stock/in',
    '/erp/stock/out',
    '/erp/stock/check',
  ]) {
    await runAppCase(app, `erp-${route.split('/').at(-1)}`, (ctx) =>
      testErpStock(ctx, route),
    );
  }
  await runAppCase(app, 'system-menu', testSystemMenu);
  await runAppCase(app, 'im-face-pack', testImFacePack);
  await runAppCase(app, 'infra-file-config', testInfraFileConfig);
}

await saveJson('results.json', cases);
console.table(
  cases.map((item) => ({
    app: item.app,
    name: item.name,
    status: item.status,
    notes: item.notes.join(' | '),
  })),
);
console.log(`results: ${path.join(outDir, 'results.json')}`);
