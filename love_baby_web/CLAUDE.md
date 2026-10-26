# CLAUDE.md — love_baby_web(管理后台前端)

## 项目概述

基于 vue-vben-admin `5.7.0`(yudao 版 `2026.09.0-snapshot`)的 B 端管理后台,消费后端 **admin-api**。

- Vue 3 + Vite + TypeScript,**pnpm `11.16.0` + Turborepo**(node `^22.18 || ^24.12`;preinstall 强制 pnpm)
- UI 组件库 import 自 **`antdv-next`**(不是 ant-design-vue——全仓 import 路径注意)
- **主力变体 = web-antdv-next**(2026-10-16 用户指定,覆盖早前「建议 web-antd」推荐;INTEGRATION-DESIGN §8.2 已同步):**业务页(views/love + api/love)只此一份**,勿逐个维护五份
- 根 identity 仍是 yudao 品牌:`apps/web-antdv-next/.env` 的 `VITE_APP_TITLE=芋道管理系统`/`VITE_APP_NAMESPACE=yudao-vben-antd`(缓存与 store 前缀),改产品名动这里

## 技术要点(写代码前必读)

1. **五变体分工**:web-antdv-next(5999,love 业务页)+ web-antd(5666,含 yudao 官方 21 模块页 ai/bpm/crm/erp/…/wms,无 love)+ web-ele(5777)/web-naive(5888)/web-tdesign(**5999,与 antdv-next 撞端口,勿同时跑**)。⚠️ web-antd 的官方模块页是参考实现,**love 业务只在主力变体写**。
2. **动态菜单、零前端路由**: `src/preferences.ts` `accessMode: 'backend'`;`router/access.ts` 的 `pageMap = import.meta.glob('../views/**/*.vue')` 收集全部页面,后端 `system_menu` 的 component 字符串 `love/<域>/index` 经 `normalizeViewPath` 查 pageMap 解析。**新增 love 页面 = 只建 views 文件 + 后端建菜单,前端不写 router/routes/modules**(love 在 `router/routes/index.ts` 零登记)。组件解析失败静默落 not-found 页 + console 报 `route component is invalid`——「菜单点了 404」先查 component 字符串与目录名是否逐字符一致。⚠️ `componentKeys` 过滤含 `/modules/` 的路径,**form.vue 不能作菜单组件**。
3. **新功能三件套**(照既有域克隆):`api/<域>/index.ts`(接口封装)+ `views/<域>/data.ts`(搜索 schema + 列定义)+ `views/<域>/index.vue`(Grid 页;全 CRUD 再加 `modules/form.vue`)+ **后端 system_menu 菜单 SQL**。CRUD 两形态:全 CRUD 7 域(config/dailyQuestion/emoji/inspiration/moodOptionDefault/user/wishTemplate)、仅查删 8 域(anniversary/checkin/couple/message/moment/moodRecord/todo/wish)。
4. **api 范式**:`export namespace Love<域>Api` + interface;`requestClient.get<PageResult<T>>('/love/<域>/page', { params })`;**DELETE 走 query**(`requestClient.delete(\`.../delete?id=${id}\`)`);`PageParam/PageResult` 类型从 `@vben/request` 导。拆包在 `api/request.ts`:`responseReturn: 'data'` + `defaultResponseInterceptor({ codeField: 'code', dataField: 'data', successCode: 0 })`(yudao CommonResult)。
5. **views 范式**:`useVbenVxeGrid`(`formOptions.schema` 搜索表单 + `proxyConfig.ajax.query` 直返 PageResult)+ `TableAction`(`auth: ['love:<域>:<动作>']` 权限串控显隐 + popConfirm 删除);`data.ts` 用本地枚举映射,**零字典依赖**;删除均为逻辑删除。⚠️ **日期区间筛选必须覆写 RangePicker `format/valueFormat: 'YYYY-MM-DD'`**——服务端 `LocalDate[]` 走 `@DateTimeFormat yyyy-MM-dd`,组件默认带时分秒必 400(moment/data.ts M18 修法,M31 新页直接带对)。
6. **目录命名**:三个域是大小写连写 `moodOptionDefault`、`wishTemplate`、`dailyQuestion`(非 kebab-case);菜单 component 字符串、api 目录、views 目录三处必须一致。
7. **request.ts 关键机制**:401 单飞静默刷新(`doRefreshToken` 取 `resp?.data?.data?.accessToken`,失败须 `throw resp.data`);Blob 里包 JSON 业务错误解包重抛(yudao 加的);**API 加解密默认开**(`VITE_APP_API_ENCRYPT_ENABLE=true`,AES,key 明文在 `.env`,后端须配套否则接口报错);租户已关(`VITE_APP_TENANT_ENABLE=false` 对齐后端)但 tenant-id 头逻辑保留。
8. **i18n 实际形态**:业务文案**硬编码中文**,只用框架级 `$t('common.delete')`/`$t('ui.actionMessage.*')` 等键;**不建 love 专属 locale 文件**(app 级语言包仅 `src/locales/langs/{zh-CN,en-US}/{page,utils}.json`)。
9. **vite 代理**(`/admin-api` → 后端,rewrite 先剥前缀再由 target 补回):默认 `http://localhost:48080/admin-api`,远程 `https://love.shattered-dream.top/admin-api` 留在注释里手动切换——**2026-10-26 M31 冒烟踩过**:代理指远程时,本地库的菜单增量对远程后端不可见,联调前先确认 target 指向。

