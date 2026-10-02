/** One-off ele datetime panel check — see hrm-ele-datetime-check.json */
import { chromium } from 'playwright';
import path from 'node:path';
import fs from 'node:fs/promises';
const BASE = 'http://127.0.0.1:5777';
const OUT = path.resolve('output/playwright/hrm-ele-datetime-check.png');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function login(page) {
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(1000);
  const b = page.locator('button[aria-label="login"]');
  if (await b.count()) await b.click();
  else {
    const u = page
      .locator(
        'input[type="text"], input[placeholder*="账号"], input[placeholder*="用户"]',
      )
      .first();
    const p = page.locator('input[type="password"]').first();
    if (await u.count()) await u.fill('admin');
    if (await p.count()) await p.fill('admin123');
    await page
      .locator('button:has-text("登录"), button[type="submit"]')
      .first()
      .click();
  }
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
    timeout: 45_000,
  });
  await sleep(1500);
}
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const result = { path: 'candidate-interview' };
try {
  await login(page);
  await page.goto(`${BASE}/hrm/recruit/candidate`, {
    waitUntil: 'domcontentloaded',
  });
  for (let i = 0; i < 40; i++) {
    const t = await page.locator('body').innerText();
    if (t.includes('安排面试') || t.includes('新增')) break;
    await sleep(800);
  }
  await sleep(800);
  await page
    .locator('.vxe-body--row')
    .getByRole('button', { name: '安排面试' })
    .first()
    .click();
  await page
    .locator('.z-popup >> text=安排面试')
    .first()
    .waitFor({ state: 'visible', timeout: 15_000 });
  await sleep(800);
  const editor = page.locator('.z-popup .el-date-editor--datetime').first();
  result.hasDatetimeEditor = await editor.count();
  result.editorClass = await editor.getAttribute('class');
  await editor.click({ force: true });
  await page
    .locator('.z-popup input[placeholder="请选择面试时间"]')
    .click({ force: true });
  await sleep(1200);
  try {
    await page
      .locator('.el-picker-panel.has-time:visible, .el-time-spinner:visible')
      .first()
      .waitFor({ state: 'visible', timeout: 5000 });
  } catch {}
  result.panel = await page.evaluate(() => {
    const vis = (el) => {
      const r = el.getBoundingClientRect();
      const st = getComputedStyle(el);
      return (
        r.width > 0 &&
        r.height > 0 &&
        st.visibility !== 'hidden' &&
        st.display !== 'none'
      );
    };
    const spinners = [...document.querySelectorAll('.el-time-spinner')].filter(
      vis,
    );
    const wrappers = [
      ...document.querySelectorAll('.el-time-spinner__wrapper'),
    ].filter(vis);
    const panels = [...document.querySelectorAll('.el-picker-panel')].filter(
      vis,
    );
    return {
      timeSpinnerVisible: spinners.length,
      timeColumnsVisible: wrappers.length,
      panelVisible: panels.length,
      panelClass: panels.map((p) => p.className).join(' | '),
      appeared:
        spinners.length > 0 ||
        wrappers.length > 0 ||
        panels.some((p) => String(p.className).includes('has-time')),
    };
  });
  await page.screenshot({ path: OUT, fullPage: false });
  result.screenshot = OUT;
  result.datetimeEditorConfirmed = String(result.editorClass || '').includes(
    'el-date-editor--datetime',
  );
  result.ok = !!result.panel?.appeared;
  await fs.writeFile(
    'output/playwright/hrm-ele-datetime-check.json',
    JSON.stringify(result, null, 2),
  );
} catch (error) {
  result.error = String(error?.stack || error);
  await page.screenshot({ path: OUT }).catch(() => {});
} finally {
  await browser.close();
}
console.log(JSON.stringify(result, null, 2));
process.exit(result.ok ? 0 : 2);
