import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const outDir = path.resolve('output/playwright/vben-p0');
await fs.mkdir(outDir, { recursive: true });
const resultFile = path.join(outDir, 'results.json');

const apps = [
  { key: 'antdv-next', baseUrl: 'http://127.0.0.1:5999' },
  { key: 'ele', baseUrl: 'http://127.0.0.1:5777' },
];

const results = [];

async function writeResults() {
  await fs.writeFile(resultFile, JSON.stringify(results, null, 2));
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function relevantConsole(messages) {
  return messages
    .filter((item) => ['error', 'warning'].includes(item.type))
    .filter((item) => {
      const text = item.text || '';
      return !text.includes('Download the Vue Devtools extension');
    });
}

function summarizeRequests(requests, pattern) {
  return requests
    .filter((item) => item.url.includes(pattern))
    .map((item) => ({
      method: item.method,
      status: item.status,
      url: item.url,
      postData: item.postData,
    }));
}

async function login(page, app) {
  await page.goto(`${app.baseUrl}/auth/login`, {
    waitUntil: 'domcontentloaded',
  });
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 20_000,
  });
  await page.waitForLoadState('domcontentloaded');
  await sleep(1500);
}

async function runCase(app, name, fn) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    ignoreHTTPSErrors: true,
  });
  const page = await context.newPage();
  page.setDefaultTimeout(10_000);
  page.setDefaultNavigationTimeout(20_000);
  const consoleMessages = [];
  const requests = [];
  page.on('console', (msg) => {
    consoleMessages.push({ type: msg.type(), text: msg.text() });
  });
  page.on('pageerror', (error) => {
    consoleMessages.push({ type: 'error', text: error.message });
  });
  page.on('requestfinished', async (request) => {
    const response = await request.response();
    requests.push({
      method: request.method(),
      postData: request.postData(),
      status: response?.status(),
      url: request.url(),
    });
  });
  page.on('requestfailed', (request) => {
    requests.push({
      method: request.method(),
      failure: request.failure()?.errorText,
      postData: request.postData(),
      url: request.url(),
    });
  });

  const record = {
    app: app.key,
    name,
    status: 'unknown',
    notes: [],
    console: [],
    requests: [],
    screenshot: '',
  };

  try {
    await Promise.race([
      (async () => {
        await login(page, app);
        await fn(page, record, requests);
      })(),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error(`case timeout: ${name}`)), 35_000),
      ),
    ]);
    record.console = relevantConsole(consoleMessages);
    if (record.status === 'unknown') {
      record.status = record.console.some((item) => item.type === 'error')
        ? 'fail'
        : 'pass';
    }
  } catch (error) {
    record.status = 'fail';
    record.notes.push(error.stack || error.message);
    record.console = relevantConsole(consoleMessages);
  } finally {
    const screenshot = path.join(outDir, `${app.key}-${name}.png`);
    await page.screenshot({ path: screenshot, fullPage: true }).catch(() => {});
    record.screenshot = screenshot;
    results.push(record);
    await writeResults();
    await context.close();
    await browser.close();
  }
}

