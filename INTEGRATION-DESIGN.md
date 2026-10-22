# 情侣 App 后端对接与新业务模块设计方案（定稿）

> 2026-10-15 · 本文档为定稿，存根目录，配套根 CLAUDE.md 使用。已吸收三份评审全部 blocker/major 意见并核实修正事实表述；个别无法在仓内核实的点显式标注「待确认」并给定案时点。同日二次评审（扩展性专项）补 §12 扩展性预留与 D-13，修正 §4/§7/§11 事实偏差。

## 1. 背景与目标

**背景**：`love_baby`（Kuikly 跨端 App）目前为纯本地假数据；`love_baby_server`（yudao 单体，仅启用 system + infra）；`love_baby_web`（vben5 管理后台，仅 system/infra 页面）。三者架构手册见根 CLAUDE.md。

**目标**：不改既有架构，纯增量完成三件事——

1. App 端适配登录注册（自建 love_user 用户体系，复用 system 的 OAuth2/SMS 基础设施）；
2. 服务端新增 `yudao-module-love` 业务模块（情侣绑定 / 待办 / 心情 / 悄悄话）；
3. 新增 app-api（App 消费）与 admin-api（后台管理）两组接口，并落地 web-antd 管理页。

**范围（首期做）**：注册/登录/登出/重置密码、邀请码绑定/解绑、待办同步、每日心情、悄悄话（文本 + 心愿快照消息）、后台四管理页。
**范围（首期不做，显式声明）**：~~心愿库服务端实体（love_wish 表与 wish 组 API）~~（**M14 起移出：已实施**，见 §5.2 wish 组 / D-19）、消息用户侧撤回、推送、离线可写、字段级冲突合并、互踢、图形验证码 App 侧组件、多端 web 变体同步、mood 后台管理页（D-13）、设备/IP 采集与登录设备管理（§12.1–12.2 预留，二期）。

## 2. 总体方案图

```text
┌────────────────────────┐   app-api(/app-api/love/**, userType=1)
│  love_baby (Kuikly App) │──────────────────────────────────┐
│  network/ + SessionStore │  HTTP 200 + CommonResult          │
│  Gate→三态→三Store写穿透  │                                   ▼
└────────────────────────┘                    ┌──────────────────────────────┐
                                              │ love_baby_server (:48080)     │
┌────────────────────────┐  admin-api          │  yudao-module-love (新增)      │
│ love_baby_web (vben5)   │──────────────────▶│   controller/app + admin      │
│ web-antd 承载 /love/**  │ (/admin-api/love/**)│   service → dal (5 张 love_*) │
│ codegen frontType=41    │                    │  复用 system: OAuth2TokenCommon│
└────────────────────────┘                     │  Api / SmsCodeApi / 权限/菜单  │
                                               └──────────┬───────────────────┘
                                              MySQL(love.sql 建表+菜单+client) │ Redis
```

关键原则：**love 模块绝不自造 token/短信/权限**，全部复用 system 既有栈；ruoyi-vue-pro.sql 零改动，全部增量进 `sql/mysql/love.sql`。

## 3. 里程碑计划

依赖链：M2 依赖 M1 的 token 闭环与 couple-app client；M3 依赖 M2 的 couple_id 归属；M4 依赖 M3 全功能在库。

### M1 服务端基座 + App 鉴权闭环

- 范围：love 模块骨架（pom 接线 §6.3）、love_user 表、auth 接口组（§5.2）、oauth2 client INSERT、App 端 network/ + SessionStore + Gate 登录注册 UI（§7.1–7.3）。
- 落地物：yudao-module-love 可编译、love.sql（M1 部分）、App 网络层与登录页。
- 验收（可勾选）：`mvn compile` 通过；注册→登录→杀进程重启保持登录→登出回 Gate；code==401 单飞刷新重放成功；并发 401 仅触发一次刷新。
- 验收清单对应 §11 App 端第 1–3、9 条、后端第 1–5 条。

### M2 情侣绑定 + 用户资料

- 范围：couple 组（含 unbind）+ user 组接口、love_couple 表、App 绑定 UI（features/couple/，三态状态机 §7.3）。
- 验收：A 生成邀请码→B 绑定成功；无效/过期码/重复绑定被 1_003_004 段拒绝；任一方解绑后双方端回到「未绑定」引导态；重绑成功（uk 兜底无冲突）。

### M3 核心业务同步 + 管理后台

- 范围：todo/mood/message 接口组、三 Store 写穿透与拉取触发点（§7.4）、codegen 生成四管理页（frontType=41）并接入 web-antd。
- 验收：双设备待办/悄悄话互通；App 产生的数据在 admin 各分页可见且字段一致；admin 删除/解绑按 D-11/D-12 策略端上生效；web-antd 四页分页/查询/删除可用、权限串核对通过。

### M4 验收加固与上线

- 范围：B-2 多端网络实测（H5/ohos/iOS）、§11 全量回归、D-4 上线清单逐项执行。
- 验收：H5 httpRequest 实测结论记录在案；上线清单全部勾选。

## 4. 数据模型（love.sql，DDL 级字段清单）

公共字段（继承 BaseDO）：`creator varchar(64)`、`create_time datetime`、`updater varchar(64)`、`update_time datetime`、`deleted bit(1) default 0`（逻辑删除）。下表只列业务字段。

### love_user（C 端用户）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | MySQL 自增（id-type=NONE 经 IdTypeEnvironmentPostProcessor 在 MySQL 下解析为 AUTO，DB 自增回填，与仓内既有表惯例一致） |
| mobile | varchar(32) | **uk_mobile 唯一**；admin 删除时改写为 `concat(mobile,'#',id)` 释放唯一键（yudao 惯例手法，定案于 M1 开发前）。**M3 实施修正：16→32**——11 位手机号 + '#' + 5 位 id 即超 16 列宽，id≥10000 后 admin 删除必 Data too long 500；ALTER 落 love.sql M3 段末尾（幂等可重放），Java concat 逻辑不变 |
| password | varchar(64) | BCrypt，可空（纯短信注册用户为空） |
| nickname | varchar(32) | 默认「用户+mobile 后四位」 |
| avatar | varchar(256) | 可空 |
| status | tinyint | 0 启用 / 1 停用；**所有 app 业务接口入口统一校验**（§6.5），停用拒绝并返回 1_003_005_002 |
| invite_code | varchar(8) | 我的邀请码，可空，idx_invite_code |
| invite_expire_time | datetime | 邀请码过期时间；**惰性校验**（校验时判 expire_time，无定时任务） |
| last_login_ip | varchar(50) | 登录成功更新（§12.1，对齐 admin updateUserLogin 惯例） |
| last_login_time | datetime | 同上 |

### love_couple（情侣关系）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| user_a_id | bigint | 发起方，**uk_user_a 唯一** |
| user_b_id | bigint | 受邀方，**uk_user_b 唯一** |
| status | tinyint | 0 正常 |
| bind_time | datetime | |

唯一键与逻辑删除冲突的裁决：**本表解绑采用物理删除**（LoveCoupleDO 不继承 BaseDO，自带 create_time/update_time，不启用 @TableLogic），关系行不留史，从而 uk_user_a/uk_user_b 天然可重绑，并发兜底语义干净（D-11）。审计归对靠 love_message/love_todo 冗余快照列。

### love_todo（待办，couple 共享 + owner 归属）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| couple_id | bigint | idx_couple，列表按此过滤 |
| owner_id | bigint | 创建人；列表对 couple 双方可见 |
| title | varchar(64) | |
| done | bit | |
| tag | varchar(32) | 标签文本（家庭/恋爱/健康/生活/随手记；客户端按文本渲染，TodoPage.kt:205 实测） |
| color | varchar(16) | hex 色值字符串（Repository 层 ↔ Color，对应客户端 tagColor） |

### love_mood_record（每日心情）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| couple_id | bigint | idx_couple_month(couple_id, mood_date) |
| user_id | bigint | **uk_user_date(user_id, mood_date)** 一人一天一条，重复写覆盖 |
| mood_date | date | |
| mood_type | tinyint（可空） | **（M3.5/D-14 废弃）旧 1-5 枚举**：仅存量行有值，新记录恒写 NULL（覆盖写时也显式置 NULL，LambdaUpdateWrapper .set）；**不复用此列存 optionId**——存量行 1-5 会与真实 love_mood_option.id 撞号致跨 couple 渲染错乱 |
| option_id | bigint（可空，M3.5 加） | 心情选项编号（love_mood_option.id，记录时校验 couple 归属） |
| mood_label | varchar(16)（可空，M3.5 加） | **心情名快照（记录时点定格，§12.5.2 先快照后实体）** |
| emoji | varchar(16)（可空，M3.5 加） | 表情快照（记录时点） |
| color | varchar(16)（可空，M3.5 加） | 颜色 hex 快照（记录时点）——**删除选项后历史月历靠三快照列渲染，不变灰**（D-14，love_message 心愿快照同哲学） |

> 存量回填：love.sql M3.5 追加段按 mood_type 1-5 映射回填快照三列（`WHERE color IS NULL` 幂等）；存量 option_id 不反填（无字典行可指）。

### love_mood_option（心情选项，couple 私有字典——M3.5/D-14 新增）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 自增（**不建 (couple_id,label) uk**：逻辑删除行占位 uk 是 uk_user_date 已踩过的 DDL 坑；重名防 UX 由 service 查重 1_003_002_003，并发极端重复可接受——可删可再加） |
| couple_id | bigint | idx_couple；**绑定事务内播种后**双方共享一份，app-api 增删双端经拉取互通 |
| label | varchar(16) | 心情名 |
| emoji | varchar(16) | |
| color | varchar(16) | hex（与 todo tag / wish color 同款约定） |
| sort | int | DEFAULT 999：播种行继承模板 sort（1..5），用户新增恒 999 落尾；list 按 sort asc, id asc |

### love_mood_option_default（心情默认选项模板，B 端管理——M3.5/D-14 新增）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| label / emoji / color | varchar(16) | 初始 5 行 = 原 MoodTypeEnum 五档 + 客户端配色原样迁入 |
| sort | int | DEFAULT 0 |
| status | tinyint | 0 启用/1 停用（CommonStatusEnum）；**仅启用行参与播种**；停用/删除不回灌已绑定 couple（播种即复制） |

### love_message（悄悄话/心愿消息）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| couple_id | bigint | idx_couple_time(couple_id, id) 增量扫 |
| sender_id | bigint | 发送方 |
| receiver_id | bigint | 对方快照（couple 物理解绑后审计仍可归对） |
| kind | tinyint | 1 文本悄悄话 / 2 心愿消息 / 3 图片消息（M10 补记，content 存图片完整 URL） |
| content | varchar(512) | 文本 / 心愿文案快照 / 图片完整 URL（M10 由 500→512 放宽，@Length 同步） |
| wish_key | varchar(32) | 可空，心愿 key 快照 |
| color | varchar(16) | 可空，hex 快照 |
| icon | varchar(32) | 可空，icon name 快照 |
| audit_status | tinyint | 0 待审 / 1 通过 / 2 命中；**首期恒写 1 兜底**，事前审核二期接入（§12.3） |
| recalled | bit(1) 默认 b'0' | **M12 新增（2026-10-17）**：是否已撤回（墓碑标记）——撤回不删行，content 原文留档审计，与 deleted 逻辑删独立双列；message/list 照常回传（端上渲染灰条提示），wish-list 与未读计数排除 |

### love_wish（恋爱清单，**M14 新增 2026-10-17**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| couple_id | bigint | idx_couple_id(couple_id, id)；couple 共享双方互见；解绑重绑后旧数据留历史值（D-11） |
| user_id | bigint | 创建人（**删除权限依据：仅创建者可删**，D-19e）；勾选完成不限创建者（服务端双方均可，done_by 记录勾选者；App 交互 2026-10-17 修订为仅对方能勾，D-19） |
| title | varchar(64) | ≤64；@NotBlank 拒纯空白（清单条目无标签兜底，比 todo 的 @NotEmpty 严一档），service trim 落库 |
| done | bit(1) 默认 b'0' | 完成态；状态迁移走 WishMapper 注解 SQL CAS（不走 updateById——undo 须显式置 NULL，MP 忽略 null 字段） |
| done_by | bigint 可空 | 完成人（勾选者，可与创建人不同；未完成 NULL） |
| done_time | datetime 可空 | 完成时间（未完成 NULL） |

无 uk（couple 双方均可添加多条，moment 先例）；与 love_message kind=2 快照**并存不替代**（D-19a）；完成消息「恰发一条」由 markDone CAS 单闸保证，无 celebrated 类防重发列（D-19b）。

### love_moment_like（相册瞬间点赞，**M15 新增 2026-10-17**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| couple_id | bigint | 归属第二道闸（service 已校验瞬间归属，此处冗余落库）；解绑重绑后留历史值（D-11） |
| moment_id | bigint | uk_moment_user(moment_id, user_id) 幂等闸；idx_moment_id 批量计数走此索引；不设外键（删瞬间不级联清赞，D-20h） |
| user_id | bigint | 点赞人（couple 恒 2 人，双方均可赞任意瞬间含自己；likeCount ∈ {0,1,2}） |

点赞 = `INSERT IGNORE` 走 uk 吸收并发重复（幂等）；**取消点赞 = 物理 DELETE**（显式 SQL，uk 与逻辑删除冲突唯一解——墓碑行永久占 uk 位），故本表 deleted 恒 0（D-20b）。同批 love_moment 加 `image_urls` text（多图 URL JSON 数组 ≤9 元素；image_url 列保留=首图冗余，admin/web 零改动；存量单图回填 [image_url]）。

