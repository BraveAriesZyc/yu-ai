import fs from 'node:fs';

const tags = ['antdv-next', 'antd', 'ele'];
const names = {
  'antdv-next': 'web-antdv-next (http://localhost:5999)',
  antd: 'web-antd (http://localhost:5666)',
  ele: 'web-ele (http://localhost:5777)',
};
const shotNames = [
  '01-工作台',
  '02-项目列表',
  '03-项目详情-任务',
  '04-项目详情-概况',
  '05-项目详情-甘特图',
  '06-工作项详情抽屉',
  '07-项目模板',
  '08-项目归档',
  '09-项目回收站',
  '10-知识库列表',
  '11-知识库文档中心',
  '12-知识库模板',
  '13-文档标签',
  '14-我的收藏',
  '15-最近浏览',
  '16-文档搜索',
  '17-知识库回收站',
];

let body = '';
let totalPass = 0;
let totalFail = 0;
for (const tag of tags) {
  const d = JSON.parse(
    fs.readFileSync(`output/playwright/pms-test/${tag}/result.json`, 'utf8'),
  );
  totalPass += d.pass;
  totalFail += d.fail;
  const rows = d.results
    .map(
      (r) => `<tr class="${r.ok ? 'pass' : 'fail'}">
        <td>${r.name}</td>
        <td>${r.ok ? '✅ 通过' : '❌ 失败'}</td>
        <td>${r.path ?? ''}</td>
        <td>${(r.note ?? '').replaceAll('<', '&lt;').slice(0, 200)}</td>
      </tr>`,
    )
    .join('\n');
  const shots = shotNames
    .filter((n) => fs.existsSync(`output/playwright/pms-test/${tag}/${n}.png`))
    .map(
      (n) => `<figure>
        <img src="${tag}/${n}.png" alt="${n}" loading="lazy" />
        <figcaption>${n}</figcaption>
      </figure>`,
    )
    .join('\n');
  body += `
  <section>
    <h2>${names[tag]} <span class="badge ${d.fail === 0 ? 'ok' : 'bad'}">${d.pass} 通过 / ${d.fail} 失败</span></h2>
    <table>
      <thead><tr><th>测试项</th><th>结果</th><th>路径</th><th>备注</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    <h3>页面截图</h3>
    <div class="shots">${shots}</div>
  </section>`;
}

const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<title>PMS Vue3 → Vben5 迁移测试报告</title>
<style>
  body { margin: 24px; font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #f5f6f8; color: #222; }
  h1 { font-size: 22px; }
  .meta { color: #666; font-size: 13px; margin-bottom: 18px; }
  .summary { display: inline-block; padding: 6px 14px; border-radius: 6px; font-weight: 600; }
  .summary.ok { background: #e6f7e9; color: #1a7f37; }
  section { background: #fff; border-radius: 8px; padding: 18px 22px; margin-bottom: 28px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
  h2 { font-size: 17px; display: flex; align-items: center; gap: 10px; }
  .badge { font-size: 12px; padding: 2px 10px; border-radius: 10px; }
  .badge.ok { background: #e6f7e9; color: #1a7f37; }
  .badge.bad { background: #fdecec; color: #c0392b; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; margin: 10px 0 18px; }
  th, td { border: 1px solid #e3e5e8; padding: 6px 10px; text-align: left; }
  th { background: #f2f3f5; }
  tr.pass td:nth-child(2) { color: #1a7f37; }
  tr.fail td:nth-child(2) { color: #c0392b; }
  .shots { display: grid; grid-template-columns: repeat(auto-fill, minmax(420px, 1fr)); gap: 14px; }
  figure { margin: 0; border: 1px solid #e3e5e8; border-radius: 6px; overflow: hidden; background: #fafafa; }
  figure img { width: 100%; display: block; }
  figcaption { font-size: 12px; color: #555; padding: 6px 10px; background: #f2f3f5; }
</style>
</head>
<body>
<h1>PMS Vue3 → Vben5 迁移测试报告</h1>
<div class="meta">
  生成时间：${new Date().toLocaleString('zh-CN', { hour12: false })}　|
  测试范围：PMS（pm 项目管理 + kb 知识库）三端冒烟测试（登录、页面渲染、列表、详情、抽屉、弹窗、看板、目录树、图表）
</div>
<div class="summary ${totalFail === 0 ? 'ok' : ''}">总计：${totalPass} 项通过，${totalFail} 项失败</div>
${body}
</body>
</html>`;

fs.writeFileSync('output/playwright/pms-test/report.html', html);
console.log('report generated:', totalPass, 'pass /', totalFail, 'fail');
