import { chromium } from 'playwright';
import fs from 'node:fs';

const outputDir = 'output/playwright/pms-project-favorite-layout';
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

async function openProjectList(page, baseUrl, waitForIcon = false) {
  await page.goto(`${baseUrl}/pms/pm/project/list?tabs=participate`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByText('星标项目', { exact: true }).waitFor();
  await page.locator('button[aria-label="取消星标"]').first().waitFor();
  if (waitForIcon) {
    await page
      .locator('button[aria-label="取消星标"] svg path')
      .first()
      .waitFor();
    await page.locator('button[aria-label="更多操作"] svg').first().waitFor();
  }
}

async function getFavoriteCardMetrics(page) {
  return page.evaluate(() => {
    const star = document.querySelector('button[aria-label="取消星标"]');
    const card = star?.closest('div.relative');
    const logo = card?.children[0];
    const content = card?.children[1];
    const title = content?.firstElementChild;
    const more = card?.querySelector('[aria-label="更多操作"]');
    const chartWrapper = content?.querySelector('.pr-10');
    const starPath = star?.querySelector('path');
    const rect = (element) => {
      if (!element) return null;
      const value = element.getBoundingClientRect();
      return {
        bottom: Math.round(value.bottom),
        height: Math.round(value.height),
        right: Math.round(value.right),
        width: Math.round(value.width),
        x: Math.round(value.x),
        y: Math.round(value.y),
      };
    };
    const cardRect = rect(card);
    const logoRect = rect(logo);
    const titleRect = rect(title);
    const starRect = rect(star);
    const moreRect = rect(more);
    const chartWrapperRect = rect(chartWrapper);
    return {
      card: cardRect,
      chartWrapper: chartWrapperRect,
      logo: logoRect,
      logoAlignedWithTitle:
        Boolean(logoRect && titleRect) &&
        Math.abs(logoRect.y - titleRect.y) <= 4,
      more: moreRect,
      moreAboveChart:
        !chartWrapperRect ||
        Boolean(moreRect && moreRect.x >= chartWrapperRect.right - 42),
      moreZIndex: more ? getComputedStyle(more.parentElement).zIndex : null,
      star: starRect,
      starPathFill: starPath ? getComputedStyle(starPath).fill : null,
      starRightOffset:
        cardRect && starRect ? cardRect.right - starRect.right : null,
      starTopOffset: cardRect && starRect ? starRect.y - cardRect.y : null,
      title: titleRect,
    };
  });
}

async function captureVben() {
  const context = await browser.newContext({
    colorScheme: 'light',
    viewport: { width: 1680, height: 1050 },
  });
  const page = await context.newPage();
  await loginVben(page);
  await useLightTheme(page);
  const lightModeButton = page.locator('button[aria-label="light"]');
  if (await lightModeButton.count()) {
    await lightModeButton.click();
  }
  await openProjectList(page, 'http://127.0.0.1:5667', true);
  if (await page.locator('html.dark').count()) {
    await page.locator('button.theme-toggle').click();
  }
  await page.screenshot({ path: `${outputDir}/vben-project-favorite.png` });
  const metrics = await getFavoriteCardMetrics(page);
  await context.close();
  return metrics;
}

async function captureVue3() {
  const context = await browser.newContext({
    colorScheme: 'light',
    viewport: { width: 1680, height: 1050 },
  });
  const page = await context.newPage();
  await loginVue3(page);
  await openProjectList(page, 'http://127.0.0.1');
  await page.screenshot({ path: `${outputDir}/vue3-project-favorite.png` });
  const metrics = await getFavoriteCardMetrics(page);
  await context.close();
  return metrics;
}

const result = {
  vben: await captureVben(),
  vue3: await captureVue3(),
};
fs.writeFileSync(`${outputDir}/result.json`, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));

await browser.close();
