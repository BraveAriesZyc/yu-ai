# Vendored: KuiklyWebsocket（社区组件源码内嵌）

本目录为 [Kuikly-contrib/KuiklyWebsocket](https://github.com/Kuikly-contrib/KuiklyWebsocket)
的**源码 vendor**（原样拷贝、保留原包名 `com.tencent.kuiklybase.websocket`，便于与上游 diff）：

- 上游锚点：commit `0bcf9786c9edcb96fbab77fcb941d0b9d86df483`（2026-04-09，main 分支最后一次推送）
- 范围：`KuiklyWebsocket/src/commonMain` 全部 5 文件（WebSocketManager / WebSocketModule /
  WebSocketNativeBridge / WebSocketConfig / WebSocketData）+ `KuiklyWebsocketAndroid` 的
  `KRWebSocketModule.kt`（Android 原生实现，放 androidApp 同包路径）
- **未拷**：`WebSocketView.kt`（老 DSL 的调试状态组件，本项目未用且项目禁用老 DSL）、
  iOS pod / 鸿蒙 HAR（多端开工时再评估，common 代码本身跨端）

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

## 更新方式

上游近乎停更（1 star / 半年无推送），正常无需跟进；若要同步：重下上游仓库，diff 本目录
5+1 文件，人工过一遍后替换并更新本文件锚点 commit。
