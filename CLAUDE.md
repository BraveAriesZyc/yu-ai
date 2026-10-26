# CLAUDE.md — love_baby 仓库总览（三端一体）

本仓库是「情侣互动」产品的 monorepo，含三个子项目：**C 端跨端 App**、**Java 后端**、**B 端管理后台**。本文件只写架构定位、代码分层与后续开发的放置约定；每个项目的详细开发手册见各自目录（三份已全部就位，见文末规划表）。

## 总体架构

```text
┌─────────────────────────────┐        ┌─────────────────────────────┐
│  love_baby/                 │        │  love_baby_web/             │
│  Kuikly 跨端 App（C 端）      │        │  vben-admin 管理后台（B 端）  │
│  Android / iOS / 鸿蒙 / H5   │        │  Vue3 + Turborepo monorepo  │
└──────────────┬──────────────┘        └──────────────┬──────────────┘
               │ app-api（移动端接口）                  │ admin-api（管理接口）
               ▼                                     ▼
      ┌────────────────────────────────────────────────────┐
      │  love_baby_server/   yudao 单体后端（Java 25）        │
      │  Spring Boot 4 + MyBatis Plus，端口 48080            │
      └───────────────────────┬────────────────────────────┘
                              ▼
                MySQL（必需）＋ Redis（必需）＋ MQ（可选）
```

- 三端共用一套后端；后端以 `controller/admin`（admin-api，供管理后台）与 `controller/app`（app-api，供 App）**双通道**区分调用方。
- 后端改接口时，同步检查对应前端的 api 封装层（web 在 `apps/*/src/api/`，App 的网络层在 `love_baby/shared/network/`，已建成：HttpClient + 各域 Api 对象）。

---

## 1. love_baby/ — Kuikly 跨端客户端（C 端）

**定位**：一码多端（Android / iOS / 鸿蒙 / H5 / 小程序）的情侣互动 App。已有首页 / 待办 / 心情 / 悄悄话 / 我的五大 Tab 页 + 登录注册 / 情侣绑定 / 资料 / 纪念日 / 打卡 / 相册 / 恋爱清单 / 情话与灵感 / 权限管理 / 情侣电量 / 位置轨迹 / 恋爱月报等共 **18 个业务域**（`shared` 共 130 个 Kotlin 文件，已对接后端 app-api）。

**技术栈**：腾讯 Kuikly `2.29.0`（KMP + Compose DSL）、Kotlin `2.1.21`、AGP `8.5.2` + Gradle `8.14.5` + JDK 17；H5 产物 `nativevue2.js` 由子目录 `static_server/`（Node/Koa）托管。

**架构分层**（业务代码全在 `shared/src/commonMain/kotlin/com/zyc/love_baby/`）：

```text
base/（Pager、Module 桥接等基建）
  ↓
state/（AppState：TodoStore / MoodStore / ChatStore 全局快照单例）
  ↓
features/<业务域>/（@Page 页面 + Compose UI）
  ↓
components/（AppTheme / ThemeStore 主题系统、AppIcons、通用组件库 32 件，2026-10-26 全源码盘点收口，清单见画廊）
```

原生扩展：各端宿主壳（androidApp / iosApp / ohosApp）注册 Module，跨端接口声明放 `base/`。

**后续开发套路（架构级）**：新页面进 `features/<域>/`，跨页共享状态进 `state/`，可复用 UI 进 `components/`（并同步补组件画廊 demo）。`data/`（kotlinx.serialization 数据模型，10 文件）与 `network/`（HttpClient 统一拆包/401 单飞刷新 + 各域 Api 对象）**已建成**——新接口照既有 Api 对象模式扩展即可，无需再自建网络层。

➡️ **详细开发手册：[love_baby/CLAUDE.md](love_baby/CLAUDE.md)（已完备，含 Kuikly 2.29 渲染层大量踩坑铁律，写代码前必读；已接入官方 Kuikly AI skills——根 `.agents/skills/` 13 个，更新 `npx skills update Tencent-TDS/KuiklyUI-AI/skills`，用法见该手册「Kuikly AI 知识库」节）**

---

## 2. love_baby_server/ — Java 后端（yudao 单体）

**定位**：基于芋道 yudao `2026.09-jdk25-SNAPSHOT` 的 Spring Boot 单体后端。当前仅启用 **system（系统管理）** 与 **infra（基础设施）** 两个业务模块，其余官方模块（member / pay / mall / crm / erp / ai…）在根 `pom.xml` 中注释待按需开启。

**技术栈**：Java 25 + Spring Boot `4.1.0`（根 pom 实际值）+ MyBatis Plus + Redis；MySQL 必需（建表脚本在 `sql/mysql/` 拆分套件，官方脚本附带多数据库方言）；MQ 可选（RocketMQ / Kafka / RabbitMQ 配置 dormant——server pom 未依赖 starter-mq）；Spring AI 相关依赖随框架带入但模块未启用。

**Maven 多模块结构**：