## 5. API 设计

### 5.1 通用约定（已核实，非待确认）

- **鉴权头**：`Authorization: Bearer <token>`。服务端 SecurityFrameworkUtils.obtainAuthorization（L41–54）明确剥除 `Bearer` 前缀（含尾随空格），兼容裸 token 直传与 `?token=` 查询参数兜底（Header 优先）；SecurityProperties.tokenHeader 默认 `Authorization`。直接按 Bearer 实现，无需联调验证。
- **回包形态**：HTTP 状态恒为 200（含鉴权失败——AuthenticationEntryPointImpl 走 ServletUtils.writeJSON(CommonResult.error(UNAUTHORIZED))，security starter 内无 setStatus/sendError），**401 只存在于回包 body 的 code 字段**；真·非 2xx（网关 502/超时）在 Android 端走 success=false + errorMsg 通道。
- **CommonResult**：`{code, data, msg}`，code==0 成功。
- **id 一律 String**：NumberSerializer（yudao-common）对超 2^53-1 的 Long 序列化为字符串、范围内仍输出数字——同一字段类型不定，App 端 DTO 全部按 String 解析，禁止按数字解析 id（兼防 H5 double 精度丢失）。
- **写请求 Content-Type 必须为 application/json**（原因见 §7.1 第 4 条）。
- 多租户关闭（application.yaml 显式 false）；love 各 DO 加 @TenantIgnore 双保险（§10 M-1）。

**错误场景 → 端上表现对照表**：

| 场景 | 服务端表现 | App 表现 | web-antd 表现 |
| --- | --- | --- | --- |
| token 过期/无效 | 200 + code=401 | 单飞刷新→成功重放；失败清会话回 Gate | vben 内置跳登录页 |
| 权限不足 | 200 + code=403 | toast「无权限」 | 按钮 403 message |
| 账号已停用 | 200 + code=1_003_005_002 | 清会话回 Gate | message 提示 |
| 业务错误 | 200 + code=1_003_xxx | toast msg | message.error(msg)（vben 对 1_003 段默认展示联调首日实测，见 §8.3） |
| 网关不可达/非 2xx | HTTP 非 200 | success=false → 网络异常 toast，不崩 | requestClient 报错 |

### 5.2 app-api（前缀 `/app-api/love`）

**auth 组**（登录响应/刷新响应统一契约：`{accessToken, refreshToken, userId, expiresTime, clientId}`；clientId 字符串 `couple-app`，端上 ApiConfig 另行固化，SessionStore 持久化 clientId 供刷新用）：

| 方法 | 路径 | 鉴权 | 请求体样例 | 说明 | 错误码 |
| --- | --- | --- | --- | --- | --- |
| POST | /love/auth/register | 免登 | `{"mobile":"138...","password":"a123456","code":"888888"}` | 密码注册（短信码校验） | 1_003_005_000 |
| POST | /love/auth/login | 免登 | `{"mobile":"138...","password":"a123456"}` | 密码登录 | 1_003_005_001/002 |
| POST | /love/auth/sms-login | 免登 | `{"mobile":"138...","code":"888888"}` | 短信登录，**未注册自动注册**（首期主路径） | 1_003_005_002 |
| POST | /love/auth/send-sms-code | 免登 | `{"mobile":"138...","scene":1}` | **scene 固定 1（MEMBER_LOGIN）**，见 D-3 | — |
| POST | /love/auth/refresh-token | 免登 | `{"refreshToken":"...","clientId":"couple-app"}` | 刷新（必传 clientId） | — |
| POST | /love/auth/reset-password | 免登 | `{"mobile":"138...","code":"888888","password":"b123456"}` | 重置密码，复用 SmsCodeApi scene=1 | — |
| POST | /love/auth/logout | **需登录** | 无 | 服务端调 OAuth2TokenApi 移除 access+refresh token；端上先调、容忍失败、再清本地 | — |

免登端点共 6 个（register/login/sms-login/send-sms-code/refresh-token/reset-password），需登录 1 个（logout）。

> **测试兜底万能验证码**：~~`888888`~~ **（2026-10-19 已随代码整体移除**——真实短信渠道 WEIWEBS 网建全链路已闭环（双真机终验通过），兜底通道无存在必要；`AppAuthServiceImpl` 字段与 `validateSmsCode` 短路、local/dev/prod 三份 yaml 的 `love.auth.master-code` 段一并删除，D-4 检查项就此关闭）。历史设计：注册/短信登录/重置密码三处短信校验收口在私有方法 `validateSmsCode`，命中万能码直接放行并打 WARN 日志。与 system 层 mock 短信（`application.yaml` sms-code 固定 9999）是两条独立测试路径：~~mock 关闭后万能码仍可用~~（该路径亦已不存在）。

**couple 组**：

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | /love/couple/create-invite | 需登录 | 生成/刷新我的邀请码：存在未过期码则复用，否则重生成 + 续期 7 天（写 love_user.invite_code/invite_expire_time） |
| POST | /love/couple/bind | 需登录 | `{"inviteCode":"AB12CD34"}` 凭码绑定，事务步骤见 D-8 |
| GET | /love/couple/get | 需登录 | 我的绑定关系与伴侣信息（昵称/头像回查 love_user）；未绑定时返回 1_003_004_003 |
| POST | /love/couple/unbind | 需登录 | 双方任一可发起，事务内物理删除 couple 行（D-11） |
| GET | /love/couple/online | 需登录 | **M13 新增（2026-10-17）**：伴侣在线状态——以 WS 内存会话表为准（sender-type=local 单实例，D-18），对方名下任一在线连接即 true；websocket 关闭/未桥接端恒 false（渐进降级）；未绑定 → 1_003_004_003，错误码零新增；**回包为对象形态 AppCoupleOnlineRespVO——端上 toOk 标量落空对象，禁改回 CommonResult\<Boolean\>（M11 冻结决策）**；端上仅聊天页停留期间 5s 轮询（离页重置），无 lastSeen，「最近在线时间」显式不做（D-18） |

**user 组**：GET /love/user/get（个人信息——MEMBER 上下文 buildUserInfo 返回空 Map，昵称头像一律由此接口回查 love_user，见 M-6）；POST /love/user/update（改昵称/头像；消费方 = MinePage 资料编辑，见 §7.7。**M2 实施修正：原设计 PUT → POST**——Kuikly core 2.29 NetworkModule 仅支持 GET/POST（反编译 AAR 核实），app-api 写端点统一 POST，M3 的 todo update/delete 同理届时改；admin-api 不受限仍 RESTful）。

**todo 组**（归属 = couple 共享，owner 仅记录创建人；**所有按 id 写操作在 service 层强制 couple 归属校验**，不属于当前 couple 返回 1_003_001_001，防 IDOR）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /love/todo/list | 全量列表，按 create_time asc, id asc 排序（端上覆盖 TodoStore） |
| POST | /love/todo/create | `{"title","tag","color"}` |
| POST | /love/todo/update | `{"id","title","done","tag","color"}`，含 toggle done；**M3 实施修正：原 PUT → POST**（同 user 组注记，core 2.29 仅 GET/POST） |
| POST | /love/todo/delete | `{"id"}`；**M3 实施修正：原 DELETE?id= → POST body**（同上） |

**mood 组**（M3.5/D-14 起含选项字典；所有按 id 写操作在 service 层强制 couple 归属校验，防 IDOR 同 todo 口径）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /love/mood/record | `{"optionId":"..."}` 记当日（optionId 一律 String 解析 §5.1；服务端反查 couple + 选项归属校验——不存在 1_003_002_000 / 跨 couple 1_003_002_001——快照 label/emoji/color 三列落库、mood_type 置 NULL），uk 冲突则覆盖。**M3.5 实施修正：原 `{"moodType":1}` → `{"optionId"}`**（预生产无旧端，净切换；理由同 user 组 PUT→POST 先例——MoodTypeEnum 随之废弃为存量值域文档，见 §6.2） |
| GET | /love/mood/record/list?month=2026-10 | 按 couple 维度返回双方记录；RespVO 含 `id/userId/moodDate/moodType(存量)/optionId/moodLabel/emoji/color(快照)`；某日无记录 = 回包无该日条目，端上按空缺渲染；**月历取色只吃快照列**（删选项后历史不变灰，D-14） |
| GET | /love/mood/option/list | couple 私有心情选项字典，sort asc, id asc（播种行在前、新增落尾）；空数组 = 空字典属正常回包（模板空或全被删，端上空态自愈） |
| POST | /love/mood/option/create | `{"label","emoji","color"}`；校验绑定、couple 内重名 1_003_002_003、上限 20 1_003_002_002；新增 sort 恒 999 |
| POST | /love/mood/option/delete | `{"id"}` 逻辑删除；历史记录靠快照不受影响；跨 couple 1_003_002_001 |

**message 组**：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /love/message/list?lastId=&limit= | 增量拉取：**limit 默认 50、上限 200**；**空参 lastId 首屏取最近一页（按 id 倒序取 limit 条后正序返回）**；有 lastId 则按 idx_couple_time 顺序扫 `id > lastId`，升序返回 |
| POST | /love/message/send | `{"content":"..."}` 文本悄悄话 |
| POST | /love/message/send-wishes | **快照式**：`{"wishKey","text","color":"#FF6B81","icon":"gift"}` 四字段整体入 love_message（kind=2）；服务端不校验 wish 归属（无 wish 实体，文案字典留端上），两设备还原靠快照字段而非字典版本 |
| POST | /love/message/send-image | **M10 新增（2026-10-17）**：`{"imageUrl"}` 图片消息（kind=3），content 列存图片完整 URL（infra 文件服务上传返回，≤512，content varchar(500)→512）；WS 推送自动继承；错误码零新增：imageUrl 空 → 400 / 未绑定 → 1_003_004_003 |
| GET | /love/message/unread | **M11 新增（2026-10-17）**：聊天未读数（红点口径）= couple 内 id > 我方 love_user.chat_read_cursor 且 sender_id ≠ 自己（kind 1/2/3 全计，admin 逻辑删不计）；未绑定返回 `{"unread":0}`（stats 先例）；**回包为对象形态 AppMessageUnreadRespVO——端上 HttpClient toOk 标量落空对象，禁改回 CommonResult\<Long\>（M11 冻结决策）** |
| POST | /love/message/read | **M11 新增（2026-10-17）**：`{"lastMessageId"}` 标记已读，`GREATEST(chat_read_cursor, lastMessageId)` 只进不退（并发/迟到标记不回退，原生 @Update 绕过 MP 显式 deleted=0）；不校验消息归属（游标只推进自己行，谎报仅自伤）；未绑定 → 1_003_004_003 / 参数空·非正 → 400 |
| POST | /love/message/recall | **M12 新增（2026-10-17）**：`{"messageId"}` 撤回——发送方 2 分钟内（RECALL_WINDOW_SECONDS=120，服务端时钟权威）可撤自己发的消息（kind 1/2/3 均可）；校验链 selectById→null·coupleId 不匹配·senderId≠当前用户 统一 1_003_003_000（防探测）/ 已撤回幂等成功 / 超窗 1_003_003_001（新码）；落库 CAS `recallById`（SET recalled=b'1' 显式 deleted=0 + update_time）；rows>0 推 WS `love-message-recall` `{"messageId"}` 给对方（在线即时变灰，离线靠切 Tab 进页 resync 全量纠偏）；未读计数与 wish-list 排除 recalled 行；回包 CommonResult\<Boolean\> 标量 true（端上只判 Ok，read 先例） |
| GET | /love/message/wish-list?limit= | **M7 新增（2026-10-17）**：心愿只读时间线（kind=2 专用视图），按 id 倒序返回最近 limit 条（端上默认 200）；RespVO 含快照四字段，端上 WishTimelinePage 消费——独立时间线页自持数据，不与 message/list 游标流耦合 |
| POST | /love/message/typing | **M13 新增（2026-10-17）**：「正在输入」信号上报，**无请求体**（create-invite/unbind 先例，服务端方法无 @RequestBody）；不落库、无持久化（D-18）：服务端经 WS 向对方在线连接推 `love-typing` 纯信号（content 单键占位 `{"typing":"1"}`，端上不消费），对方离线/未桥接静默 no-op（AbstractWebSocketMessageSender 既有语义）；节流在端上（3s），服务端不做限流；错误码零新增：未绑定 → 1_003_004_003；回包 CommonResult\<Boolean\> 标量 true（read 先例，端上只判 Ok） |

**wish 组**（**M14 新增，2026-10-17**；恋爱清单实体化——与 message 组 kind=2 心愿卡消息**并存不替代**（D-19a）：kind=2 是快照式聊天消息，love_wish 是可勾选完成的共享清单实体，wish_key 不升级外键（§12.5.2 快照哲学）；错误码 1_003_009：000 心愿不存在（不存在/跨 couple 统一，防探测 recall 口径）/ 001 仅创建者可删；未绑定复用 1_003_004_003；写端点 POST 同 todo 口径；按 id 写操作 service 层强制 couple 归属校验防 IDOR）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /love/wish/list | couple 共享全量列表（双方互见），create_time asc, id asc 稳定序返回；端上整体覆盖本地（todo 先例，§7.4），「进行中在前 / 已完成沉底」分层排序端上做（D-19f） |
| POST | /love/wish/create | `{"title"}`（≤64，@NotBlank）；userId = 当前登录人（创建人，删除权限依据 D-19e），done 固定初始未完成；回包 RespVO 供端上落态（todo create 先例） |
| POST | /love/wish/done | `{"id","done"}` **单口双向**：done=true 走 CAS 置位（markDone `WHERE done = b'0'` 单闸）——每次「未完成→完成」跃迁**恰发一条**庆祝消息（kind=1 文本 `我们一起完成了「title」🎉`，字符串拼接禁 String.format 防标题 % 歧义；senderId=勾选者），重复勾选幂等（doneBy/doneTime 不变、消息不重发）；done=false 清 done/done_by/done_time 三字段（注解 SQL 显式置 NULL）不发消息；undo→re-done 重发为预期（**无 celebrated 列**，D-19b）；done 端点**无事务**（D-19d：RR 事务内 rows=0 重读命中旧快照回错态）；回包 = 重读库真值对象（D-19h：端上原位替换本地行）；App 交互口径修订（2026-10-17）：端上仅对方可勾（发布者点自己心愿不响应），服务端保持双方均可不设限 |
| POST | /love/wish/delete | `{"id"}` 逻辑删除；仅创建者可删（1_003_009_001 用户维度，防误删，moment 口径），任意状态可删；不存在/跨 couple 统一 1_003_009_000；端上传播走全量覆盖 |