## 常用操作

- **dev**:`pnpm dev:antdv-next`(= `pnpm -F @vben/web-antdv-next run dev`,端口 5999)
- **typecheck(唯一类型校验闸)**:`pnpm --filter @vben/web-antdv-next typecheck`(vue-tsc,⚠️ 退出码只认 `echo EXIT=$?` 直取,严禁经管道判断)
- **build**:`pnpm build:antdv-next`(turbo,`NODE_OPTIONS=--max-old-space-size=8192`)
- **lint**:app 包**无 lint script**,只能根跑 `pnpm lint`(= `vsh lint`,oxlint/eslint/stylelint 全家桶;`--format` 顺带修);lefthook pre-commit 对 staged 文件串行 oxlint→oxfmt→eslint→stylelint 后再全量 `pnpm check:type`,commit-msg 跑 commitlint

## 目录结构与后续规划

```text
love_baby_web/
├── apps/                    # 五变体(见技术要点 1);业务页只写 web-antdv-next
│   └── web-antdv-next/src/
│       ├── adapter/         # form.ts / vxe-table.ts(setupVbenForm / setupVbenVxeTable;yudao 格式化工具)
│       ├── api/             # request.ts(客户端+拆包+401 刷新+加解密)+ love/ 14 域(每域单 index.ts)
│       ├── views/love/      # 15 业务域,与 api 一一对应;全 CRUD 域含 modules/form.vue
│       ├── router/access.ts # pageMap glob + accessMode backend(动态菜单唯一入口)
│       ├── preferences.ts   # accessMode/refreshToken 等覆盖(改后须清 localStorage 缓存才生效)
│       └── locales/ layouts/ store/ plugins/ components/ utils/
├── packages/
│   ├── @core/               # ★ 框架核心(base/composables/preferences/ui-kit)——不含业务,原则上勿改
│   ├── effects/
│   │   ├── request/         # @vben/request(requestClient 工厂 + PageParam/PageResult 类型)
│   │   └── common-ui/       # 可复用业务封装:Page/Cropper/IconPicker/Captcha/JsonViewer/EllipsisText/
│   │                        #   Tree/CountTo/Barcode/DocAlert/Iframe/ApiComponent + ui/(authentication/
│   │                        #   dashboard/profile/about/fallback)——新页面优先复用
│   └── constants/ icons/ locales/ preferences/ stores/ styles/ types/ utils
├── internal/                # vite-config / lint-configs(eslint|oxlint|oxfmt|stylelint 共享配置)
├── scripts/                 # vsh CLI(lint 入口)
└── lefthook.yml             # pre-commit/commit-msg/post-merge 钩子(模板,钩子是否安装看 git hooks 路径)
```

**新增 love 功能固定套路** = api 封装(技术要点 4)+ views 页(技术要点 5)+ 后端 system_menu SQL(技术要点 2);确需改 `packages/` 先想清楚是否影响 vben 升级与五变体同构。三端对接设计与 D-决策全录见根 [INTEGRATION-DESIGN.md](../INTEGRATION-DESIGN.md)。