async function openPath(page, app, routePath) {
  await page.goto(`${app.baseUrl}${routePath}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForLoadState('domcontentloaded');
  await sleep(1500);
}

async function checkPageLoaded(page, record, expectedText) {
  const text = await page.locator('body').innerText({ timeout: 10_000 });
  if (text.includes('404') || text.includes('页面不存在')) {
    record.status = 'fail';
    record.notes.push('页面进入 404');
  }
  if (expectedText && !text.includes(expectedText)) {
    record.notes.push(`未找到预期文本：${expectedText}`);
  }
  return text;
}

async function clickFirstByText(page, texts) {
  for (const text of texts) {
    const locator = page.getByText(text, { exact: false }).first();
    if ((await locator.count()) > 0) {
      await locator.click();
      return text;
    }
  }
  return '';
}

async function testPayCashier(page, record, requests) {
  await openPath(page, this, '/pay/cashier?id=567');
  await checkPageLoaded(page, record, '支付订单');
  const descriptionsItems = await page
    .locator('.el-descriptions__cell, .ant-descriptions-item')
    .count();
  if (this.key === 'ele' && descriptionsItems === 0) {
    record.status = 'fail';
    record.notes.push('ele 商品详情 Descriptions 未渲染 item');
    return;
  }
  const wxNative = page.getByText('微信扫码支付', { exact: false }).first();
  if ((await wxNative.count()) === 0) {
    record.status = 'blocked';
    record.notes.push('页面未找到微信扫码支付入口，可能渠道/页面数据不完整');
    return;
  }
  await wxNative.click();
  await sleep(1200);
  record.requests = summarizeRequests(requests, '/pay/order/submit');
  const modalText = await page.locator('body').innerText();
  if (modalText.includes('请使用微信') || modalText.includes('配置')) {
    record.status = 'pass';
  } else {
    record.status = 'blocked';
    record.notes.push(
      '点击微信扫码后未出现二维码弹窗或明确错误，需人工核查渠道配置',
    );
  }
}

async function testMpMessage(page, record, requests) {
  await openPath(page, this, '/mp/message');
  const text = await checkPageLoaded(page, record, '消息');
  record.requests = summarizeRequests(requests, '/mp/message/page');
  if (record.requests.length === 0) {
    record.status = 'fail';
    record.notes.push('未触发 mp/message/page 请求');
    return;
  }
  if (text.includes('未知消息类型') || text.includes('未知事件类型')) {
    record.status = 'blocked';
    record.notes.push('当前数据包含未知消息/事件，需要结合具体消息类型复核');
  } else {
    record.status = 'pass';
  }
}

async function testErpStock(page, record, requests, routePath, title) {
  await openPath(page, this, routePath);
  await checkPageLoaded(page, record, title);
  const clicked = await clickFirstByText(page, ['新增', '创建']);
  if (!clicked) {
    record.status = 'fail';
    record.notes.push('未找到新增按钮');
    return;
  }
  await sleep(1800);
  const visibleSelect = page
    .locator('.ant-select-selector, .el-select__wrapper')
    .filter({ hasText: '' })
    .first();
  if ((await visibleSelect.count()) > 0) {
    await visibleSelect.click();
    await sleep(800);
  }
  const optionCount = await page
    .locator('.ant-select-item-option, .el-select-dropdown__item')
    .count();
  record.requests = [
    ...summarizeRequests(requests, '/erp/product/product/simple-list'),
    ...summarizeRequests(requests, '/erp/stock/warehouse/simple-list'),
  ];
  if (record.requests.length < 2) {
    record.status = 'fail';
    record.notes.push('产品/仓库 simple-list 请求未完整触发');
    return;
  }
  if (optionCount === 0) {
    record.status = 'fail';
    record.notes.push('打开下拉后没有任何选项');
    return;
  }
  record.status = 'pass';
}

async function testSystemMenu(page, record) {
  await openPath(page, this, '/system/menu');
  await checkPageLoaded(page, record, '菜单');
  const clicked = await clickFirstByText(page, ['新增', '创建']);
  if (!clicked) {
    record.status = 'fail';
    record.notes.push('未找到新增菜单按钮');
    return;
  }
  await sleep(1000);
  const bodyText = await page.locator('body').innerText();
  if (!bodyText.includes('组件名称')) {
    record.status = 'fail';
    record.notes.push('菜单表单未打开或组件名称字段不可见');
    return;
  }
  const input = page.locator('input').filter({ hasNotText: '' }).nth(4);
  await input.fill('system/menu');
  await sleep(700);
  const popupText = await page.locator('body').innerText();
  if (popupText.includes('system/menu/index')) {
    record.status = 'pass';
  } else {
    record.status = 'blocked';
    record.notes.push('自动完成候选未在文本快照中出现，需人工确认弹层定位');
  }
}

async function testImFacePack(page, record, requests) {
  await openPath(page, this, '/im/face/pack');
  await checkPageLoaded(page, record, '表情包');
  const clicked = await clickFirstByText(page, ['管理表情']);
  if (!clicked) {
    record.status = 'fail';
    record.notes.push('未找到管理表情按钮');
    return;
  }
  await sleep(1800);
  record.requests = summarizeRequests(
    requests,
    '/im/manager/face-pack-item/page',
  );
  if (record.requests.length === 0) {
    record.status = 'fail';
    record.notes.push('抽屉打开后未触发表情列表请求');
    return;
  }
  const bodyText = await page.locator('body').innerText();
  record.status = bodyText.includes('表情列表') ? 'pass' : 'fail';
  if (record.status === 'fail') {
    record.notes.push('抽屉未显示表情列表');
  }
}

async function testInfraFileConfig(page, record, requests) {
  await openPath(page, this, '/infra/file/file-config');
  await checkPageLoaded(page, record, '文件配置');
  const clicked = await clickFirstByText(page, ['主配置']);
  if (!clicked) {
    record.status = 'blocked';
    record.notes.push(
      '未找到可点击主配置按钮，可能当前页首屏均为主配置/权限隐藏',
    );
    return;
  }
  await sleep(600);
  await clickFirstByText(page, ['确定', '确认']);
  await sleep(1500);
  record.requests = summarizeRequests(
    requests,
    '/infra/file-config/update-master',
  );
  const loadingCount = await page
    .locator('.el-loading-mask, .ant-spin')
    .count();
  if (record.requests.length === 0) {
    record.status = 'blocked';
    record.notes.push('未捕获 update-master 请求，可能 PopConfirm 未确认成功');
    return;
  }
  record.status = loadingCount > 0 ? 'fail' : 'pass';
  if (loadingCount > 0) {
    record.notes.push('操作后仍检测到 loading 遮罩/spin');
  }
}

for (const app of apps) {
  await runCase(app, 'P0-01-pay-cashier', testPayCashier.bind(app));
  await runCase(app, 'P0-02-03-mp-message', testMpMessage.bind(app));
  await runCase(app, 'P0-04-erp-stock-move', (page, record, requests) =>
    testErpStock.call(
      app,
      page,
      record,
      requests,
      '/erp/stock/move',
      '库存调拨',
    ),
  );
  await runCase(app, 'P0-05-erp-stock-in', (page, record, requests) =>
    testErpStock.call(app, page, record, requests, '/erp/stock/in', '入库'),
  );
  await runCase(app, 'P0-06-erp-stock-out', (page, record, requests) =>
    testErpStock.call(app, page, record, requests, '/erp/stock/out', '出库'),
  );
  await runCase(app, 'P0-07-erp-stock-check', (page, record, requests) =>
    testErpStock.call(app, page, record, requests, '/erp/stock/check', '盘点'),
  );
  await runCase(app, 'P0-08-system-menu', testSystemMenu.bind(app));
  await runCase(app, 'P0-09-im-face-pack', testImFacePack.bind(app));
  await runCase(app, 'P0-10-infra-file-config', testInfraFileConfig.bind(app));
}

await writeResults();

const summary = results.map((item) => ({
  app: item.app,
  name: item.name,
  status: item.status,
  notes: item.notes,
  consoleErrors: item.console.filter((msg) => msg.type === 'error').length,
}));
console.table(summary);
console.log(`results: ${resultFile}`);
