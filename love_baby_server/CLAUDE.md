# CLAUDE.md — love_baby_server(yudao 单体后端)

## 项目概述

基于芋道 yudao `2026.09-jdk25-SNAPSHOT` 的 Spring Boot 单体(端口 48080),当前启用 **system + infra + yudao-module-love** 三个业务模块,其余官方模块在根 `pom.xml` 注释待按需开启。

- **Java 25 + Spring Boot `4.1.0`**(根 pom 实际值;外界资料写 4.1.1 勿信)+ MyBatis Plus + Redis(必需:cache type=REDIS ttl 1h)
- 版本统一走 `revision` 属性 + flatten-maven-plugin;lombok `1.18.48`;mapstruct `1.7.0.Beta2`(仅官方 system/infra 层使用,**love 模块不用 MapStruct**)
- MQ(RocketMQ/Kafka/RabbitMQ)配置在 yaml 里但 **dormant**——`yudao-server/pom.xml` 未依赖 starter-mq;WS sender-type=local(单实例)
- maven 走华为云 + 阿里云镜像;**全仓无任何 `<profiles>`**,打包只有 fat jar 一条路

## 技术要点(写代码前必读)

1. **双通道自动前缀**:Controller 的 `@RequestMapping` 不含 `/admin-api`、`/app-api`——框架按包名 `controller.admin` / `controller.app` 自动加。管理后台端点进 `controller/admin` + `@PreAuthorize("@ss.hasPermission('love:xxx:yyy')")`;App 端点进 `controller/app`。**权限串必须落 `system_menu`(type=3 按钮)才能过**——`@ss.hasPermission` 严格查表,缺行恒 false(403);菜单/按钮 SQL 随任务并入 `sql/mysql/` 拆分套件。
2. **love 模块分层(实际)**:`controller/admin|app(VO+Controller) → service(接口+Impl) → dal(dataobject+mysql)+ enums + framework/validation`。**没有 convert/ 目录、没有 job/ mq/、没有 src/test**——VO 转换统一 `BeanUtils.toBean`(MapStruct 仅 system/infra 官方层);根总览画的六层树是照 module-system 写的,love 模块以本条为准。
3. **命名不对称(现状如此,照抄)**:admin 域服务 = `service/admin/LoveAdminXxxService(+Impl)`;app 域服务 = `service/<域>/XxxService`(无前缀,auth 例外叫 `AppAuthService`)。Mapper/DO 按域分包(`dal/mysql/<域>/`、`dal/dataobject/<域>/`),两端共用同一 Mapper。
4. **新业务域套路**:自下而上 DO → Mapper → Service → Controller;错误码进 `enums/ErrorCodeConstants` 开新段(`1_003_0XX_XXX`,现用 001-016,**段次序恒定只增不删不改义**);admin 按物理 id 删/改时判空抛**专属 NOT_EXISTS 码**(app 侧无按 id 写端点用不到它——`MOOD_RECORD_NOT_EXISTS`/`CHECKIN_NOT_EXISTS` 等七处先例)。
5. **逻辑删除 vs 物理删除**:20 个 DO 里 19 个 `extends BaseDO`(deleted + @TableLogic);**唯一例外 `LoveCoupleDO` 不继承**——解绑=物理 DELETE(uk_user_a/uk_user_b 天然支持解绑重绑),且其 creator/createTime 等审计字段由 **service 显式赋值**(自动填充器只处理 BaseDO)。
6. **`@TenantIgnore` 双保险**:全部 love DO 都挂(口径:「租户配置漂移被误开时跳过本表 tenant_id 过滤」;`yudao.tenant.enable: false`,biz-tenant 依赖经 module-system 传递引入)。
7. **uk 冲突覆盖写**:一人一天一条(uk_user_date)并发场景走「应用层 select 命中 → update 覆盖写,uk 终审并发,**不打错误码**」(mood record / checkin 先例)。
8. **IDOR/防探测**:跨 couple 访问统一按「不存在」拒绝(不暴露他 couple 资源的存在性,MESSAGE_NOT_EXISTS 口径);删除权限冻结决策:moment/wish **仅创建者本人可删**(用户维度防误删),admin 兜底可删任何(不校验该码)。
9. **快照哲学**:端上渲染只吃记录时点快照列(mood 的 moodLabel/emoji/color 三列、心愿消息 kind=2),删字典历史不变灰、不回查实体表;冗余列恒同步(`love_moment.image_url` 恒 = imageUrls JSON 首元素,读侧 `parseImageUrls` 对损坏 JSON 兜底)。
10. **DO 字段名与列名 snake_case 严格驼峰对应**(moodDate→mood_date),拿不准就数一遍——历史教训写进 DO 注释。
11. **WebSocket 只传信号不传数据**(D-16):消息落库后向对方在线连接推纯打点,数据完整性由端上 lastId 增量拉保证;端点 `/infra.ws`。**多实例部署前须先落 redis sender 扩展**(部署红线)。
12. **复用 system 侧能力,绝不自造**:OAuth2 令牌走 OAuth2TokenCommonApi、短信验证码走 SmsCodeApi(经 yudao-module-system 依赖引入);登录/发码限流 `@RateLimiter`(starter-protection)。密码校验用 love 自有 `framework/validation/Password`(6-16 位含字母数字)。
13. **mood_type 列废弃**(M3.5/D-14):新记录恒写 NULL,值域改 optionId + 快照三列;`MoodTypeEnum` 零引用保留。存量行端上按旧 1-5 映射兜底渲染。
14. **上传体量**:multipart max-file-size 16MB / max-request-size 32MB(聊天视频上限在此,客户端压缩链对齐)。
15. **错误码纪律**:次序恒定,只增不删不改义;新码先 grep `ErrorCodeConstants` 现有段取下一空槽。

