# Vendored: KuiklyWebsocket（社区组件源码内嵌）

本目录为 [Kuikly-contrib/KuiklyWebsocket](https://github.com/Kuikly-contrib/KuiklyWebsocket)
的**源码 vendor**（原样拷贝、保留原包名 `com.tencent.kuiklybase.websocket`，便于与上游 diff）：

- 上游锚点：commit `0bcf9786c9edcb96fbab77fcb941d0b9d86df483`（2026-04-09，main 分支最后一次推送）
- 范围：`KuiklyWebsocket/src/commonMain` 全部 5 文件（WebSocketManager / WebSocketModule /
  WebSocketNativeBridge / WebSocketConfig / WebSocketData）+ `KuiklyWebsocketAndroid` 的
  `KRWebSocketModule.kt`（Android 原生实现，放 androidApp 同包路径）
- **M35（2026-10-27）补齐两端原生半边**（均 verbatim）：
  `KuiklyWebsocketIOS` 的 `KRWebSocketModule.h/.m`（ObjC / NSURLSessionWebSocketTask /
  Network.framework，放 `iosApp/iosApp/KuiklyExpand/Modules/` 并进 pbxproj；依赖
  OpenKuiklyIOSRender 2.7.0——Podfile 已有，NSClassFromString 按 Module 名自动发现，无需手动注册）
  且 `KuiklyWebsocketOhos` 的 `KRWebSocketModule.ets`（ArkTS / @ohos.net.webSocket，放
  `ohosApp/entry/src/main/ets/kuikly/modules/`，KuiklyViewDelegate
  getCustomRenderModuleCreatorRegisterMap 显式注册；依赖 @kuikly-open/render >=2.7.0，
  ohosApp 现为 2.7.0 满足）。common 侧注册：iosMain / ohosMain 的 platformExternalModules
  各注册 `WebSocketModule`（ohosMain actual 为 M35 新建——此前 ohos 管线缺 actual 本就编不过）
- **未拷**：`WebSocketView.kt`（老 DSL 的调试状态组件，本项目未用且项目禁用老 DSL）、
  iOS podspec / ohos ohpm 打包壳（以源文件直入宿主壳方式接入，不走包管理）

## 为什么 vendor 而不是 Maven 依赖

上游 README 的坐标 `com.tencent.kuiklybase:KuiklyWebsocket:1.0.0-2.0.21` **实际不可消费**：
腾讯镜像（mirrors.tencent.com/nexus/repository/maven-tencent）上 maven-metadata.xml 与 POM
在，但所有平台变体产物实体 404（Gradle module metadata 的 files 全空）；Maven Central /
JitPack 亦无此坐标。源码 vendor 是唯一稳定接入路径（2026-10-27 逐一实证）。

## 本地适配（与上游的差异）

- 无代码改动，仅注释/本文档。编译目标 core `2.29.0-2.1.21`（上游按 `2.7.0-2.1.21` 构建）。
- 服务端配合：yudao `JsonWebSocketMessageHandler` 的 "ping" 帧由固定回 `"pong"` 改为
  **回显原 payload**——组件心跳为 echo 模式（`data == pingPayload` 才算 Pong），固定 "pong"
  对不上会导致 60s 误判超时重连循环（详见该文件内注释）。
- 心跳配置：`pingPayload="ping"`、interval 30s（对齐旧自研 okhttp RFC ping 30s 节奏）、
  maxMissedPongs 3；重连走默认（2s→60s 指数退避+jitter，无限次）。
- `lifecycleAware` 保持默认 true 但不接线 onAppBackground/Foreground（无人调用即不生效），
  后台连接语义与自研版一致（后台静默断开由重连+轮询兜底）；接线与否留多端期再议。
- **验证状态（M35 定稿）**：Android 全链已验证（编译+语义平移）；**ohosArm64 已在 Windows
  实证编译绿**（KBA 工具链 `2.0.21-KBA-010`，`:shared:compileKotlinOhosArm64` BUILD
  SUCCESSFUL——commonMain 全量 19 业务域 + 本组件 5 文件对 KBA Kotlin 2.0.21 零兼容问题；
  唯二适配在 shared 侧：`kotlin.time.Clock` 改 TimeKit expect/actual〔2.1.20+ stdlib 才有〕、
  ohosMain 源集手工接线；konan 首次构建依赖 llvm/libffi 由腾讯镜像灌缓存）。**iOS 与 ArkTS
  原生件仍按「源码就位、注册就位、编译待首次构建核」交付**：iOS K/N 目标被 cocoapods 插件在
  非 mac 主机整体禁用（compileKotlinIosArm64 SKIPPED 实证），ArkTS 需 DevEco；common 侧
  （iOS/ohos actual 与 Android 同构，同 two-line mapOf）风险极低。**边界**：ohos 验证止步
  Kotlin 编译（klib/目标码），未做 link（so 产物）——链接需 KBA sysroot 依赖
  `ohos-aarch64-sysroot-15`，腾讯镜像已将其撤为 `.invalid` 墓碑，首次 link 待 DevEco/SDK
  环境补齐。cleartext：本地 ws:// 调试在 iOS 需 Info.plist ATS 例外、
  ohos 需 module.json5 cleartextTraffic 许可（均待两端首次真调时按需加，远程 https→wss 不涉及）。

## 更新方式

上游近乎停更（1 star / 半年无推送），正常无需跟进；若要同步：重下上游仓库，diff 本目录
5+1 文件，人工过一遍后替换并更新本文件锚点 commit。
