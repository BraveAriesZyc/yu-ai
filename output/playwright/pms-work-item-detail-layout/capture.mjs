import { chromium } from 'playwright';
import fs from 'node:fs';

const outputDir = 'output/playwright/pms-work-item-detail-layout';
fs.mkdirSync(outputDir, { recursive: true });

const browser = await chromium.launch({ headless: true });

async function loginVben(page) {
  await page.goto('http://127.0.0.1:5667/auth/login', {
    waitUntil: 'domcontentloaded',
  });
  const tenantSelect = page.locator('.ant-select:has-text("请选择租户")');
  if (await tenantSelect.count()) {
    await tenantSelect.click();
    await page
      .locator('.ant-select-dropdown .ant-select-item-option')
      .first()
      .click();
  }
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 30_000,
  });
}

async function loginVue3(page) {
  await page.goto('http://127.0.0.1/login', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), {
    timeout: 30_000,
  });
}

async function openWorkItem(page, baseUrl) {
  await page.goto(`${baseUrl}/pms/pm/workbench`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByRole('button', { name: 'EEEE', exact: true }).click();
  await page.getByText('基础信息', { exact: true }).last().waitFor();
}

async function useLightTheme(page) {
  await page.evaluate(() => {
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (!key?.endsWith('preferences-theme')) continue;
      const value = JSON.parse(localStorage.getItem(key));
      value.mode = 'light';
      localStorage.setItem(key, JSON.stringify(value));
    }
  });
  await page.reload({ waitUntil: 'domcontentloaded' });
}

async function captureVben() {
  const context = await browser.newContext({
    colorScheme: 'light',
    viewport: { width: 1680, height: 1120 },
  });
  const page = await context.newPage();
  await loginVben(page);
  await useLightTheme(page);
  await openWorkItem(page, 'http://127.0.0.1:5667');
  const drawer = page.locator('[role="dialog"]').last();
  await drawer.screenshot({ path: `${outputDir}/vben-work-item-detail.png` });
  const metrics = await page.evaluate(() => {
    const drawer = document.querySelector('[role="dialog"]');
    const exactText = (selector, text) =>
      [...(drawer?.querySelectorAll(selector) || [])].find(
        (element) => element.textContent?.trim() === text,
      );
    const editButton = exactText('button', '编辑描述');
    const descriptionTitle = exactText('h3', '需求描述');
    const textarea = drawer?.querySelector(
      'textarea[placeholder="请输入评论内容"]',
    );
    const submitButton = exactText('button', '发表评论');
    const statusLabel = exactText('.ant-collapse-content *', '状态');
    const box = (element) => {
      const rect = element?.getBoundingClientRect();
      return rect
        ? { x: rect.x, y: rect.y, right: rect.right, bottom: rect.bottom }
        : null;
    };
    return {
      actionAligned: Math.abs(box(editButton).x - box(descriptionTitle).x) < 2,
      commentButtonBelowTextarea: box(submitButton).y > box(textarea).bottom,
      statusInsideBasicPanel: Boolean(statusLabel),
    };
  });
  await context.close();
  return metrics;
}

async function captureVue3() {
  const context = await browser.newContext({
    colorScheme: 'light',
    viewport: { width: 1680, height: 1120 },
  });
  const page = await context.newPage();
  await loginVue3(page);
  await openWorkItem(page, 'http://127.0.0.1');
  const drawer = page.locator('.el-drawer:visible').last();
  await drawer.screenshot({ path: `${outputDir}/vue3-work-item-detail.png` });
  const statusInsideBasicPanel =
    (await drawer
      .locator('.el-collapse-item__wrap')
      .getByText('状态', { exact: true })
      .count()) === 1;
  await context.close();
  return { statusInsideBasicPanel };
}

const result = {
  vben: await captureVben(),
  vue3: await captureVue3(),
};
fs.writeFileSync(`${outputDir}/result.json`, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));

await browser.close();