## 常用操作

- **编译验证(Windows,唯一正确姿势)**:`cd love_baby_server && export JAVA_HOME='D:\Application\jetbrains\IntelliJ IDEA\jbr' && "D:\Application\DevDependencies\apache-maven-3.9.16\bin\mvn.cmd" -q -pl yudao-module-love compile > log 2>&1; echo EXIT=$?` —— ⚠️ **退出码只认 `echo EXIT=$?` 直取,严禁经过管道判断**(管道吞退出码会假绿;grep 无匹配也会假红)。机器默认 JAVA_HOME 不是编译可用值,必须显式 export IDEA jbr。
- **运行**:IDEA 跑 `yudao-server`(YudaoServerApplication),profile `local`。两个环境配置差异大:
  - `local`(默认,端口 **48080**):MySQL 127.0.0.1:3306/`yudao` + Redis 127.0.0.1 db0;验证码关闭、mock security、**短信码固定 9999**;Druid 监控 `/druid/*` 开放(无账密);swagger/knife4j 开启。
  - `dev`(端口 **48081**):MySQL/Redis 指向远程 47.120.73.48(库 `love_baby`、Redis **db1**),真实 6 位短信码。
- **打包**:`mvn package` → `yudao-server/target/yudao-server.jar`(spring-boot repackage fat jar)。
- **SQL 交付(2026-10-26 终态)**:增量**并入** `sql/mysql/` 拆分套件——菜单/权限类进拆分3、业务数据进拆分4、表结构进拆分1/2;不产独立日期文件。⚠️ 并入 ≠ 已应用:本地运行库要手动执行对应语句。

## 目录结构与后续规划

```text
yudao-module-love/src/main/java/cn/iocoder/yudao/module/love/
├── controller/
│   ├── admin/          # 15 Controller / 14 包(mood 包双控制器:OptionDefault + Record),
│   │                   #   路径 /love/{anniversary,checkin,config,couple,daily-question,emoji,inspiration,
│   │                   #   message,moment,mood-option-default,mood-record,todo,user,wish,wish-template}
│   │                   #   —— 无 report/share(admin 不看月报,共享域纯 App)
│   └── app/            # 17 Controller / 17 包(/love/{...同上 + auth,report,share};mood 单控制器)
├── service/
│   ├── admin/          # 15 组 LoveAdminXxxService(+Impl):page+delete 两件套为主,
│   │                   #   moment/checkin/user/couple/mood-option-default/inspiration/emoji/
│   │                   #   wish-template/daily-question/config 有更全 CRUD(全列 VO 口径)
│   └── <域>/           # 18 组 App 服务(接口+Impl;mood 包双服务 MoodService+MoodOptionService)
├── dal/
│   ├── dataobject/     # 23 DO / 15 包(moment 2:moment+like;mood 3:record+option+optionDefault;
│   │                   #   share 4:setting+battery+location 快照+place;dailyquestion 3:question+day+answer)
│   └── mysql/          # 23 Mapper(分组与 DO 一一对应;BaseMapperX + LambdaQueryWrapperX 惯例)
├── enums/              # ErrorCodeConstants(1_003 段 16 个子段)+ MoodTypeEnum(废弃保留)
└── framework/validation/ # Password 自定义校验注解(注册/重置密码 VO 专用)

sql/mysql/              # 拆分1 yudao 结构(59 表)/ 拆分2 love 结构(23 表,M32 起 20+3)/ 拆分3 yudao 基础数据
                        #   (7320 条,含 system_menu 全表)/ 拆分4 love 业务数据(145 条,纯字典/配置/种子,
                        #   2026-10-26 净化:用户真实数据 11 表 545 行已出库,git 历史 ae31849d4c 可找回);
                        #   其余方言目录为 yudao 官方原版,未做 love 拆分
```

**后续规划**:yudao-server/CLAUDE.md 本册即;模块级新功能照「技术要点 4」套路;三端对接设计与 D-决策全录见根 [INTEGRATION-DESIGN.md](../INTEGRATION-DESIGN.md)。
