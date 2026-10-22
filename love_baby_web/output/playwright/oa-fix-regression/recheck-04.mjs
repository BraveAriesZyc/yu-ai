import { chromium } from 'playwright';
import fs from 'node:fs';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DIR = 'output/playwright/oa-fix-regression';
const base = 'http://127.0.0.1:5666';
const tag = 'antd';

// 本周一与上周一（本地时区）
function weekStart(offsetDays = 0) {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7) + offsetDays);
  return d;
}
const fmt = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const thisMonday = weekStart();
const prevMonday = weekStart(-7);

const results = [];
const record = (name, ok, extra = {}) =>
  results.push({ tag, name, ok, ...extra });

const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();

const itemErrors = [];
const itemHttpErrors = [];
let weekRequests = [];
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
page.on('request', (r) => {
  if (r.url().includes('/admin-api/oa/attendance/week-report'))
    weekRequests.push(r.url());
});

const shot = async (name) => {
  const file = `${tag}-${name}.png`;
  await page.screenshot({ path: `${DIR}/${file}`, fullPage: false });
  return file;
};
const itemNote = (extra = '') => {
  const parts = [extra].filter(Boolean);
  if (itemErrors.length > 0)
    parts.push(`pageerror: ${itemErrors.join(' | ').slice(0, 300)}`);
  if (itemHttpErrors.length > 0)
    parts.push(
      `http: ${[...new Set(itemHttpErrors)].join(' | ').slice(0, 300)}`,
    );
  return parts.join(' || ');
};
const dayHeaders = () =>
  page.$$eval('.vxe-table--header-wrapper th', (els) =>
    els
      .filter((el) => el.offsetParent !== null)
      .map((el) => el.textContent.trim())
      .filter((t) => /^周[一二三四五六日] \d{2}-\d{2}$/.test(t)),
  );

try {
  // 登录
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

  // 04. 周报「上一周/本周」按钮
  try {
    await page.goto(`${base}/oa/attendance/report`, {
      waitUntil: 'domcontentloaded',
      timeout: 30_000,
    });
    await sleep(4000);
    const before = await dayHeaders();
    const fileBefore = await shot('recheck-04-week-report-current');
    weekRequests = [];

    // 点「上一周」
    await page
      .locator('button', { hasText: /上一周/ })
      .first()
      .click();
    await sleep(3000);
    const afterPrev = await dayHeaders();
    const reqPrev = [...weekRequests];
    const filePrev = await shot('recheck-04-week-report-prev');

    // 点「本周」
    await page
      .locator('button', { hasText: /^本\s*周$/ })
      .first()
      .click();
    await sleep(3000);
    const afterCurrent = await dayHeaders();
    const reqCurrent = weekRequests.slice(reqPrev.length);
    const fileCurrent = await shot('recheck-04-week-report-today');

    const mmdd = (d) => fmt(d).slice(5);
    const expectPrevFirst = `周一 ${mmdd(prevMonday)}`;
    const expectThisFirst = `周一 ${mmdd(thisMonday)}`;
    const prevOk =
      afterPrev.length === 7 &&
      afterPrev[0] === expectPrevFirst &&
      reqPrev.some((u) => u.includes(`startDate=${fmt(prevMonday)}`));
    const currentOk =
      afterCurrent.length === 7 &&
      afterCurrent[0] === expectThisFirst &&
      reqCurrent.some((u) => u.includes(`startDate=${fmt(thisMonday)}`));

    record(
      '04-周报上一周本周按钮',
      prevOk && currentOk && itemErrors.length === 0,
      {
        screenshot: `${fileBefore}, ${filePrev}, ${fileCurrent}`,
        note: itemNote(
          `初始列头首日=${before[0]} || 上一周后=${afterPrev[0]}（预期 ${expectPrevFirst}，请求=${reqPrev.map((u) => u.split('startDate=')[1]).join('/') || '无'}）|| 本周后=${afterCurrent[0]}（预期 ${expectThisFirst}，请求=${reqCurrent.map((u) => u.split('startDate=')[1]).join('/') || '无'}）`,
        ),
      },
    );
  } catch (error) {
    const file = await shot('recheck-04-week-report').catch(() => undefined);
    record('04-周报上一周本周按钮', false, {
      screenshot: file,
      note: itemNote(String(error).slice(0, 200)),
    });
  }
} finally {
  await browser.close();
}

// 追加写入 recheck-result.json
const file = `${DIR}/recheck-result.json`;
const existing = fs.existsSync(file)
  ? JSON.parse(fs.readFileSync(file, 'utf8'))
  : { results: [] };
// 覆盖同名的旧记录
existing.results = (existing.results || []).filter(
  (r) => !(r.tag === tag && results.some((n) => n.name === r.name)),
);
existing.results.push(...results);
existing.generatedAt = new Date().toISOString();
fs.writeFileSync(file, JSON.stringify(existing, null, 2));

for (const r of results) {
  console.log(
    `${r.ok ? 'PASS' : 'FAIL'} [${r.tag}] ${r.name}${r.note ? ` -- ${r.note}` : ''}`,
  );
}
