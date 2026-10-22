# Kuikly 文档导航手册

> 官方文档站:https://kuikly.tds.qq.com/
> 本项目 UI 层使用 **Kuikly Compose DSL**(标准 Jetpack Compose 语法,`com.tencent.kuikly.compose.*`),
> 页面入口/基建沿用 core 的 **Pager 体系**(`com.tencent.kuikly.core.*`)。
> 所以:**写 UI 查 Compose 站,页面入口/桥接/网络/编译查 DevGuide 站。**

---

## 一、按场景速查(什么时候查什么)

| 场景 | 查这里 |
|---|---|
| 环境搭建 / 跑通 Demo | [快速开始-环境搭建](https://kuikly.tds.qq.com/QuickStart/env-setup.html) / [第一个页面](https://kuikly.tds.qq.com/QuickStart/hello-world.html) |
| 新建一个页面 | [Compose 快速开始](https://kuikly.tds.qq.com/Compose/getting-started.html) + [页面入口 Pager](https://kuikly.tds.qq.com/DevGuide/pager.html)(`@Page` 注解注册) |
| 布局(约等于 Flexbox) | [Compose 布局组件](https://kuikly.tds.qq.com/Compose/layout.html) / [Flexbox 实战教程](https://kuikly.tds.qq.com/DevGuide/flexbox-in-action.html) |
| 常用组件(Text/Image/输入框等) | [Compose 核心组件](https://kuikly.tds.qq.com/Compose/core-components.html) |
| Modifier / 样式 | [Compose Modifier](https://kuikly.tds.qq.com/Compose/modifier.html) |
| 状态管理 / remember | [Compose 状态管理](https://kuikly.tds.qq.com/Compose/status-management.html) / [ViewModel](https://kuikly.tds.qq.com/Compose/view-model.html) |
| 列表 / 长列表 | [Compose 列表与滚动](https://kuikly.tds.qq.com/Compose/list-and-scroll.html) |
| 手势(点击/滑动/拖拽) | [Compose 手势系统](https://kuikly.tds.qq.com/Compose/gesture-system.html) |
| 动画 | [Compose 动画系统](https://kuikly.tds.qq.com/Compose/animation-system.html) / [Native 属性动画](https://kuikly.tds.qq.com/Compose/native-animation.html) / [重组性能分析](https://kuikly.tds.qq.com/Compose/recomposition-performance.html) |
| 页面跳转 / 关闭 | [打开和关闭 Kuikly 页面](https://kuikly.tds.qq.com/DevGuide/open-and-close-page.html) + 本项目 `base/BasePager.kt`、`RouterPage.kt` |
| 调 Native 能力(桥接) | [使用 Module 调 Native](https://kuikly.tds.qq.com/DevGuide/module.html) + [扩展原生 API](https://kuikly.tds.qq.com/Compose/extend-native-api.html)(参考本项目 `base/BridgeModule.kt`) |
| 内嵌原生 View | [扩展原生 UI 组件](https://kuikly.tds.qq.com/Compose/extend-native-ui.html) |
| 网络请求 | [DevGuide 网络请求](https://kuikly.tds.qq.com/DevGuide/network.html)(KMP 也可用 Ktor) |
| 资源 / 图片 | [Compose 资源管理](https://kuikly.tds.qq.com/Compose/resource-management.html) / [Assets 资源与图片](https://kuikly.tds.qq.com/DevGuide/assets-resource.html) |
| 多线程 / 协程 | [Compose 多线程与协程](https://kuikly.tds.qq.com/Compose/thread-and-coroutines.html) |
| 全局事件 / 通知 | [发送事件通知](https://kuikly.tds.qq.com/DevGuide/notify.html) |
| 返回键拦截 | [接入系统返回键](https://kuikly.tds.qq.com/DevGuide/back-press-handler.html) |
| 文本测量 / 富文本 / 表情 | [文本尺寸测量](https://kuikly.tds.qq.com/DevGuide/text-measure.html) / [自定义表情实践](https://kuikly.tds.qq.com/DevGuide/text-post-processor-guide.html) |
| H5 / JS-SDK / 字体 | [引入第三方 JS-SDK](https://kuikly.tds.qq.com/DevGuide/web-import-jssdk.html) / [H5 SPA](https://kuikly.tds.qq.com/DevGuide/h5-spa-demo.html) / [H5 自定义字体](https://kuikly.tds.qq.com/DevGuide/h5-custom-font.html) |
| 鸿蒙(KN 编译/适配) | [KMP 模块鸿蒙 Kotlin/Native 适配](https://kuikly.tds.qq.com/DevGuide/kuiklybase-ohos-kn.html) + 根目录 `build.ohos.gradle.kts` |
| 编译报错 / 多模块 | [编译指引与常见问题](https://kuikly.tds.qq.com/DevGuide/compile_skills.html) / [工程多模块](https://kuikly.tds.qq.com/DevGuide/multi_module.html) |
| 性能调优 | [重组性能分析](https://kuikly.tds.qq.com/Compose/recomposition-performance.html) / [UI Inspector](https://kuikly.tds.qq.com/Compose/ui-inspector.html) |
| 找现成组件 | [社区组件市场](https://kuikly.tds.qq.com/Community/component_market.html) |
| 不确定某能力是否支持 | [Compose 能力全览](https://kuikly.tds.qq.com/Compose/status.html) / [FAQ](https://kuikly.tds.qq.com/Compose/faq.html) |
| 理解框架原理 | [架构介绍](https://kuikly.tds.qq.com/Introduction/arch.html) / [跨端工程模式](https://kuikly.tds.qq.com/Introduction/paradigm.html) |

---

## 二、文档站完整地图

### 1. Compose 站(本项目主力,UI 写法) — https://kuikly.tds.qq.com/Compose/overview.html

Kuikly Compose = 在 Kuikly 跨端引擎上支持**标准 Jetpack Compose DSL**,原生渲染,支持动态化,覆盖 Android/iOS/鸿蒙/H5/微信小程序。

- 入门:[快速开始](https://kuikly.tds.qq.com/Compose/getting-started.html) · [入门指南](https://kuikly.tds.qq.com/Compose/how-to-read.html) · [能力全览](https://kuikly.tds.qq.com/Compose/status.html)
- 指南:[状态管理](https://kuikly.tds.qq.com/Compose/status-management.html) · [布局](https://kuikly.tds.qq.com/Compose/layout.html) · [列表与滚动](https://kuikly.tds.qq.com/Compose/list-and-scroll.html) · [核心组件](https://kuikly.tds.qq.com/Compose/core-components.html) · [文本选中](https://kuikly.tds.qq.com/Compose/text-selection.html) · [Modifier](https://kuikly.tds.qq.com/Compose/modifier.html) · [动画系统](https://kuikly.tds.qq.com/Compose/animation-system.html) · [Native 属性动画](https://kuikly.tds.qq.com/Compose/native-animation.html) · [手势系统](https://kuikly.tds.qq.com/Compose/gesture-system.html) · [多线程与协程](https://kuikly.tds.qq.com/Compose/thread-and-coroutines.html) · [ViewModel](https://kuikly.tds.qq.com/Compose/view-model.html)
- 集成扩展:[扩展 Kuikly DSL UI 组件](https://kuikly.tds.qq.com/Compose/extend-kuikly-dsl-ui.html) · [扩展原生 UI](https://kuikly.tds.qq.com/Compose/extend-native-ui.html) · [扩展原生 API](https://kuikly.tds.qq.com/Compose/extend-native-api.html)
- 工具链:[资源管理](https://kuikly.tds.qq.com/Compose/resource-management.html) · [预览](https://kuikly.tds.qq.com/Compose/preview.html) · [UI Inspector](https://kuikly.tds.qq.com/Compose/ui-inspector.html) · [重组性能分析](https://kuikly.tds.qq.com/Compose/recomposition-performance.html)
- [FAQ](https://kuikly.tds.qq.com/Compose/faq.html)

### 2. DevGuide 站(页面入口/Pager 体系/基建/编译) — https://kuikly.tds.qq.com/DevGuide/dev-guide-overview.html

> 本项目页面入口是 `@Page` 注册的 `Pager`(见 `base/BasePager.kt`),页面内 UI 用 Compose DSL 写,故 Pager 系列必看,attr/event/directive/ViewRef 等**老 DSL 语法仅在维护老代码时参考**。

- 快速开始:[环境搭建](https://kuikly.tds.qq.com/QuickStart/env-setup.html) · [第一个页面](https://kuikly.tds.qq.com/QuickStart/hello-world.html)
- 入门:[Pager](https://kuikly.tds.qq.com/DevGuide/pager.html) · [PagerData](https://kuikly.tds.qq.com/DevGuide/page-data.html) · [Pager 生命周期](https://kuikly.tds.qq.com/DevGuide/pager-lifecycle.html) · [页面事件](https://kuikly.tds.qq.com/DevGuide/pager-event.html) · [属性 attr](https://kuikly.tds.qq.com/DevGuide/attr.html) · [事件 event](https://kuikly.tds.qq.com/DevGuide/event.html) · [响应式更新](https://kuikly.tds.qq.com/DevGuide/reactive-update.html) · [指令](https://kuikly.tds.qq.com/DevGuide/directive.html) · [ViewRef](https://kuikly.tds.qq.com/DevGuide/view-ref.html) · [ComposeView](https://kuikly.tds.qq.com/DevGuide/compose-view.html) · [延时任务](https://kuikly.tds.qq.com/DevGuide/set-timeout.html) · [Module 调 Native](https://kuikly.tds.qq.com/DevGuide/module.html) · [打开/关闭页面](https://kuikly.tds.qq.com/DevGuide/open-and-close-page.html) · [事件通知](https://kuikly.tds.qq.com/DevGuide/notify.html) · [网络请求](https://kuikly.tds.qq.com/DevGuide/network.html) · [Assets 资源](https://kuikly.tds.qq.com/DevGuide/assets-resource.html) · [返回键](https://kuikly.tds.qq.com/DevGuide/back-press-handler.html)
- Flexbox:[基础](https://kuikly.tds.qq.com/DevGuide/flexbox-basic.html) · [实战](https://kuikly.tds.qq.com/DevGuide/flexbox-in-action.html)
- 进阶:[自定义属性](https://kuikly.tds.qq.com/DevGuide/view-external-prop.html) · [文本测量](https://kuikly.tds.qq.com/DevGuide/text-measure.html) · [表情实践](https://kuikly.tds.qq.com/DevGuide/text-post-processor-guide.html) · [设计尺寸与字号缩放](https://kuikly.tds.qq.com/DevGuide/font-size-and-display-scale.html) · [组件宽高坐标](https://kuikly.tds.qq.com/DevGuide/get-component-size-and-position.html) · [Protobuf](https://kuikly.tds.qq.com/DevGuide/protobuf.html) · [协程多线程](https://kuikly.tds.qq.com/DevGuide/thread-and-coroutines.html) · [多页面原理](https://kuikly.tds.qq.com/DevGuide/multi-page.html)
- 工程集成:[工程多模块](https://kuikly.tds.qq.com/DevGuide/multi_module.html) · [模块与版本配置](https://kuikly.tds.qq.com/DevGuide/version_skills.html) · [编译指引与常见问题](https://kuikly.tds.qq.com/DevGuide/compile_skills.html)
- KuiklyBase:[鸿蒙 KN 适配](https://kuikly.tds.qq.com/DevGuide/kuiklybase-ohos-kn.html) · [其他基建能力](https://kuikly.tds.qq.com/DevGuide/kuiklybase-feat-remaining.html)
- Web:[JS-SDK](https://kuikly.tds.qq.com/DevGuide/web-import-jssdk.html) · [图片域名路径](https://kuikly.tds.qq.com/DevGuide/h5-image-path.html) · [H5 SPA](https://kuikly.tds.qq.com/DevGuide/h5-spa-demo.html) · [H5 字体](https://kuikly.tds.qq.com/DevGuide/h5-custom-font.html) · [cssClass](https://kuikly.tds.qq.com/DevGuide/h5-css-class.html)

### 3. Introduction 站(理解框架) — https://kuikly.tds.qq.com/Introduction/arch.html

- [架构介绍](https://kuikly.tds.qq.com/Introduction/arch.html):Kuikly 基于 KMP,分 KuiklyUI(Core/API/DSL/Render 四模块)+ KuiklyBase(逻辑跨端基建);原生渲染;可动态化
- [跨端工程模式](https://kuikly.tds.qq.com/Introduction/paradigm.html) · [应用场景案例](https://kuikly.tds.qq.com/Introduction/application_cases.html) · [Demo 体验](https://kuikly.tds.qq.com/Introduction/demo_experience.html)
- 周边:[组件市场](https://kuikly.tds.qq.com/Community/component_market.html) · 监控 [Bugly](https://bugly.tds.qq.com/) · 发布 [Shiply](https://shiply.tds.qq.com/)

---

## 三、本项目关键认知

1. **双 DSL 并存**:UI 用 Compose DSL(`com.tencent.kuikly.compose.*`,与官方 Compose API 基本一致,AI 友好);页面注册、Module 桥接、Pager 生命周期等基建是 core 包(`com.tencent.kuikly.core.*`)。
2. **页面 = `@Page` 注解的 Pager 类**,Compose 内容通过 `setContent {}` 挂载(参考 `shared/src/commonMain/kotlin/com/zyc/love_baby/RouterPage.kt`)。
3. **H5 产物**为 `nativevue2.js`(见 `shared/build.gradle.kts` js(IR) 目标),由 `static_server/`(Node/Koa)托管。
4. **版本**:Kuikly `2.29.0`、Kotlin `2.1.21`、鸿蒙 Kotlin `2.0.21-ohos`(定义在 `buildSrc/src/main/java/KotlinBuildVar.kt`)。
5. **仓库镜像**走了腾讯 maven(见根 `settings.gradle.kts`),依赖解析慢/失败时先查镜像。