**anniversary 组**（**M5/D-17 新增，2026-10-17**；写端点一律 POST 同 todo 口径；按 id 写操作 service 层强制 couple 归属校验防 IDOR，同 todo/mood 口径）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /love/anniversary/list | 全量列表，按 anniv_date asc, id asc 排序，端上整表覆盖（AnniversaryStore），展示序由端上倒数纯函数重排（D-17）；RespVO 含 `id/coupleId/ownerId/title/annivDate（"yyyy-MM-dd" 字符串，全局 LocalDateSerializer 出参）/repeatType/emoji/createTime` |
| POST | /love/anniversary/create | `{"title","annivDate":"yyyy-MM-dd","repeatType","emoji"}`；annivDate 服务端 @Pattern + LocalDate.parse 双重校验（非 4-2-2 格式 400、日历不存在如 02-30 → 1_003_006_002），repeatType 1=每年 2=一次性（值域外 400），title ≤32 / emoji ≤16；回包 RespVO 供端上乐观落态（同 todo create 先例）；**无 update 端点**（D-17：删了重加） |
| POST | /love/anniversary/delete | `{"id"}` 逻辑删除；行不存在 1_003_006_000、跨 couple 1_003_006_001 |

**checkin 组**（**M8 新增，2026-10-17**；无独立错误码——check 幂等照 mood record 先例，未绑定复用 1_003_004_003；无补打：只能打服务端当天，不收日期参数）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /love/checkin/check | 打卡今天，**无请求体**；uk_user_date 幂等——命中即返回原行，未命中 insert + catch DuplicateKeyException 再 select（重复点击回已有记录不打错，照 MoodServiceImpl.java:81-114）；命中行 couple_id 与当前不一致（同日解绑重绑）则覆盖刷新；admin 删过（逻辑删）则复活原行并挂当前 couple。回包 RespVO 含 `id/userId/checkinDate（"yyyy-MM-dd" 字符串）/createTime` |
| GET | /love/checkin/list?month=yyyy-MM | 按月返回 couple 双方记录（缺省当月；非法 month 400）；按 checkin_date asc, id asc 排序；某日无记录 = 回包无该日条目，端上按空缺渲染 |
| GET | /love/checkin/summary | 服务端聚合：commonStreak（**共同连续**——双方同日都打才累计，今天缺一方从昨天起算，上限 365）/ myTotal / partnerTotal（各自全量累计）；跨月 streak 月历单月算不了，表小（一人一天一条）selectListByCoupleId 全量内存聚合 |

**moment 组**（**M9 新增，2026-10-17；M15 多图 + 点赞扩展同日**；错误码 1_003_008：000 瞬间不存在 / 001 仅上传者可删；未绑定复用 1_003_004_003；text 超限走 @Length → 400；图 URL 为 infra 文件服务上传返回的完整地址（M4 上传链路复用，端上选图即上传），无 update（删了重发，D-17 口径）；M15 起多图 imageUrls ≤9（@Size → 400，imageUrls 空 → 服务端归一 [imageUrl] 老端兼容）与点赞 /like（幂等，**零新增错误码**：不存在/跨 couple 统一 000 防探测；不发消息、无 WS 信号 D-20c，对端靠 App 可见期静默轮询感知））：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /love/moment/create | `{"imageUrl","text"}` 发布瞬间（校验绑定；imageUrl @NotBlank ≤512、text 可空 ≤64，空白统一落空串）；**M15 加 imageUrls**（可空 ≤9 @Size，服务端归一：空 → [imageUrl]；image_url 列恒落首图冗余）；回包 RespVO 含 `id/userId/imageUrl/text/createTime`（端上头插落态不重拉）+ **M15 加 `imageUrls/likeCount/liked`**（组装恒非空 imageUrls；likeCount ∈{0,1,2}；liked 按人算；hutool String→List 同名拷贝坑 → service 无条件覆写） |
| GET | /love/moment/list?beforeId=&limit= | **游标分页** id 倒序新→旧：beforeId 空/0 = 取最新一页，非 0 = 取 id 小于它的下一页；limit 缺省 30 上限 100；couple 维度共享（双方同列表）；**M15 起每条组装 imageUrls/likeCount/liked（一页 2 条 like SQL：全赞行 group 计数 + 我赞过的 id 集）** |
| POST | /love/moment/delete | `{"id"}` 逻辑删除；行不存在 1_003_008_000；**仅上传者本人**（userId 维度校验，跨用户 1_003_008_001——非 couple 维度，防误删；admin 兜底可删任何）；M15 起不级联清赞（残留行永不被查询，D-20h） |
| POST | /love/moment/like | `{"id","liked"}` **M15 新增**：点赞/取消单口幂等 toggle——true 走 INSERT IGNORE（uk 吸收并发重复 rows=0 幂等）、false 走**物理 DELETE**（uk 与逻辑删除冲突唯一解，D-20b）；瞬间不存在/跨 couple 统一 1_003_008_000（防探测，零新增错误码）；回包恒操作后真值 `{likeCount, liked}`（端上按 id 原位替换，D-20e）；**不发悄悄话消息、无 WS 信号**（D-20c）——对端靠 App 相册页可见期 5s 静默轮询对账感知（游标分页窗口删账合并，窗口外老条目删除不可见=接受，下拉刷新全量纠偏） |

### 5.3 admin-api（前缀 `/admin-api/love`，codegen 生成为主）

| 方法 | 路径 | 权限串 | 说明 / PageReqVO 筛选字段 |
| --- | --- | --- | --- |
| GET | /love/user/page | love:user:query | mobile / nickname / status |
| PUT | /love/user/update | love:user:update | 昵称/头像/停启用（停用即时生效路径见 §6.5） |
| DELETE | /love/user/delete | love:user:delete | 逻辑删除 + mobile 改写释放 uk；**级联：连带逻辑删除其 todo/mood/message、物理解绑 couple** |
| GET | /love/couple/page | love:couple:query | status / 双方昵称（join love_user；邀请码存于 love_user 不作筛选字段） |
| DELETE | /love/couple/unbind?id= | love:couple:update | 强制解绑（与 app unbind 同 service 路径） |
| GET | /love/todo/page | love:todo:query | coupleId / ownerId / 时间段 |
| DELETE | /love/todo/delete | love:todo:delete | 端上传播走全量覆盖（todo 拉取即覆盖，天然生效） |
| GET | /love/message/page | love:message:query | coupleId / 时间段 / kind / content 关键字（审计四件套） |
| DELETE | /love/message/delete | love:message:delete | 端上传播策略见 D-12 |
| GET | /love/mood-option-default/page | love:mood-option-default:query | label 模糊 / status 精确；排序 sort asc, id asc（效果序即管理序）——**M3.5/D-14 新增** |
| POST | /love/mood-option-default/create | love:mood-option-default:create | 新模板行；status 未传落库默认启用；之后的新绑定将播种本行 |
| PUT | /love/mood-option-default/update | love:mood-option-default:update | 按 id 全字段保存；**只影响之后的新绑定，不回灌已绑定 couple**（播种即复制） |
| DELETE | /love/mood-option-default/delete | love:mood-option-default:delete | 逻辑删除；该档不再参与之后的新绑定播种 |
| GET | /love/anniversary/page | love:anniversary:query | title 模糊 / createTime 时间段（仅两项冻结口径）——**M5/D-17 新增** |
| DELETE | /love/anniversary/delete | love:anniversary:delete | 逻辑删除；端上传播靠回前台/进页整表补拉（§7.4），管理删除后 App 下次拉取即消失——**M5/D-17 新增** |
| GET | /love/checkin/page | love:checkin:query | userId 精确 / checkinDate 时间段（仅两项冻结口径）——**M8 新增** |
| DELETE | /love/checkin/delete | love:checkin:delete | 逻辑删除；行不存在 1_003_007_000；**复活回退（M8 特有）**：逻辑删行仍占 uk_user_date 位（@TableLogic 读写均过滤 deleted），App 端当日重新打卡由 CheckinMapper 复活原行并挂当前 couple，App 侧无感——**M8 新增** |
| GET | /love/moment/page | love:moment:query | coupleId 精确 / userId 精确 / createTime 时间段（三项冻结口径）——**M9 新增** |
| DELETE | /love/moment/delete | love:moment:delete | 逻辑删除；行不存在 1_003_008_000；admin 不受 1_003_008_001 限制（兜底可删任何）；图片本体留档 infra_file 不级联删（D-12 残留策略哲学）——**M9 新增** |
| GET | /love/wish/page | love:wish:query | coupleId 精确 / userId 精确 / createTime 时间段（三项冻结口径）——**M14 新增** |
| DELETE | /love/wish/delete | love:wish:delete | 逻辑删除；行不存在 1_003_009_000；admin 不受 1_003_009_001 限制（兜底可删任何）；端上传播走全量覆盖——**M14 新增** |

