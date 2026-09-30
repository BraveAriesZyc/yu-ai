import fs from 'node:fs/promises';
import path from 'node:path';

import { chromium } from 'playwright';

const outDir = path.resolve('output/playwright/vben-p0-targeted');
await fs.mkdir(outDir, { recursive: true });

const apps = [
  { key: 'antdv-next', baseUrl: 'http://127.0.0.1:5999' },
  { key: 'ele', baseUrl: 'http://127.0.0.1:5777' },
];

const routes = [
  '/im/face/pack',
  '/im/manager/face/pack',
  '/im/sensitive-word',
  '/infra/file-config',
  '/infra/file/file-config',
  '/infra/fileConfig',
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function login(page, baseUrl) {
  await page.goto(`${baseUrl}/auth/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((url) => !url.pathname.includes('/auth/login'), {
    timeout: 25_000,
  });
  await sleep(1200);
}

const rows = [];
for (const app of apps) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  await login(page, app.baseUrl);
  for (const route of routes) {
    await page.goto(`${app.baseUrl}${route}`, {
      waitUntil: 'domcontentloaded',
    });
    await sleep(1800);
    const bodyText = await page
      .locator('body')
      .innerText()
      .catch(() => '');
    rows.push({
      app: app.key,
      route,
      actualPath: await page.evaluate(
        () => location.pathname + location.search + location.hash,
      ),
      titleHits: {
        facePack:
          bodyText.includes('表情包列表') || bodyText.includes('管理表情'),
        sensitive:
          bodyText.includes('敏感词列表') || bodyText.includes('敏感词管理'),
        fileConfig:
          bodyText.includes('文件配置') || bodyText.includes('主配置'),
        notFound: bodyText.includes('404') || bodyText.includes('页面不存在'),
      },
      firstText: bodyText.slice(0, 300),
    });
    await page
      .screenshot({
        fullPage: true,
        path: path.join(
          outDir,
          `${app.key}-probe-${route.replaceAll('/', '_') || 'root'}.png`,
        ),
      })
      .catch(() => {});
  }
  await context.close();
  await browser.close();
}

await fs.writeFile(
  path.join(outDir, 'route-probe.json'),
  JSON.stringify(rows, null, 2),
);
console.table(
  rows.map(({ app, route, actualPath, titleHits }) => ({
    app,
    route,
    actualPath,
    ...titleHits,
  })),
);