```text
yudao-dependencies        # BOM：统一三方依赖版本
yudao-framework           # 横切能力：yudao-common + 13 个 starter
                          #   （web / security / mybatis / redis / job / mq / tenant
                          #     / data-permission / websocket / excel / protection / …）
yudao-server              # 唯一启动入口（application.yaml + local / dev profile）
yudao-module-system       # 业务模块：系统管理（用户/角色/菜单/租户/…）
yudao-module-infra        # 业务模块：基础设施（代码生成/文件/定时任务/…）
yudao-ui/                 # ⚠️ 官方附带的前端参考实现，仅作对照，不是开发对象
                          #   （实际前端 = 仓库根的 love_baby_web）
```

**模块内分层**（以 module-system 为准，六层）：

```text
controller/
├── admin/（admin-api：VO + controller，供管理后台）
└── app/ （app-api ：VO + controller，供 App）
        ↓ convert/（MapStruct 转换器）
service/（接口 + impl，业务逻辑）
        ↓
dal/（dataobject 数据对象 + mysql Mapper）
enums / job / mq / framework（模块级配置与扩展）
```

**后续开发套路（架构级）**：

- **新业务模块**：新建 `yudao-module-xxx`（照抄 module-system 包结构）→ 根 pom 加 `<module>` → yudao-server 加依赖；新表 SQL 进 `sql/mysql/`。
- **现有模块加功能**：自下而上 DO → Mapper → Service → Controller；管理后台用的进 `controller/admin`，App 用的进 `controller/app`。
- **环境配置**：`application-local.yaml`（本地，Quartz 默认关）/ `application-dev.yaml`；版本号由 `yudao-dependencies` 统一管理，不要在业务模块写死版本。

➡️ **详细开发手册：[love_baby_server/CLAUDE.md](love_baby_server/CLAUDE.md)（✅ 已完备，2026-10-26：双通道/分层实况[love 无 convert/、无测试]/错误码段纪律/逻辑删 vs 物理删/@TenantIgnore/uk 覆盖写/IDOR 口径/双环境配置与编译验证铁律）**

---

## 3. love_baby_web/ — 管理后台前端（vben v5 monorepo）

**定位**：基于 vue-vben-admin `5.7.0`（yudao 版 `2026.09.0-snapshot`）的 B 端管理后台，消费后端 **admin-api**。

**技术栈**：Vue 3 + Vite + TypeScript，pnpm workspace + Turborepo；oxlint / eslint / stylelint / cspell 全套校验。

**monorepo 分层**：

```text
apps/                     # 5 个同功能应用变体，仅 UI 库不同：
│   web-antd / web-antdv-next / web-ele / web-naive / web-tdesign
│   ⚠️ 主力变体已定为 web-antdv-next（2026-10-16 用户指定，
│      覆盖早前「建议 web-antd」的推荐；INTEGRATION-DESIGN §8.2 已同步），
│      业务页面写主力一份，勿逐个维护五份
packages/
├── @core                 # 框架核心（base / composables / preferences / ui-kit）
│                         #   ★ 不含业务，原则上勿改
├── effects/              # 业务抽象层（common-ui / request / access / layouts / hooks）
│                         #   跨 app 复用的业务封装放这
├── constants / icons / locales / preferences / stores / styles / types / utils
│                         # 共享基础包
```

app 内部结构（以 web-antd 为例）：`views/<业务域>/` 页面、`api/<模块>/` 接口封装、`router/routes/` 路由、`adapter/` 表单/表格适配器、`layouts/` + `components/` 骨架；`#/*` alias 指向 `src/*`。

**后续开发套路（架构级）**：新功能 = api 封装（对接 admin-api）+ views 页面 + 路由注册，优先复用 `effects/common-ui` 现成封装；确需改 `packages/` 时先想清楚是否五变体同构、是否影响 vben 升级。

➡️ **详细开发手册：[love_baby_web/CLAUDE.md](love_baby_web/CLAUDE.md)（✅ 已完备，2026-10-26：动态菜单零路由/新功能三件套/CRUD 两形态清单/RangePicker 日期坑/401 刷新与加解密/vite 双环境代理）**

---

## 开发手册规划

| 子项目 | 手册位置 | 状态 |
| --- | --- | --- |
| love_baby（Kuikly 客户端） | `love_baby/CLAUDE.md` | ✅ 已完备 |
| love_baby_server（Java 后端） | `love_baby_server/CLAUDE.md` | ✅ 已完备（2026-10-26） |
| love_baby_web（管理后台） | `love_baby_web/CLAUDE.md` | ✅ 已完备（2026-10-26） |

三端对接实施设计（App 登录注册 + yudao-module-love 业务模块 + 后台管理页，含表设计/API/里程碑）：[INTEGRATION-DESIGN.md](INTEGRATION-DESIGN.md)（2026-10-15 定稿）。

新手册补充后，更新本表与本文件中各项目的「详细开发手册」指向。