（~~love/wish/* CRUD 整体二期~~——M14 已实施，见上。）VO 惯例：admin 三件套 PageReqVO/RespVO/SaveReqVO；app 侧 App 前缀放 `controller/app/<域>/vo/`；DO→VO 用 `BeanUtils.toBean`，不建 convert 包。

## 6. 服务端模块设计（yudao-module-love）

### 6.1 命名

`moduleName=love`，四处同时受益：URL 前缀、包名 `cn.iocoder.yudao.module.love`、权限串、表名前缀。取舍见 D-5。

### 6.2 包结构（六层，照 module-system 裁剪）

```text
yudao-module-love/
├── pom.xml
└── src/main/java/cn/iocoder/yudao/module/love/
    ├── package-info.java
    ├── controller/
    │   ├── admin/            # codegen 生成：user/ couple/ todo/ message/（各含 vo/）
    │   └── app/              # 手写：auth/ couple/ user/ todo/ mood/ message/（各含 vo/）
    ├── service/              # auth/AppAuthService(+Impl)；user/couple/todo/mood/message
    ├── dal/dataobject/ + dal/mysql/
    ├── enums/ErrorCodeConstants.java + MoodTypeEnum.java
    └── framework/security/config/SecurityConfiguration.java  # 预留；默认不需要
```

**M3 实施修正——admin service 命名**：love 的管理端 service 一律 `LoveAdmin{Domain}Service(+Impl)`（如 `LoveAdminUserService`），**不得叫 `Admin{Domain}Service`**——system 模块已有 `AdminUserService` 且显式注册 `@Service("adminUserService")`，同名类/同名 bean 混用会让 love 控制器的 `@Resource` 按字段名注入命中 system 的 bean，启动即 `BeanNotOfRequiredTypeException`（2026-10-16 实证后改名修复）。

**AppAuthService 安全要点**（评审补充，防线上爆破/轰炸）：

1. 密码登录/短信登录加 `@RateLimiter`（yudao-spring-boot-starter-protection，Bean 已在 yudao-server 装配）按 IP 限频；密码连续失败计数锁定可二期叠加；
2. send-sms-code 复用每手机号既有频控（1 分钟 1 条 / 每天 10 条，SmsCodeServiceImpl L57–64）并叠加 IP 级 @RateLimiter；首期 App 不引图形验证码（Kuikly 五端滑块成本高）；
3. 短信 **scene 固定 1（MEMBER_LOGIN）**：SmsCodeServiceImpl L44–45 对 scene 做硬断言（仅 1/2/3/4/21/22/23 且各绑固定 templateCode），模板 `user-sms-login` 与 DEBUG_DING_TALK 渠道已被 ruoyi-vue-pro.sql 种子化，联调零配置；若未来需独立 scene，必须给 system 的 SmsSceneEnum 加枚举项并显式声明为对 system 的受控修改；
4. 密码 BCrypt（PasswordEncoder 现成）；发/校验/刷新 token 全走 OAuth2TokenCommonApi（clientId=couple-app，userType=1）。

### 6.3 pom 接线点（逐文件，按操作顺序）

1. `love_baby_server/pom.xml`：`<modules>` 追加 `<module>yudao-module-love</module>`（只加一行，不动注释行）。
2. 新建 `yudao-module-love/pom.xml`：必需依赖 `yudao-spring-boot-starter-mybatis`、`yudao-spring-boot-starter-security`、`yudao-spring-boot-starter-protection`（@RateLimiter）；另加 `yudao-module-system` 依赖（参照 system→infra 的 api 依赖方式，yudao-module-system/pom.xml:23）以注入 SmsCodeApi/OAuth2TokenCommonApi 等。**注意：本仓不存在 `yudao-spring-boot-starter-test` artifact**（yudao-framework/pom.xml 的 14 个 modules 无此项、全仓无引用、无 BaseDbUnitTest 等基类）——单测骨架用 yudao-dependencies/pom.xml:414 管理的官方 `spring-boot-starter-test`，或直接经 yudao-module-system 传递获得，照搬旧清单必然 mvn 解析失败。
3. `yudao-server/pom.xml`：`<dependencies>` 追加 `yudao-module-love`（`${revision}`）。
4. **无需触碰**：YudaoServerApplication（`${base-package}.module` 通配扫描）、application.yaml（type-aliases 通配、@MapperScan 按 @Mapper 注解）、任何 yudao-framework starter。

### 6.4 错误码号段

独立占 `1_003_000_000` 段（infra=1_001、system=1_002）：待办 `1_003_001_xxx`、心情 `1_003_002_xxx`（**M3.5/D-14 起激活**：`1_003_002_000 心情选项不存在`、`1_003_002_001 无权操作该心情选项`（跨 couple IDOR）、`1_003_002_002 心情选项已达上限`（20）、`1_003_002_003 心情名称已存在`、`1_003_002_004 心情默认选项不存在`；record 的 uk 冲突仍走覆盖写不打错）、消息 `1_003_003_xxx`、情侣 `1_003_004_xxx`、账号/鉴权 `1_003_005_xxx`。样例：`1_003_004_000 邀请码无效`、`1_003_004_001 你已存在有效的情侣关系`、`1_003_004_002 不能与自己绑定`、`1_003_004_003 尚未绑定情侣关系`、`1_003_004_004 邀请码已过期`、`1_003_005_000 手机号已注册`、`1_003_005_001 账号或密码错误`、`1_003_005_002 账号已停用`、`1_003_005_003 手机号不存在`（重置密码对不存在手机号返回，AppAuthServiceImpl 已使用）、`1_003_001_000 待办不存在`、`1_003_001_001 无权操作该待办`、`1_003_003_000 消息不存在`。

### 6.5 免登与鉴权接入

- 免登：6 个 auth 端点全部 `@PermitAll`（框架自动收集 getPermitAllUrlsFromAnnotations，YudaoWebSecurityConfigurerAdapter.java:126,135-139），不改 yaml、不加 permit-all_urls。
- 业务端点默认 `anyRequest().authenticated()`（:148）零配置；logout 需登录（天然走 token 过滤器）。
- **停用账号收口**：love 各 app 业务 service 入口统一校验 love_user.status（App 请求上下文 userId 回查），停用返回 1_003_005_002；登录/注册环节同样拒绝停用号。已签发 token 在 TokenAuthenticationFilter 层仍有效（filter 只查 oauth2 token），因此端上对 1_003_005_002 的处理 = 清会话回 Gate（§5.1 对照表）。
- 写完后 swagger 逐端点实测 401 边界（含 logout 需 401 验证），防漏标或 pattern 过宽（M-7）。

### 6.6 菜单/权限/基础数据 SQL（love.sql 内）

- 菜单：目录「情侣业务」（type=1）→ 五管理菜单（type=2，component=love/user/index 等，component_name 唯一；M3.5 增「心情默认选项」love/moodOptionDefault/index）→ 按钮（type=3，permission 串），父子用 `SELECT @parentId := LAST_INSERT_ID()` 挂接（codegen sql.vm:23-49 同款）；**幂等**：脚本头部先 DELETE 同 permission 旧数据再插，或约定只执行一次。**M3.5 追加段注意**：目录 id 不能复用 M3 段的 LAST_INSERT_ID（重放语境下已失真），按 `SELECT @loveDirectoryId := id FROM system_menu WHERE type=1 AND name='情侣业务' AND deleted=b'0' LIMIT 1` 查名定位。
- **system_oauth2_client INSERT（couple-app 新行，不改 default 行）**：`client_id='couple-app'`、secret 自生成、`authorized_grant_types='["password","refresh_token","mobile"]'`（DB 存 JSON 数组格式，OAuth2ClientDO 用 Jackson3TypeHandler 解析）、`scopes='[]'`（love 侧 createAccessToken 不传 scopes，空 scopes 跳过校验）、access/refresh 有效期按 App 场景调大（建议 access 30 天 / refresh 90 天）；AppAuthService 发 token 调用组合 = `createAccessToken(clientId="couple-app", userId, userType=1)`。
- 字典：**首期零字典**（system_dict_type/data 不新增），值域用服务端枚举类（MoodTypeEnum），App 端值域以服务端枚举为唯一定义源，避免两端各写一份漂移。
- 角色分配：默认只靠超管自动拥有，不写普通管理员分配 SQL（定案于 love.sql 编写时，如需再加）。

### 6.7 codegen：走

love_* 表建好 → 「基础设施-代码生成」导入 → **逐表把 frontType 从默认 20 改为 41**（VUE3_VBEN5_ANTD_GENERAL，CodegenFrontTypeEnum.java:21-22）→ 预览/下载 → 后端按包路径粘入、前端粘入 love_baby_web → 执行菜单 SQL。**必须手写**：controller/app 全部接口（codegen 只产 admin 通道）+ 绑定校验/归属校验/可见性逻辑。生成后先 `mvn compile` 再粘贴（M-5）。

## 7. App 端适配（Kuikly）

### 7.1 网络封装 network/

选型：core 自带 NetworkModule + suspendCoroutine 包装（BridgeModule.kt L91-104 ssoRequest 先例）；不选 Ktor（需改 commonMain 构建脚本，违背只增不改）。

**HttpClient.kt 职责（单点收口，5 条）**：

1. **CommonResult 拆包**：success=true 取 data；`code==401` 走第 3 条刷新通道；其余 code!=0 一律按网络异常 toast 处理（不刷新不崩溃）；非 JSON 回包（包成 `{data:xxx}` 形态）识别处理；
2. 每请求手动塞 `Authorization: Bearer <token>` 头；
3. **单飞（single-flight）静默刷新**：全局共享同一个 in-flight 刷新 suspend 结果——并发多个请求同时收到 code==401 时只有一次 refresh-token 调用（复用 couple-app client），其余挂起等待；刷新成功后统一用新 token 重放原请求；**仅刷新本身失败才 SessionStore.clear()**；区分网络失败（保留会话、可重试）与 refresh token 无效（clear 回 Gate）。§7.4「登录成功拉全量」是并发场景，此约束为必选项而非优化；
4. **所有写请求强制注入 `Content-Type: application/json`**（GET 不带也无害）：反编译 core-render-android 2.29 AAR 的 KRNetworkModule.addBodyParamsIfNeed 证实——仅当 Content-Type 含 application/json 时才把 param 原文作为 JSON body 写出，否则按 k=v& 表单编码，而 yudao app-api 全是 @RequestBody，漏此项则登录等 POST 首日直接 400 或字段全空（M2 修正后 app-api 写端点统一 POST——§5.2；core 2.29 NetworkModule 亦仅支持 GET/POST 二值，PUT/DELETE 经此通道发不出去）；联调首日抓包确认 body 为 JSON 原文；
5. 模块获取一律可空 `getModule` 降级，不走会双抛异常的 acquireModule 路径（铁律⑦）。

DTO 序列化：core 自带 JSONObject 手写映射，不动 shared/build.gradle.kts；`data/model/` 放 LoginReq/LoginResp/TodoDTO/MoodDTO/ChatMsgDTO，**id 字段全部 String**（§5.1）。

### 7.2 SessionStore 与 token 持久化

- 复用 ThemeStore 三件套范式（AppTheme.kt L74/L86-108/L111-117）：全局 object + SharedPreferencesModule 同步 KV + pageData 双通道。
- **持久化完整 session JSON**（accessToken/refreshToken/userId/expiresTime/clientId 全量）——只存 accessToken 会导致 access 过期后冷启动必登出，静默刷新对最常见场景失效。
- **clear() 必须同步 `SharedPreferencesModule.setItem(SESSION_KEY, "")`**（可空 getModule 降级，ThemeStore.persist 同款容错）：只翻内存状态的话，冷启动 initFromPageData 又判已登录 → 首请求 401 → 再 clear → Gate 晚现，且退出登录后 session「复活」。
- **initFromPageData 判定**：非空校验之外加 expiresTime 判断——未过期直接放行；已过期但有 refreshToken 仍放行进首页（靠首请求 code==401 触发单飞刷新）；连 refreshToken 都没有才直接 Gate。
- Android 宿主 KuiklyRenderActivity.createPageData（L92-100）从同名 prefs 读**完整 session JSON** 注入 pageData；iOS/鸿蒙/H5 首帧只有 didInit 兜底，Gate 可能一帧闪烁（可接受，m-2）。
- 局限：明文存储，情侣 App 首期接受（m-3）。

### 7.3 登录注册与绑定 UI（features/auth/ + features/couple/）

- **Gate = 整棵子树条件渲染**：MainPageImpl 末尾段 `if (!isLoggedIn) { GatePanel() }`（后组合画在上层，MainPager.kt L103 层序规则；CLAUDE.md L22 认可「子级 if/else 换整个容器，子视图销毁无残留」。M2 接线后 Gate 之后还依次追加 BindingPanel 与两个绑定轮询 LaunchedEffect，「末尾」不再精确成立，但「后组合画在上层」的层序机制不变）。登录成功翻 SessionStore 状态整棵 Gate 卸载。**「常驻+切透明」铁律不适用于整棵 Gate 子树**（那是针对同节点选中态背景增删残留的），仅允许 Gate 内部选中态类小元素使用；若误用常驻 Gate + 切透明，会因 clickable 与 background 同节点（铁律⑤）在登录后继续拦截整屏点击导致 App 假死。不使用 Crossfade/AnimatedVisibility（铁律④）。
- **三态状态机**：`未登录 → Gate(登录/注册/重置密码)`；`已登录未绑定 → BindingPanel（features/couple/BindingPanel.kt。M2 实施形态：UNKNOWN「校准中」卡片——couple/get 校准、失败给重试入口；UNBOUND 单卡双模式——「我的邀请码」与「输码绑定」以模式芯片切换，无独立分页，亦无「绑定成功页」（绑定成功即 toast 并整树卸载引导层））`；`已绑定 → 主功能`（伴侣信息 + 解绑入口落在 MinePage 情侣空间卡 BoundPartnerCard）。BindingPanel 同样整树条件渲染，遵守铁律②③④⑤。couple/get 返回 1_003_004_003 即切回未绑定态——这也是解绑/被解绑后对方端的感知路径（配合 §7.4 拉取触发点）。
- **未绑定/未登录时各 Tab 降级**：主功能 Tab 内容区由 Gate/BindingGuide 全屏覆盖，无需改各页内部逻辑。
- 独立 @Page("login") 方案否（需改宿主默认启动页 + H5 入口，违背只增不改）；压栈模式（MinePage.kt L120-126 先例）保留给二级页按需使用。
- 登录结果回传走全局 SessionStore（RouterModule 无带参返回 API）。

### 7.4 三 Store 过渡策略：服务端优先 + 写穿透（失败即回滚）

- Stores 保持 `internal object`，页面直读零改动（TodoPage L60-99、HomePage L141-142、ChatPage L65）。**例外修正：TodoItem.id 与 ChatMsg.id 均由 Int 改 String**（两者现均为本地 nextId 自增 Int——AppState.kt L24-30/L132-138，Int 装不下 Long 且 NumberSerializer 回包类型不定；id 实为 MySQL 自增而非雪花（§4），按 String 解析对数字 JSON 仍兼容）——TodoStore 移除本地自增；**ChatStore 移除 nextId、改为记录服务端最新消息 id 作 lastId**（§5.2 message/list 增量拉取的前提）——改动收敛在 Store 方法签名，页面仅透传 id 不受影响，但「页面零改动」承诺就 id 部分不成立，此处显式推翻。
- **写语义统一：写穿透失败即回滚**（乐观更新 + 失败回滚 toast）。服务端不可达时 = 只读降级（离线可见旧数据），**不做离线写**（写操作直接失败提示），消除「离线写入 Store / 验收要求回滚」的语义矛盾；全量拉取覆盖写入因此不会抹掉离线改动（不存在离线改动）。
- **拉取触发点（逐域）**：todo——TodoPage 进入时全量拉；mood——MoodPage 进入 / 切月时按月拉 record/list，**M3.5/D-14 起同触发点另拉 option/list 字典**（首拉随 bindState 驱动 effect、再进入随切 Tab 补拉；**对端增删选项经切 Tab 补拉互通**，无推送下的最小可用路径同 couple 轮询哲学），记录心情 POST 失败回滚（写穿透语义同下）；message——ChatPage 进入 / 下拉刷新 / 本端发送成功后按 lastId 增量拉；couple/get——Gate 消失后拉一次（M2 已实现）+ 登录期间常驻自适应轮询（M2 实现：未绑定 10s / 已绑定 60s——等待对方输码的感知窗口及时性优先、绑定后常态低耗；先 delay 后拉、连续 3 次失败暂停、bindState 翻转时重启并切换间隔；「回前台生命周期钩子 + 退避恢复」M3 落地）；绑定成功直接采用 bind 回包置已绑定态，仅回包缺伴侣信息时补拉一次 couple/get，故无「BindingGuide 消失后再拉一次」。B 绑定成功后 A 端靠常驻轮询的 couple/get 感知（无推送方案下的最小可用路径）。
  - **M3 实施修正——聊天页停留轻轮询**：原 message 触发点不含「停留页面期间」，对方发消息须切 Tab 才可见；补 ChatPage 停留期间 5s 静默增量轮询（`ChatStore.POLL_INTERVAL_MS`，仅 BOUND 且停留本页时运行，selectedTab/foregroundTick 翻转即取消重启，连败 3 次暂停）。与 couple 轮询同范式：先 delay 后拉、失败静默。
  - **M3 验收修正——游标只由拉取回包推进（D-15，2026-10-17）**：send/sendWishes 成功仅以回包消息入列，**不推进 lastId**——原实现以本端新消息 id 顶游标，会越过对方并发已落库的低 id 消息形成区间盲区，增量 `id > lastId` 永不重拉、本会话内丢失（冷启动首屏覆盖拉才自愈）；修复后本端消息由下轮增量带回、appendDedupe 幂等去重，增量合并按 id 数值序归位（迟到低 id 消息插回正确位置，不尾插乱序）。
- 悄悄话：partnerReply 本地假回复（delay(900) 随机池）整体移除，替换为 message/list 增量拉取。
- 心情：MoodStore.allWishes 24 条文案字典留端上，服务端只同步 kind=2 快照消息与 love_mood_record；**M3.5/D-14 增补**：心情选项字典（couple 私有,服务端实体）增删同为写穿透——新增服务端先（回包带 id 才落态,防脏 key,同 TodoStore.add）、删除乐观移除+失败回滚；删除当前选中项时清选中态（选中态住 MoodStore 供管理浮层与页共用）。
- 同账号多设备并发写冲突：last-write 语义，首期不做字段级合并（D-6 定案）。

### 7.5 API 基地址多端配置

ApiConfig.kt：commonMain 默认常量 + pageData 覆盖（`params.optString("apiBaseUrl")`）。Android 模拟器 `http://10.0.2.2:48080`、真机局域网 IP，改 KuiklyRenderActivity.createPageData 一处。**H5 两通道（URL query 进 pageData / 同源相对路径反代）均为未验证假设**——RouterPage.kt L59 实为 AAR_MODE_TIP 调试字符串、static_server 为裸 koa-static 且 static/ 为空，仓内无先例；上 H5 前按 B-2 实测，不行就在 static_server 纯新增 /api 反代。iOS/鸿蒙按各宿主 pageData 同理。

### 7.6 CLAUDE.md 铁律逐条对齐

| # | 铁律 | 本设计落法 |
| --- | --- | --- |
| ① | willInit 里 acquireModule 必崩（L24） | 网络初始化与首个请求一律 didInit / 用户交互 / LaunchedEffect；initFromPageData 在 willInit 只读 pageData 参数，安全 |
| ② | MainPager 常驻堆叠勿改 | Gate/BindingGuide 不进 Tab 体系、不动 visitedTabs，整树条件渲染追加在 MainPageImpl 末尾 |
| ③ | background 常驻 + 透明切换（L22） | **仅适用于 Gate/面板内部的选中态类小元素**；Gate/BindingGuide 整树用条件渲染增删（见 7.3，勿把本条外推到整棵子树） |
| ④ | 勿 Crossfade / 动 visibility 堆叠 | 登录↔注册↔重置面板切换与 Gate 出现直接条件渲染；反馈用 toast |
| ⑤ | clickable 必须与 background 同节点 | Gate 拦截层、LoginPanel/RegisterPanel/BindingGuide 内按钮全部同节点 |
| ⑥ | Color/AppIconType 不可 JSON 序列化 | DTO 只传 hex 与 icon name，Repository 层双向映射 |
| ⑦ | acquireModule 失败双抛 | 模块获取一律可空 getModule 降级 |
| ⑧ | Brush 一律 lazy/remember 缓存（L25） | auth/couple UI 渐变一律 ThemeStore.current.brandBrush（已 lazy 缓存）或 remember 缓存，不新建 Brush 实例 |
| ⑨ | 安全区（L27） | LoginPanel 顶部 statusBarPaddingDp()；底部输入条/按钮避让一律 + navBarSafePaddingDp() |

### 7.7 MinePage 适配（补充，M2）

MinePage 目前为本地假数据；M2 起：用户资料区（昵称/头像）读 GET /love/user/get 回填，编辑走 POST /love/user/update（M2 实施修正 PUT → POST，见 §5.2）写穿透失败回滚（§7.4 语义）；主题切换卡与「组件列表」入口不受影响。后续「登录设备管理」类展示复用 §12.2 的 DeviceStore。M2 已知接受项：解绑确认/昵称编辑浮层挂 MinePage 根 Box，底栏（更晚组合）浮于其上且可点，浮层打开期间可切 Tab；M3 将浮层状态与渲染上移 MainPageImpl 根 Box（底栏之后组合）收口。

## 8. 管理后台适配（vben）

> **2026-10-16 变更：主力变体由 web-antd 改为 web-antdv-next（用户指定，覆盖下文 web-antd 表述与 D-7）**；本节路径把 `apps/web-antd/` 读作 `apps/web-antdv-next/` 即为现行事实，其余接线要点不变。

### 8.1 接线清单（仅主力变体 web-antdv-next）

1. api：`apps/web-antdv-next/src/api/love/{user,couple,todo,message,moodOptionDefault}/index.ts`，照 api/system/user 模式，URL 写 `/love/user/page`，**严禁带 /admin-api 前缀**（moodOptionDefault 为 M3.5/D-14 新增，管理绑定播种模板）。
2. views：`apps/web-antdv-next/src/views/love/<域>/`（index.vue + data.ts + modules/form.vue），照 views/system/user 结构，零新基建。
3. 路由：纯菜单管理页不写前端路由文件（accessMode:'backend'，菜单由 system_menu 下发，access.ts:16-26 glob 匹配 component）；本期无 hideInMenu 详情页，不加。
4. 菜单/权限：server 侧 love.sql（§6.6）；权限串与 @PreAuthorize 逐字符一致。
5. 字典：首期零字典（§6.6），data.ts 无 DICT_TYPE 依赖。

### 8.2 五变体策略

~~主力 web-antd 唯一承载新业务~~（**已改：主力 = web-antdv-next**，2026-10-16 用户指定；codegen frontType 匹配问题届时再议）；web-naive/tdesign 官方即无业务页不承载；web-ele/web-antd 暂缓同步（业务收敛在 api/love + views/love 独立目录、独立提交，上游冲突面最小）。原 web-antd 理由详见 D-7。

### 8.3 环境接线

- dev：vite proxy 已指 `http://localhost:48080/admin-api`（vite.config.ts:9-16），零改动。
- 生产：部署前必改 `.env.production` 的 `VITE_GLOB_API_URL`（现写死 127.0.0.1:48080）。
- `VITE_APP_TENANT_ENABLE=false` 是未提交本地改动，尽快提交固定（M-8）。
- API 加解密：前端仅对**显式声明 isEncrypt 的单个请求**加密、仅当响应带 `X-Api-Encrypt: true` 头才解密（request.ts:93-105/114-121），且服务端 `yudao.api-encrypt.enable` 默认 false（application.yaml:281）——新 love 管理页不主动声明 isEncrypt 就不会触发加解密，「联调报解密失败」基本不会发生；仅当未来开启服务端加密开关时回看该配置。
- 联调首日验证 vben requestClient 对 1_003 段错误码的默认展示效果（§5.1 对照表）。

## 9. 关键决策记录

**D-1 用户体系：自建 love_user（不拉官方 member）**
事实修正（评审核实）：本仓 member 缺失为两处注释——根 pom:18 与 yudao-server/pom.xml:36-40 的注释依赖声明；且 ruoyi-vue-pro.sql 中 **member_* 表为 0 张**（不是缺约 20 张，而是全无，补表只能从官方仓库整体搬）。本仓为 SB 4.1.1/Java 25，官方 member 版本线编译适配成本不可预估；(b) 自建全部依赖本仓现成能力，完全增量。附注：love_user 即本项目 C 端用户表（与 D-2 呼应）。

**D-2 userType 复用 MEMBER(1)**：/app-api 前缀硬映射 MEMBER（WebFrameworkUtils.java:111-116）；TokenAuthenticationFilter 对 URL 推导 userType 与 token userType 强校验（L71-83）；buildUserInfo 未知 userType 抛异常（OAuth2TokenServiceImpl.java:235）。love_user 独立建表 + userId/userType=1 进 oauth2 token。

**D-3 登录双轨（密码 + 短信），短信 scene 固定 1**：SmsCodeService 全套现成，短信为主路径、密码兜底；**scene=1（MEMBER_LOGIN）**，模板 user-sms-login 与 DEBUG_DING_TALK 渠道已被种子 SQL 覆盖，联调零配置；未来独立 scene 需改 system SmsSceneEnum（受控修改，显式声明）。找回密码：提供 reset-password 端点（复用 SmsCodeApi scene=1，§5.2），不留给二期。

**D-4 开发三捷径与上线强制清单（含各端影响面）**：local 沿用 mock-enable:true（token `test<userId>` 免登录直模拟）、captcha.enable:false、sms-code 固定 9999。**位置修正**：begin-code/end-code: 9999 在 **application.yaml:338-339**（所有 profile 共享），application-local.yaml 只有 captcha.enable:false（:250）与 mock-enable:true（:252）——上线改法为「改 application.yaml 的 yudao.sms-code.begin-code/end-code 为随机区间」。上线强制清单：mock-enable→false；captcha.enable→true（**仅覆盖 system 后台登录**，App 端防护由 @RateLimiter + IP 频控承担，见 §6.2，App 不依赖此项）；sms-code 改随机区间；补真实短信渠道；VITE_GLOB_API_URL 改实际域名。

**D-5 模块命名 love**：moduleName 进 URL/包名/权限串/表前缀四处，短名四处干净。

**D-6 数据同步：服务端优先 + 写穿透失败即回滚**（定案）：离线只读降级，不做离线写、不做双向合并、**不做字段级冲突策略**（last-write 语义，定案于 M3 开发前）；多设备并存语义见 D-10。

**D-7 主力 web 变体：web-antd 单端**（理由见 8.2）。

**D-8 邀请码绑定：love_user 内嵌字段 + 双重并发兜底**
存储：invite_code/invite_expire_time 落 love_user（独立 invite 表首期不需要）；过期惰性校验。bind 事务步骤：① 按码查发起方（idx_invite_code）→ 无效 1_003_004_000；② 惰性判过期 → 1_003_004_004；③ 校验非自绑 → 1_003_004_002；④ `SELECT ... FOR UPDATE` 按 userId 升序锁双方 love_user 行（防死锁），锁内以 selectByUserIdForUpdate（FOR UPDATE 当前读）校验双方均无有效 couple → 否则 1_003_004_001；⑤ 插入 love_couple。**数据库兜底**：uk_user_a/uk_user_b 唯一键，service 捕获 DuplicateKeyException 转 1_003_004_001——唯一键仅终审「同邀请方多受邀方（撞 uk_user_a）/同一受邀方多邀请方（撞 uk_user_b）」两类竞态（两设备同绑为同对同向重复插入、亦撞 uk）；**A/B 互绑产出 (user_a=B,user_b=A) 与 (user_a=A,user_b=B) 两行、撞不到任何 uk**，由 ④ 锁内 FOR UPDATE 当前读兜住（普通快照读快照建立早于加锁、看不到并发已提交的 couple 行——M2 评审修正：原「互绑由唯一键终审」表述失真）。

**D-9 登录落位 love 模块 + token 复用 system**：接口在 controller/app，发/校验/刷新走 OAuth2TokenCommonApi，love 绝不自造 token。

**D-10 多设备并存（新增）**：允许同账号多设备并存，各自独立 token（yudao 默认：每次登录新发 token，旧 token 有效至过期），**不做互踢**（§11 双设备验收项据此成立）。token 有效性由校验时过期判断保证；system_oauth2_access_token 存量行清理可选用 infra 定时任务，首期不配置（不影响安全）。

**D-11 解绑与数据留存（新增）**：app unbind 双方任一可发起、admin 强制解绑同 service 路径；couple 行**物理删除**（本表不启用逻辑删除，见 §4，同时天然解决逻辑删除后重绑的唯一键冲突）；todo/mood/message **数据保留**（couple_id 保留为历史值，message 另有 receiver_id 快照保证审计归对）；重绑产生新 couple_id，旧数据不迁移、消息流从零开始（显式声明）。对方端感知：couple/get 返回 1_003_004_003 → 端上切回 BindingGuide。

**D-12 消息传播与撤回（新增，M12 修订 2026-10-17）**：~~首期不做用户侧撤回~~ **M12 起推翻前半裁决：用户侧撤回已实施**（发送方 2 分钟内，recalled 墓碑列标记不删行、content 原文留档审计，§5.2 /love/message/recall；用户侧删除仍不做）；admin 删除已拉取消息在端上**残留**（lastId 只增不减，显式接受该限制并写入验收声明）；后续如需消除，引入数据版本号触发全量重拉，二期评估。

**D-13 mood 无后台管理页（二次评审补录）**：心情是端上月历功能、首期无审核场景，admin-api 不设 mood 组——§5.3/§6.2/§8.1 无 mood 属预期而非遗漏；将来要内容审核时随 §12.3 一并规划（届时补 admin mood 组与后台页）。**M3.5/D-14 补注**：新增的 mood-option-default 管理页是**绑定播种模板**而非 mood 记录/情侣字典页——mood 记录仍无后台页、couple 私有选项亦无后台审计页（隐私边界），D-13 本意不变。

**D-14 心情选项私有字典 + 记录快照（M3.5 新增）**：心情选项升级为 **couple 私有字典**（love_mood_option），绑定时从 B 端可配模板（love_mood_option_default）**复制**播种——播种即复制，admin 改模板**不回灌**已绑定 couple（隐私 + 归属边界，显式声明）；双方可增删（上限 20、couple 内查重；**无改名/改色/重排接口**，需求只有加/删）。记录落库**快照 label/emoji/color 三列**（§12.5.2 先快照后实体，love_message 心愿快照同哲学）——删除选项后历史月历不变灰，字典只服务选择器；**mood_type 列废弃置 NULL 不复用存 optionId**（存量 1-5 撞真实 id 会跨 couple 渲染错乱，宁可留死列）；App 侧 1-5 旧映射降级为存量兜底常量（回填窗口期保险）。空模板/空字典不报错——客户端空态自愈（「点 + 添加心情」）；绑定播种在 bind 事务内（失败整体回滚，D-8 语义，不 catch 吞错）。App 侧无 moodType 契约（预生产净切换，§5.2 实施修正）。

**D-15 增量游标推进不变量（M3 验收新增，2026-10-17）**：message/list 的 lastId 游标**只由拉取回包推进**——send/sendWishes 成功只入列不推游标。根因（真机验收发现：两端同时发消息，一侧概率性收不到对方消息）：本端发送回包的 id 若直接顶游标，会越过对方并发落库的低 id 消息形成区间盲区，`id > lastId` 增量扫描永不返回该消息，仅冷启动首屏覆盖拉自愈。代价可控：本端已发消息由下轮增量再拉回一次、按 id 幂等去重；增量合并走 id 数值序归位防乱序。todo/mood 为全量覆盖拉无游标，不受影响。

**D-16 WS 触发器只传信号不传数据；自定义跨端 Module 必进各平台 actual 注册（M7 回填，2026-10-17）**：WebSocket 长连接只发「有新消息」纯信号打点（ChatPushStore，驱动 ChatStore.loadIncrement），数据完整性恒由 lastId 游标增量拉保证——未桥接端（iOS/ohos/H5）与断线由既有 5s 轮询兜底，渐进降级。实施发现：Kuikly 自定义 Module 必须走 expect/actual 的 platformExternalModules 注册进各端宿主（androidApp/iosApp/ohosApp），只在 commonMain 声明则运行时 getModule 恒 null——新跨端 Module 落地清单据此固化：声明放 base/、注册进各端 actual。

**D-17 纪念日落位裁决（M5，2026-10-17）**：独立表 love_anniversary（§12.4 预留落位兑现）；**倒数算术全端上**——commonMain 禁 java.time，服务端只存原始 anniv_date，端上以 Howard Hinnant civil 算术 + GMT+8 日序号计算：每年重复 2/29 钳当年月末（不折算 3/1）、已过 +1 年；一次性可负（已过）；排序/倒数均为 (list, todayIndex) 纯函数，首页第 4 统计列「纪念日(天)」与独立页（@Page anniversaryList）共消费，跨天不重排（todayIndex remember），回前台/进页重拉校准。**无到点推送**（§7.4 哲学：回前台/进页拉取收敛）、**无 update**（删了重加）、emoji 仅端上预设字典选一、admin 仅 page/delete 无新增编辑；owner_id/emoji 为照 todo 域惯例的增量（§12.4 原规划三字段之外）；我的页「纪念日提醒」死菜单接线为第二入口。

**D-18 正在输入与伴侣在线状态：全程无持久化（M13 新增，2026-10-17）**：typing 与 presence **零落库、零 lastSeen**（「最近在线时间」显式不做，需持久化+心跳超范围）；在线判定 = **WS 内存会话表直查**（`WebSocketSessionManager.getSessionList(MEMBER, partnerId)` 非空即在线，sender-type=local 单实例——**多实例部署前须先落 redis sender 扩展，部署红线**）；websocket 关闭（sender/manager 弱注 null）或未桥接端（iOS/鸿蒙/H5 不连 WS）恒离线 = 渐进降级，与 D-16 同哲学。typing 走 **HTTP POST 上报 + WS 中继下发**（App WebSocketModule 无 send 通道、POST-only 写惯例、冒烟纯 HTTP 可驱动 + 真 WS 客户端断言下行）；**无「停止输入」信号**——端上 4s 过期唯一收口（>3s 上报节流，连续输入不闪断）；服务端不做 typing 节流（端上 3s 承担，不落库无存储放大，留二期）。回包形态双先例：online 对象 VO（禁改标量，M11 冻结）、typing 标量 true（read 先例）。

**D-19 恋爱清单落位裁决（M14 新增，2026-10-17）**：love_wish 实体表与 message 组 kind=2 心愿卡消息**并存不替代**——kind=2 是快照式聊天消息、love_wish 是可勾选完成的共享清单实体，wish_key 不升级外键（§12.4 落位兑现、§12.5.2 快照哲学），stats.wishCount 绑死 kind=2 不动。完成消息 = 每次「未完成→完成」跃迁**恰发一条**：CAS `UPDATE ... WHERE done = b'0'` 单闸保证（并发双勾恰一方 rows=1），**无 celebrated 类防重发列**（对抗验证结论：任何补法都自相矛盾——CAS 不含该列则并发仍重复发，补上则 undo→re-done 永远无法重新完成）；undo→re-done 属新完成事件、重发为预期；CAS 提交后 sendMessage 失败（极低概率）自愈路径 = undo→re-done，不做补偿任务。消息 = kind=1 文本 `我们一起完成了「title」🎉`（字符串拼接禁 String.format 防标题 % 歧义；标题 64 封顶总长 ≤75 无需截断），senderId=勾选者，可按 M12 规则撤回；不进心愿时间线（selectListWishes 滤 kind=2）、不计 wishCount。done 端点**无 @Transactional**（RR 事务内 rows=0 路径重读命中旧快照回错态；各语句独立 autocommit 保证重读必见已提交真值）。删除 = 仅创建者可删（1_003_009_001 用户维度，防误删），不存在/跨 couple 统一 1_003_009_000 防探测（recall 口径），admin 兜底不受限。排序：服务端 create_time+id 升序全量返回（无游标，todo 先例），「进行中在前 / 已完成沉底」分层排序端上做；App 页内自持不进 AppState（D-19g，零 reset 接线），解绑后旧行随 couple_id 失联（D-11）；App 勾选成功一律用回包原位替换本地行（乐观翻转缺 doneBy/doneTime 必须回填服务端真值，D-19h）。**勾选权限修订（2026-10-17 真机反馈）**：原「双方均可勾选」收窄为**「仅对方能勾」**——发布者不能点亮自己许的心愿（自己许、TA 来完成；卡与勾选框对发布者均不响应，未完成行副文案「等TA点亮」作解释）；**端上收口**（WishRow `canToggle = wish.userId != myId`），服务端不设限保持灵活与 admin 兜底；行升级为 CardBg 卡片；交互同日再修订为**详情弹框确认制**——行卡片/勾选框点击弹出详情弹框（创建人·创建时间 / 完成人·完成时间，友好日+时刻 GMT+8），[已完成]/[取消完成] 按钮仅对方的行显示（发布者只读详情 +「等TA点亮」），操作经弹框确认防误点亮（完成会自动发庆祝消息）；行副文案同步显「创建于 …」/「…完成于 …」。

**D-20 相册二期：多图 + 点赞裁决（M15 新增，2026-10-17）**：**多图存储 = love_moment 加 `image_urls` TEXT（JSON 数组字符串 ≤9 元素）单列，不建图片子表**（单行原子写、列表查询零改动）；`image_url` 列**保留 = 首图冗余**（admin LoveMomentRespVO/web 管理页只读 imageUrl → 管理后台零改动，多图瞬间 admin 显示首图）；create 归一：imageUrls 空 → [imageUrl]（老版 App 兼容出口），image_url 恒 = imageUrls[0]；存量单图回填 JSON_ARRAY(image_url) 幂等（information_schema 判列 + PREPARE 可重放，M11/M12 一次性迁移段先例升级）；读侧 parseImageUrls 对 NULL/空/损坏 JSON 一律兜底 [imageUrl]（与存量单图同一出口）。⚠ hutool BeanUtils 遇 DO imageUrls(String)→VO imageUrls(List) 走 CollectionConverter 按逗号切分产垃圾列表——**组装时无条件覆写，不依赖同名拷贝**。**点赞存储 = love_moment_like 新表 uk_moment_user(moment_id,user_id)**：like = INSERT IGNORE 吸收并发重复（幂等），unlike = **物理 DELETE**（uk 与逻辑删除冲突唯一解——墓碑行永久占 uk 位，显式 SQL 不经 @TableLogic），本表 deleted 恒 0；couple_id 冗余落库作归属第二道闸；删瞬间不级联清赞（D-20h，残留行永不被查询）。**点赞权限 = 双方均可赞任意瞬间（含自己）**，回包恒操作后服务端真值 {likeCount, liked}（端上按 id 原位替换，D-19h 同款）。**同步机制 = 不发悄悄话消息、无新 WS 信号**（用户裁决：点赞是高频轻互动不轰炸悄悄话，庆祝消息仍只属心愿完成类仪式事件 D-19b 先例）——App 相册页**可见期 5s 静默轮询对账**（WishListPage fetchSilent 同款；游标分页合并策略：恒拉首页按窗口删账——本地 id > 首页最小 id 且不在回包者判删，data class 相等跳过原位替换，hasMore 顺带纠偏；**窗口外更老条目的删除不可见 = 接受**，下拉刷新全量纠偏；preview 浮层开着不拦轮询——preview 是独立状态副本，合并时同步真值）。**防探测 = 零新增错误码**：like 对不存在/跨 couple 统一 1_003_008_000，超 9 张走 400 参数校验。**原生多选 = pickAndUpload 扩 maxCount 参数（缺省 1，三老调用方头像/聊天图/单图零改动）**：Android 走 ACTION_PICK + EXTRA_ALLOW_MULTIPLE（minSdk 23 覆盖，零新依赖零 manifest；PickVisualMedia 否决——需 androidx.activity≥1.7 + ComponentActivity 基类）+ clipData 迭代 + data.data 回退（OEM 忽略 extra 只回单张）；**串行压缩上传 fail-fast「第N张上传失败」**不做部分成功；回调契约：maxCount=1 → data 恒单 URL 字符串（老契约字节不变），>1 → data 为 JSON 数组字符串；multipart 文件名前缀 avatar_ → img_。

## 10. 风险与坑（分级）

### blocker

- **B-1 local 捷径照搬进生产**：mock token 'test1' 冒充任意用户 + 验证码 9999 可猜 + love 侧万能验证码 888888（`AppAuthServiceImpl.MASTER_SMS_CODE`，测试兜底）= 认证整体旁路。→ ~~D-4 上线清单强制执行（含注释万能码 if）~~ **万能码 2026-10-19 已随代码移除（此子项关闭）；验证码 9999 已由 application-prod.yaml 覆盖为 100000-999999 真随机；mock token 若仍存在需另行核对**；App 侧防爆破不依赖 captcha 开关，由 §6.2 限流措施承担。
- **B-2 多端网络与基地址未验证**：本地仅有 Android AAR 证据；iOS pod（OpenKuiklyIOSRender ~>2.7.0）与 core 2.29.0 版本错位未验证；ohos 侧 @kuikly-open/render 2.7.0 与 core 2.29.0-2.0.21-ohos 同类版本差（oh-package.json5、build.ohos.gradle.kts L49-51），NetworkModule handler 与 statusCode 支持均未验证；H5 的 apiBaseUrl 两通道亦未验证（§7.5）。→ M4 前逐端实测：H5 浏览器跑通 httpRequest、ohos/iOS 确认 NetworkModule 可用性；不可用则评估替代（HttpClient 封装层已隔离切换点；H5 可走 static_server /api 反代）。

### major

- **M-1 多租户误伤**：TenantProperties ENABLE_DEFAULT=true 全靠 application.yaml 显式 false 压制；若配置漂移开启，无 tenant_id 的 love_* 会被 TenantLineInnerInterceptor 强拼 tenant_id → **Unknown column SQL 异常、接口 500**（不是静默查空：computeIgnoreTable 对未标注 DO 返回 false）。→ **love 各 DO 类加 TYPE 级 @TenantIgnore**（DictDataDO 同款，computeIgnoreTable 据此忽略），零成本永久免疫；love.sql 头部注释同步说明。
- **M-2 codegen frontType 忘改**：默认 20 ≠ vben5，逐表改 41。
- **M-3 菜单 SQL 非幂等**：LAST_INSERT_ID 重复执行插重复菜单 → 头部先 DELETE 再插。
- **M-4 权限串不一致 → 403**：逐字符核对。
- **M-5 SNAPSHOT 工具链**：codegen 产物先 mvn compile 再粘贴。
- **M-6 buildUserInfo 对 MEMBER 返回空 Map**：昵称/头像一律回查 love_user，不改 system。
- **M-7 @PermitAll 范围错误**：漏标 → 401；pattern 过宽 → 业务端点裸奔 → swagger 逐端点实测（含 logout）。
- **M-8 VITE_APP_TENANT_ENABLE=false 未提交**：尽快提交固定。

### minor

- **m-1 Kuikly 渲染层历史坑**：clickable/background 同节点、勿 Crossfade、整树条件渲染 vs 常驻+透明的适用边界——已按 7.6 逐条落设计，联调按 CLAUDE.md L19-27 复查。
- **m-2 非 Android 端首帧 Gate 闪烁**：didInit 兜底，可接受。
- **m-3 token 明文存 SharedPreferences**：首期接受，安全存储桥五端成本高不做。
- **m-4 iOS/ohos 渲染 SDK 版本错位**：并入 B-2 清单，上端前验证（定案时点：对应端联调期前）。
- **m-6 跨表统计**：用已配置的 mybatis-plus-join（application.yaml:72-77）或应用层组装，不引 mapper XML。
- **m-7 五变体维护成本**：以「api 层收敛逻辑」约束缓解。
- **m-9 love_user.uk_mobile 与逻辑删除冲突**：定案于 M1 love_user 开发前——admin 删除用户时将 mobile 改写为 `concat(mobile,'#',id)` 释放唯一键（§4），注册侧应用层查重 + uk 兜底不变。

（原 m-5/m-8 升级并入 M-5/B-2 与 §7.1/§5.1 实锤结论；Bearer 前缀项已结案，不再是风险。）

## 11. 验收清单

### App 端

- [ ] (M1) 注册 → 登录 → 杀进程重启保持登录 → 调 logout → 回 Gate，且重启后不复活
- [ ] (M1) willInit 无任何 acquireModule/网络调用；didInit 恢复正常；含网络失败路径全程无崩溃
- [ ] (M1) code==401 通道：并发多请求仅触发一次刷新（单飞），刷新成功后原请求重放成功；网络失败保留会话；刷新失败 clear 回 Gate；success=false 通道按网络异常 toast
- [ ] (M1/M4) 写请求抓包：body 为 JSON 原文而非 k=v& 表单
- [ ] (M2) A 生成邀请码 → B 绑定成功；第三者重复绑定 / 无效码 / 过期码 / 自绑被 1_003_004 段拒绝；任一方解绑后双方端回未绑定态且重绑成功
- [ ] (M3) 双设备同账号（D-10 并存）：待办增改、悄悄话收发互通；kind=2 心愿消息文案与颜色/图标按快照还原正确
- [ ] (M3) 双设备当日心情互通：一方记录后另一方切月历可见；同日重复记录按覆盖语义生效
- [ ] (M3) 拉取触发点生效：进 TodoPage 拉待办、进/刷新 ChatPage 增量拉消息、回前台拉 couple 状态
- [ ] (M3) 断网：旧数据可见不崩；写操作失败 toast 回滚无脏数据
- [ ] (M1) Gate/BindingGuide 整树条件渲染：登录后无触摸拦截残留（整屏可点）；底栏不掉帧、Tab 选中态无色块残留；无 Crossfade
- [ ] (M4) H5/ohos/iOS NetworkModule 实测结论记录在案（B-2）

### 后端

- [ ] (M1) 根 pom / yudao-server pom 接线后 mvn compile 打包成功；system/infra 回归无感；pom 无 yudao-spring-boot-starter-test 引用
- [ ] (M1/M2) ruoyi-vue-pro.sql 零改动；love.sql 建表（含 uk/idx）+ couple-app client 干净库一次执行通过（菜单段随 M3 codegen 落地后执行，见下）
- [ ] (M3) love.sql 菜单段执行：「情侣业务」目录与四管理页菜单/按钮权限在后台可见可用
- [ ] (M1) swagger 逐端点 401 边界：仅 6 个 auth 免登端点匿名可达，logout 需登录；app-api 需 MEMBER token；admin-api 需 ADMIN token + 权限串
- [ ] (M1) userType 交叉校验：MEMBER token 打 admin-api 403，ADMIN token 打 app-api 403
- [ ] (M1) 登录/发码端点限流生效（超频返回限流错误）；scene=1 短信在 local 模式联调通过
- [ ] (M3) 归属校验：跨 couple 按 id 写 todo/message 被 1_003_001_001/1_003_003_000 拒绝（IDOR 用例）
- [ ] (M3) 停用账号：登录被拒；旧 token 请求业务接口返回 1_003_005_002
- [ ] (M3) codegen 产物编译通过且每表 frontType=41；错误码落在 1_003 对应子段

### 管理后台

- [ ] (M3) web-antd 登录出现「情侣业务」目录与用户/情侣/待办/消息四页，分页/查询/删除可用（message 页含 coupleId/时间段/kind/关键字筛选）
- [ ] (M3) **数据闭环**：App 注册用户/发悄悄话/创建待办后，admin 对应分页可见且字段一致；admin 删除用户按级联策略生效（todo/mood/message 连带删、couple 解绑）、删除消息按 D-12 声明的残留策略执行
- [ ] (M3) 权限串核对：去权限账号按钮消失，直连接口 403
- [ ] (M3) vben 对 1_003 段错误提示展示效果确认
- [ ] (M3) web-ele / web-antdv-next / web-naive / web-tdesign 零改动且回归编译通过

### (M5) 纪念日（D-17，2026-10-17 实施）

- [ ] (M5) App 真机走查：首页第 4 统计列 "--" → 独立页 FAB 新增（每年/一次性/2-29 类钳当年月末）→ 列表与首页卡同步变数字 → 删行生效/失败回滚 → 解绑后回 "--"
- [ ] (M5) 后台走查：web-antdv-next 纪念日页分页 / title 模糊 / 删除可用，权限串 love:anniversary:query|delete 核对（去权限账号按钮消失、直连接口 403）
- [ ] (M5) 服务端冒烟回归：create 回包全量（annivDate "yyyy-MM-dd" 字符串）/ 2026-02-30 → 1_003_006_002 / repeatType=3 → 400 / 跨 couple IDOR 1_003_006_001 / 未绑定 1_003_004_003 / admin page+delete（M5 实施时 21/21 已过，验收时复跑）

### (M8) 每日打卡（2026-10-17 实施）

- [ ] (M8) App 真机走查：首页「今日打卡」条 → 打卡页月历点亮我的点 → 对方打卡后点+环同格、条与按钮文案翻「今天已共同打卡 ✓」→ 共同连续 +1 → 翻月（上月记录保留、返回当月）→ 月历格子不可点（无补打）；我的页「每日打卡」菜单入口可达
- [ ] (M8) 后台走查：web-antdv-next 打卡页分页 / userId 精确 / 打卡日期段搜索可用，权限串 love:checkin:query|delete 核对（去权限账号按钮消失、直连接口 403）；删除记录后 App 端当日可重新打卡（复活回退）
- [ ] (M8) 服务端冒烟回归：重复 check 幂等同 id / 未绑定三端点 1_003_004_003 / couple 隔离（第二对互不可见）/ 跨日 streak（DB 直插昨日双向验证「今天缺一方从昨天起算」）/ 非法 month 400 / admin page（userId+日期段）+ delete + 复活同 id + 删不存在 1_003_007_000（M8 实施时 26/26 已过，验收时复跑）

### (M9) 相册/瞬间（2026-10-17 实施）

- [ ] (M9) App 真机走查：我的页「我们的相册」菜单 → 相册页双列墙（空态引导）→ FAB 发布浮层选图（选图即上传 1080px，上传中蒙层）→ 配文（可不填 ≤64）→ 发布头插 → 点卡片开大图预览（Fit 全屏 + 配文/我·TA/时间）→ 删除自己的瞬间（TA 的无删除钮）→ 下拉刷新 → 加载更多（30 条后出现）
- [ ] (M9) 后台走查：web-antdv-next 瞬间页分页 / coupleId·userId 精确 / 时间段搜索可用 / 缩略图点击放大，权限串 love:moment:query|delete 核对（去权限账号按钮消失、直连接口 403）；admin 删除后 App 下拉刷新即消失
- [ ] (M9) 服务端冒烟回归：create 回包全量（图+文 / 图+空 text）/ 双方 list 共享 / 倒序 / beforeId 游标翻页 / text 65 字 400 / 未绑定 1_003_004_003 / couple2 隔离 / B 删 A 的 1_003_008_001 / 删自己 code=0 / 删不存在 1_003_008_000 / admin page（userId+时间段）+ delete（M9 实施时 27/27 已过，验收时复跑）

### (M10) 聊天发图片（2026-10-17 实施）

- [ ] (M10) App 真机走查：输入行图片按钮 → 系统选图 → 上传中预览条占位（「上传中…」蒙层）→ 缩略图 + 「发出后不可撤回」提示 + ✕ 取消 / 重选覆盖 / 发送 → 图片消息入列（180dp 方图气泡）并滚底 → 点气泡开全屏大图（黑底 Fit）→ 点击关闭；对方在线 WS 实时收到 / 离线 5s 轮询兜底；文本 / 贴纸 / 心愿气泡全回归；键盘开着点图片按钮先收键盘；预览条开着切 Tab 再回仍在；未桥接端 toast「当前端暂不支持选择照片」；断网发送失败 toast 且预览图保留可重发
- [ ] (M10) 后台走查：web-antdv-next 悄悄话消息页 kind 下拉含「图片消息」；kind=3 行内容列显 40px 缩略图、点击放大；kind=1/2 行仍显文本；kind=3 筛选生效；content 关键字搜 URL 片段命中
- [ ] (M10) 服务端冒烟回归：send-image 回包全量（kind=3 / content=URL / auditStatus=1）/ 双方 list 可见（恒升序混序）/ 落库 kind=3 / imageUrl 空串·缺字段·纯空白 400 / 513 字 400（@Length max=512）/ 未绑定 1_003_004_003 / couple 隔离 / admin content LIKE 命中 URL + kind=3 筛选 / 心愿时间线无 kind=3（M10 实施时 21/21 已过，验收时复跑）

### (M11) 聊天未读红点（2026-10-17 实施）

- [ ] (M11) App 真机走查：对方发消息 → 底栏聊天 Tab 红点数字（WS 桥接端即时 / 未桥接端切 Tab 或回前台后出现，>99 显 99+）→ 带红点切聊天 Tab 红点消失 → 停留聊天页对方连发红点恒 0 → 首页「去聊天」入口红点与底栏一致 → 后台收消息回前台红点出现 → 断网 markRead 失败恢复后 ≤5s 自愈 → 登出/解绑红点消失、重绑正常
- [ ] (M11) 服务端冒烟回归：unread 回包对象形态 `{"unread":n}` / read 后清零 / 落库 chat_read_cursor / GREATEST 防回退（旧 id 不回退）/ 自己发不计未读 / 未绑定 unread=0 + read 1_003_004_003 / kind=2/3 全计 / admin 逻辑删计数减 / couple 隔离 / read 缺字段·0·负数 400 + 未登录 401 / 解绑重绑新 couple 计数不误报（M11 实施时 33/33 已过，验收时复跑）
- [ ] (M11) 上线顺序：love.sql M11 段（love_user ADD COLUMN chat_read_cursor）必须先于新代码（新代码 DO 映射依赖本列）；ADD COLUMN 非幂等，重放报 1060 属预期

### (M12) 聊天消息撤回（2026-10-17 实施）

- [ ] (M12) App 真机走查：长按自己 2 分钟内文本/心愿/图片气泡 → 确认浮层 → 撤回后本端即时变灰条「你撤回了一条消息」；对方在线（WS 桥接端）即时灰条「对方撤回了一条消息」；对方离线 → 切 Tab 进聊天页 resync 后灰条出现；长按对方消息/超窗气泡长按无反应；浮层开着 MainPager 横滑不切 Tab（onOverlayChange 拦）；断网撤回失败 toast + 浮层关；墓碑打断分组（相邻消息重出头像/时间行）；登出/解绑浮层清场无残留
- [ ] (M12) 服务端冒烟回归：recall → data=true + 落库 recalled=1 / list 双方含墓碑行且 content 原文仍在 / kind=2/3 可撤 / DB 造旧超窗 1_003_003_001 / 已撤回幂等 / 未绑定 1_003_004_003 / 不存在·跨 couple·非本人统一 1_003_003_000（防探测）/ 参数 400 + 未登录 401 / 撤回未读消息对方 unread 差值 -1 / admin 逻辑删独立（删未撤回→recall 拒，删已撤回→list 不含）/ wish-list 排除墓碑（M12 实施时 39/39 已过，验收时复跑）
- [ ] (M12) 上线顺序：love.sql M12 段（love_message ADD COLUMN recalled）必须先于新代码（DO 映射依赖本列）；ADD COLUMN 非幂等，重放报 1060 属预期

### (M13) 正在输入与伴侣在线状态（2026-10-17 实施）

- [ ] (M13) App 真机走查：A/B 双端开聊天——A 输入 B 头部即时「正在输入…」→ 停手 4s 回「在线」；连续输入 10s+ 不闪断（3s 上报 < 4s 过期）；A 停留不输入 B「在线」（≤5s）；A 切走/杀 App B ≤5s 回默认副标题；B 离页重进先默认后 ≤5s 恢复；A 用未桥接端 B 恒默认（降级）；A 发消息自身副标题不变（不回环）；B 离页时 A 输入无红点副作用；解绑/登出无残留；断网静默、恢复 ≤5s 自愈
- [ ] (M13) 服务端冒烟回归：online 基线 false（对象形态含 partnerOnline 键）/ B 连 WS → true（双连接仍 true，多端并存）/ 全断开 → false / typing 中继（B 的 WS 收 love-typing 且 content.typing=="1"）/ 自发不回环 / couple 隔离（C 上报 D 收、B 无）/ 对方离线静默 no-op / 未绑定 typing+online 均 1_003_004_003 / 未登录 401 + 坏 token 握手拒绝 / 垃圾体容忍（M13 实施时 15/15 已过，验收时复跑）
- [ ] (M13) 上线顺序：**无 DDL，部署即生效**；多实例部署前须先落 D-18 redis sender 扩展（部署红线）；冒烟需 pip install websocket-client（真 WS 客户端断言下行，Temp/m13_presence_typing_smoke.py）

### (M14) 恋爱清单（2026-10-17 实施）

- [ ] (M14) App 真机走查：我的页「恋爱清单」进页（Flag 图标菜单行）；A/B 双方添加/互见（id 升序，CardBg 卡片行）；**勾选权限=仅对方能勾（2026-10-17 修订）：点行卡片/勾选框弹详情框（创建/完成时间），A 自己的行无操作按钮只读 +「等TA点亮」，B 在弹框点 [已完成] 确认点亮 / 已完成行 [取消完成]**；勾选完成对方聊天即时收「我们一起完成了…」+ 红点（WS 在线）/ 切 Tab 增量拉自愈（离线）；重复勾选消息不重发；取消勾选行回进行中、再勾消息重发（取消由勾选者操作）；已完成沉底 doneTime 倒序 + 行副文案（创建于/完成于 友好日+时刻）；仅创建者见删除按钮（确认弹层）；**对方增删改/勾选 ≤5s 静默轮询感知（可见期自动，2026-10-17 补）+ 下拉刷新即时**；解绑/登出无残留；键盘弹起添加条避让正常
- [ ] (M14) 服务端冒烟回归：create 落库原样（中文引号+emoji）/ couple 互见 id 升序 / done 庆祝消息恰一条（kind=1 content 精确 sender=勾选者）+ unread+1 / 重复 done 幂等（真值不变消息不重发）/ undo 三态清空无消息 / re-done 重发 / 删他人 1_003_009_001 / 跨 couple+不存在 1_003_009_000 / 未绑定 1_003_004_003 / 无 token 401 / title 64-65-空-纯空白 / admin page 三筛选+delete 兜底+不存在 000 / done 回包对象形态含 doneTime（M14 实施时 33/33 已过，验收时复跑，Temp/m14_wish_smoke.py）
- [ ] (M14) 上线顺序：love.sql M14 段（love_wish 建表 + system_menu 三行，IF NOT EXISTS + 先子后父 DELETE 幂等可重放，Temp/m14_apply_sql.py）**先于新代码**；48080 主实例需 IDEA 重启载入新 jar

### (M15) 相册二期：多图 + 点赞（2026-10-17 实施）

- [ ] (M15) App 真机走查：发布浮层多选 ≤9 张（串行上传蒙层 + fail-fast 第N张提示；单张 X 删除 + 条尾追加块；OEM 回退单张也能发）；墙卡三态（1 图 140dp 全宽不变 / 2-9 图三列迷你九宫格 + 底行纯展示红心计数）；预览浮层横滑多图 + 页码指示 + 信息卡点赞钮（双端点赞 ≤5s 内同步）；**轮询对账三场景：对方删首屏内瞬间 ≤5s 消失 / 发新瞬间 ≤5s 头插 / 点赞 ≤5s 计数变**（窗口外老条目删除靠下拉刷新）；存量为单图的旧瞬间显示正常；双列变高行观感目测（不佳则配文 maxLines=2 收敛）；admin 后台多图瞬间显示首图
- [ ] (M15) 服务端冒烟回归：多图 create 落库 image_urls JSON + image_url 首图冗余 / 仅 imageUrl 归一 [imageUrl] / 回填幂等 / list 组装 imageUrls+likeCount+liked / like×2 幂等 DB 恰 1 行 / unlike×2 物理删 DB 0 行 / 双方视角 liked 分叉 / uk 1062 / 跨 couple+不存在 000 同文案 / 删后再赞 000 + 赞行残留 / 10 图 400 / 未绑定 1_003_004_003 / 无 token 401 / admin page 多图显首图（M15 实施时 21/21 已过，验收时复跑，Temp/m15_moment_smoke.py）
- [ ] (M15) 上线顺序：love.sql M15 段（image_urls 加列 information_schema+PREPARE 可重放 + 存量回填 + love_moment_like 建表，无菜单段，Temp/m15_ddl.py）**先于新代码**；48080 主实例需 IDEA 重启载入 like 端点

### 上线前（M4，D-4 强制项）

- [ ] mock-enable=false；captcha.enable=true（仅后台登录生效）；application.yaml 的 sms-code begin-code/end-code 改随机区间；补真实短信渠道；VITE_GLOB_API_URL 改实际域名
- [x] **万能验证码通道关闭**——~~注释 validateSmsCode 中命中 MASTER_SMS_CODE（888888）的 if~~ **2026-10-19 超越式完成：整个功能已随代码移除（字段+短路+三份 yaml 配置段），无配置即无风险**（§10 B-1）
- [ ] love 各 DO @TenantIgnore 在位；租户配置漂移演练：误开后 love 接口不 500

## 12. 扩展性预留（后续能力不返工，二次评审补）

> 定位：本章**不新增首期实现**，只把市面恋爱 App 迟早要做的能力（IP 归属地、设备信息、登录设备管理、内容审核、纪念日/相册/打卡/推送等）的落位提前钉死——后续加功能照表入座，不乱建表、不乱挂接口。所有结论均经仓内代码核实（file:line 见括号内标注）。

### 12.1 IP 归属地与登录审计（服务端，复用为主，首期即接入两列）

- **IP→归属地**：复用 `yudao-spring-boot-starter-biz-ip`——`IPUtils.getAreaId(ip)`（ip2region.xdb 离线库整包进内存，无外部 HTTP 依赖，IPUtils.java:19-85）+ `AreaUtils.format(areaId)` 输出「省 市」文案。love 模块 pom 直接依赖该 starter（system 同款引入，yudao-module-system/pom.xml:38），同进程零成本。调用收口到自建 `LoveIpService`，将来换全量 xdb（补运营商字段——精简版 xdb 无运营商，IPUtils.java:13）只换实现不动调用方。
- **真实 IP 采集**：service 层 `ServletUtils.getClientIP()/getUserAgent()`（yudao-common，Hutool 内置代理头列表），照抄 AdminAuthServiceImpl.createLoginLog 写法（L149-166）。⚠️ 生产 Nginx 必须直传 `X-Real-IP` / `X-Forwarded-For`，否则记到的是代理 IP。
- **登录日志**：复用 `system_login_log` 而非自建——表已按 user_type 区分（LoginLogDO.java:20-70），D-2 已定 love_user=MEMBER(1)；AppAuthService 注入 `LoginLogApi.createLoginLog`（LoginLogCreateReqDTO 的 userIp @NotEmpty 记得传），log_type 用 LOGIN_MOBILE=103 / LOGIN_SMS=104。已知副作用：后台登录日志页不按 userType 过滤（LoginLogMapper.java:17-28），后台看 love 用户日志需自带过滤。**首期不建 love_login_log**；将来设备维度字段（device_id/os/push_token）要进日志时再建并迁移。
- **已纳入首期**：love_user 的 `last_login_ip` / `last_login_time` 两列（§4），登录成功更新——对齐 admin 侧 updateUserLogin 惯例（AdminAuthServiceImpl.java:163-164）。

### 12.2 设备信息采集与登录设备管理（二期，两端缝已定死）

**服务端**：

- 表 `love_device`（二期建）：id / user_id / device_id（客户端生成 UUID，uk_user_device）/ device_name / os / os_version / brand / model / app_version / push_token（预留推送）/ last_login_ip / last_login_ip_area（冗余归属地）/ last_login_time / status。登录成功 service 层 upsert。
- 「踢下线」= `OAuth2TokenCommonApi.removeAccessToken` + 登录日志 log_type=LOGOUT_DELETE(202)。**不要**把 device_id 塞进 access token——那要动 system_oauth2_access_token 列与 Redis 结构，成本不成比例。
- 风控挂 protection starter 现有体系（yudao-server 已全局引入，零配置）：登录/发码 `@RateLimiter`（已有）；设备维度将来仿 ClientIpRateLimiterKeyResolver 写 DeviceRateLimiterKeyResolver（从 header 取 device_id）；防篡改用现成 `@ApiSignature`。

**App 端**（跨端缝已核实，commonMain 单源集）：

- `base/DeviceModule : Module()`（MODULE_NAME 对齐现有命名 "HRDeviceModule"）+ `DeviceStore` object（ThemeStore 三件套范式：全局 object + 可空 getModule 降级 + pageData 双通道）。**不用 expect/actual**——加平台源集要动四个 target 的构建脚本，违背仓内既定架构。
- 只桥接 core 没有的三项：deviceId / model / networkType → 一个异步 `deviceInfo(callback)` + suspendCoroutine 包装（BridgeModule.kt L91-97 先例）。**appVersion / osVersion / platform 不进模块**——core 2.29 PagerData 已自带且渲染层自动填 pageData，宿主零改动。
- 各端落点：Android `androidApp/.../module/KRDeviceModule.kt` + KuiklyRenderActivity.registerExternalModule 加一行（deviceId 用首启 UUID 存 prefs，随 createPageData 回灌）；ohos `KRDeviceModule.ets` + KuiklyViewDelegate 注册 map 加一行；iOS `KuiklyExpand/Modules/HRDeviceModule.h/.m`（类名=模块名自动路由；⚠️ 需先在 2.7.0 pod 上验证路由行为，并入 B-2 实测清单）；H5 两级降级——DeviceStore 空值优雅降级 + URL query 缝（mergeUrlParamsToPageParams）把 localStorage UUID 当 deviceId 传入；UA 解析不放 commonMain，需要时由服务端解析 UA 请求头（本就更可信）。
- 请求头注入：`X-Device-Id` 等**唯一收口在 network/HttpClient.kt**（届时加进 §7.1 职责清单成为第 6 条），严禁业务调用点散注入；DeviceStore 首读在 didInit 或首个请求前懒加载（铁律①：willInit 里不能拿模块）。
- 已知现状：iOS 壳 HRBridgeModule 仅实现 2 个方法（copyToPasteboard/log），其余跨端方法在 iOS 全部 no-op——DeviceModule 落地时同场补齐 iOS 缺口或显式接受 iOS 降级。

### 12.3 内容审核与安全（预留列已就位）

- love_message 已带 `audit_status`（§4，首期恒写 1 兜底）。事前审核：将来在 love 模块自建 SensitiveWordService（词库或微信 msgSecCheck），落库前调用——**仓库内现无任何敏感词/审核设施**（全仓 0 命中），属全新建设。
- 事后审计兜底：`infra_api_access_log` 已自动记录 requestParams（含消息 body，≤8000 字）/ userIp / userAgent（ApiAccessLogDO.java:20-100），消息取证零成本可用。

### 12.4 市面恋爱 App 常见功能落位表（先占名，防乱建）

| 功能 | 表（二期建） | 归属与要点 |
| --- | --- | --- |
| 纪念日 / 恋爱天数 | love_anniversary | **已实现（M5/D-17，2026-10-17）**：couple_id + title/anniv_date/repeat_type + owner_id/emoji 增量（照 todo 域惯例）；首页统计卡第 4 列消费 + 独立页管理；无推送无更新（D-17） |
| 相册 / 瞬间 | love_moment（+love_moment_like） | **已实现（M9，2026-10-17；M15 多图+点赞二期同日落）**：couple_id + user_id + image_url（M15 起=首图冗余）+ **image_urls（M15，JSON 数组 ≤9）** + text（≤64 可空配文）；**点赞 love_moment_like uk(moment_id,user_id)，unlike 物理删（D-20b），点赞不发消息无 WS（D-20c）**；媒资复用 infra 文件服务，不新建存储（端上 pickAndUpload maxEdge=1080，M15 多选 maxCount ≤9）；游标分页 id 倒序；仅上传者本人可删（1_003_008_001 用户维度，admin 兜底）；无 uk（同人可发多条）、无 update（删了重发）；视频/评论留后续 |
| 打卡 | love_checkin | **已实现（M8，2026-10-17）**：couple_id + user_id + checkin_date，uk_user_date 防重复（照 mood record 先例）；App 月历双角色点（我实心/TA 空心）+ 共同连续 streak（服务端聚合）；admin delete 逻辑删行复活回退，App 当日重打回原行 |
| 心愿库 | love_wish | **已实现（M14，2026-10-17）**：couple_id + user_id（创建人）+ title（≤64）+ done/done_by/done_time；勾选完成自动发 kind=1 庆祝消息（CAS 单闸恰一条，D-19b）；**wish_key 不升级外键**（快照哲学不变：kind=2 快照与实体表并存不替代，D-19a）；仅创建者可删（1_003_009_001，admin 兜底）；无 uk、无 update；无分页全量拉（todo 先例） |
| 位置共享 | love_location_snapshot | 强隐私：默认关、couple 双方显式开关、上报频率受限 |
| 推送 | （外部通道） | 厂商通道/个推；凭 love_device.push_token 下发，服务端不建推送表 |

接线规则不变：`love_` 前缀、couple_id 归属、BaseDO 公共字段 + @TenantIgnore、admin/app 双通道、codegen 优先。另：buildUserInfo 对 MEMBER 返回空 Map 的分支（OAuth2TokenServiceImpl.java:223-236）将来可按 userId 查 love_user 填充昵称——改动集中、向后兼容，列为可选优化，首期仍按 M-6 回查。

### 12.5 演进原则（防乱核心，所有后续迭代遵守）

1. **接口只加不改**：ReqVO 只加可选字段（旧端零影响），RespVO 只加字段；错误码只增不删不改义。
2. **先快照后实体**：展示类数据先冗余快照列（心愿消息先例），确有管理需求再抽实体表。
3. **原生能力一律 Module 桥接**：跨端接口声明进 `base/`，各端实现在各宿主壳；业务层不 import 平台细节。
4. **love.sql 只追加**：历史段不改不删；表演进走「末尾 ALTER 追加段」，脚本保持可重放。
5. **敏感数据最小化**：位置/设备指纹默认不采、显式开关；服务端把客户端上报一律当 advisory 数据，风控只认服务端可验证信息。
6. **复用优先**：IP=biz-ip、登录日志=system_login_log、限流/幂等/签名=protection、请求审计=infra_api_access_log、文件=infra 文件服务——动 system/framework 前先确认是否已有现成件。
