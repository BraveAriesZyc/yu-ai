-- ----------------------------------------------------------------------------
-- love.sql —— 情侣业务（yudao-module-love）增量 SQL
--
-- 范围约定（见 INTEGRATION-DESIGN.md §6.6 / M1）：
-- 1. 本脚本对官方 ruoyi-vue-pro.sql 零改动，全部为增量内容；
-- 2. M1 段 = 建表 + OAuth2 客户端；菜单段（「情侣业务」目录 + user/couple/todo/message 四管理页及按钮权限）
--    随 M3 codegen 产物一起追加到本文件末尾，勿提前添加；
-- 3. 本文件只追加、不修改历史段（表演进走末尾 ALTER 追加段），保持可重放；
-- 4. 多租户双保险：application.yaml 已显式 yudao.tenant.enable=false，
--    同时 love 各 DO（LoveUserDO 等）在类上标注 @TenantIgnore（DictDataDO 同款），
--    即使租户配置漂移被误开，love_* 表也不会被强拼 tenant_id 导致 SQL 报错；
-- 5. love_user.uk_mobile 唯一键与逻辑删除冲突的处理约定：管理后台删除用户时，
--    将 mobile 改写为 concat(mobile, '#', id) 释放唯一键（yudao 惯例手法）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- Table structure for love_user（C 端用户表，M1）
-- ----------------------------
DROP TABLE IF EXISTS `love_user`;
CREATE TABLE `love_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  `mobile` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号码',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '密码（BCrypt 加密），纯短信注册用户为空',
  `nickname` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称，默认「用户+手机号后四位」',
  `avatar` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户头像',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '帐号状态（0启用 1停用）',
  `invite_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '我的邀请码',
  `invite_expire_time` datetime NULL DEFAULT NULL COMMENT '邀请码过期时间（惰性校验，无定时任务）',
  `last_login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '最后登录IP',
  `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_mobile`(`mobile` ASC) USING BTREE,
  INDEX `idx_invite_code`(`invite_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'C 端用户表（情侣业务）';

-- ----------------------------
-- Records of system_oauth2_client（追加 couple-app 客户端，M1）
-- 说明：不改 default 行；先 DELETE 再 INSERT 保证脚本可重放；
--       access 令牌 30 天 / refresh 令牌 90 天（App 场景调大）；
--       authorized_grant_types 为 JSON 数组格式（OAuth2ClientDO 用 Jackson3TypeHandler 解析）。
-- ----------------------------
DELETE FROM `system_oauth2_client` WHERE `client_id` = 'couple-app';
INSERT INTO `system_oauth2_client` (`client_id`, `secret`, `name`, `logo`, `description`, `status`, `access_token_validity_seconds`, `refresh_token_validity_seconds`, `redirect_uris`, `authorized_grant_types`, `scopes`, `auto_approve_scopes`, `authorities`, `resource_ids`, `additional_information`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('couple-app', '2d474ddb6fb20336eeca9625a58f2a17', '情侣 App', '', '情侣互动 App 客户端', 0, 2592000, 7776000, '[]', '[\"password\",\"refresh_token\",\"mobile\"]', '[]', '[]', '[]', '[]', '{}', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- M2 段：情侣关系（INTEGRATION-DESIGN.md §4 love_couple / §3 M2）
-- 本段追加于 M1 段之后；M1 段一字不动；本文件只追加、不修改历史段，保持可重放。
-- ============================================================================

-- ----------------------------
-- Table structure for love_couple（情侣关系表，M2）
-- 说明（INTEGRATION-DESIGN.md §4 / D-11）：
-- 1. 解绑采用【物理删除】：本表不设 deleted 字段、LoveCoupleDO 不继承 BaseDO（无 @TableLogic），
--    deleteById 即物理 DELETE，关系行不留史；
-- 2. 因此 uk_user_a / uk_user_b 两个唯一键在解绑后天然释放，重绑无冲突，并发兜底语义干净
--    （service 捕获 DuplicateKeyException 转 1_003_004_001，应用层校验兜不住的竞态由唯一键终审）；
-- 3. 审计归对不依赖本表留存：todo/mood/message 数据在解绑后保留（couple_id 留历史值），
--    message 另有 receiver_id 快照；重绑产生新 couple_id，旧数据不迁移；
-- 4. 多租户双保险：application.yaml 已显式关闭租户，LoveCoupleDO 类上另加 @TenantIgnore（LoveUserDO 同款）。
-- ----------------------------
DROP TABLE IF EXISTS `love_couple`;
CREATE TABLE `love_couple`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系编号',
  `user_a_id` bigint NOT NULL COMMENT '发起方用户编号（邀请码属主）',
  `user_b_id` bigint NOT NULL COMMENT '受邀方用户编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '关系状态（0正常）',
  `bind_time` datetime NOT NULL COMMENT '绑定时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_a`(`user_a_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_user_b`(`user_b_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '情侣关系表（情侣业务）';

-- ============================================================================
-- M3 段：核心业务三表 + 菜单/按钮权限（INTEGRATION-DESIGN.md §4 / §5.3 / §6.6 / §3 M3）
-- 本段追加于 M2 段之后；M1/M2 段一字不动；本文件只追加、不修改历史段，保持可重放。
-- 三张业务表均继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），
-- 与 love_couple 的物理删除路线相反；解绑/重绑后数据保留，couple_id 留历史值（D-11）。
-- ============================================================================

-- ----------------------------
-- Table structure for love_todo（待办表，M3）
-- 说明（INTEGRATION-DESIGN.md §4）：
-- 1. 归属 = couple 共享 + owner 仅记录创建人，列表对 couple 双方可见；
--    按 id 写操作在 service 层强制 couple 归属校验，不属于当前 couple 返回 1_003_001_001（防 IDOR）；
-- 2. idx_couple：列表按 couple_id 全量过滤（端上覆盖式拉取）；
-- 3. admin 删除端上传播走全量覆盖（todo 拉取即覆盖，天然生效，§5.3）。
-- ----------------------------
DROP TABLE IF EXISTS `love_todo`;
CREATE TABLE `love_todo`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '待办编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（couple 共享，列表按此过滤）',
  `owner_id` bigint NOT NULL COMMENT '创建人用户编号（仅记录归属，双方均可见可操作）',
  `title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '待办标题',
  `done` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否完成',
  `tag` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '标签文本（家庭/恋爱/健康/生活/随手记，客户端按文本渲染；M3 实施修正加 DEFAULT：与 create/update VO 可空字段口径对齐，缺省空串）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '标签颜色（hex 色值字符串，如 #FF6B81；M3 实施修正加 DEFAULT：与 create/update VO 可空字段口径对齐，缺省空串）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple`(`couple_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '待办表（情侣业务）';

-- ----------------------------
-- Table structure for love_mood_record（每日心情记录表，M3）
-- 说明（INTEGRATION-DESIGN.md §4）：
-- 1. uk_user_date(user_id, mood_date)：一人一天一条，重复记录走覆盖写（uk 冲突兜底终审，
--    应用层 selectByUserIdAndDate 命中后 update，不打错误码，心情段 1_003_002_xxx 仅预留）；
-- 2. idx_couple_month(couple_id, mood_date)：月历按 couple 维度查月区间；
-- 3. mood_type 值域 = 服务端枚举 MoodTypeEnum（1开心/2平静/3难过/4生气/5疲惫），首期零字典（§6.6）；
-- 4. 无 admin 管理页（D-13），仅 app-api 读写。
-- ----------------------------
DROP TABLE IF EXISTS `love_mood_record`;
CREATE TABLE `love_mood_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '心情记录编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号',
  `user_id` bigint NOT NULL COMMENT '记录用户编号',
  `mood_date` date NOT NULL COMMENT '心情日期（一天一条，uk_user_date 组成列）',
  `mood_type` tinyint NOT NULL COMMENT '心情类型（1开心 2平静 3难过 4生气 5疲惫，值域 MoodTypeEnum）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_month`(`couple_id` ASC, `mood_date` ASC) USING BTREE,
  UNIQUE INDEX `uk_user_date`(`user_id` ASC, `mood_date` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日心情记录表（情侣业务）';

-- ----------------------------
-- Table structure for love_message（悄悄话/心愿消息表，M3）
-- 说明（INTEGRATION-DESIGN.md §4 / D-12）：
-- 1. idx_couple_time(couple_id, id)：消息增量拉取按「couple_id 过滤 + id 游标」顺序扫；
-- 2. receiver_id 为对方快照：couple 物理解绑后审计仍可归对（D-11）；
-- 3. kind=2 心愿消息为【快照式】：content/wish_key/color/icon 四字段整体入列，
--    服务端不校验 wish 归属（无 wish 实体，文案字典留端上），两设备还原靠快照字段（§5.2）；
-- 4. audit_status 首期恒写 1（通过）兜底，事前审核二期接入（§12.3）；
-- 5. 首期无用户侧撤回/删除（D-12），admin 删除已拉取消息在端上残留（lastId 只增不减，显式接受）。
-- ----------------------------
DROP TABLE IF EXISTS `love_message`;
CREATE TABLE `love_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（增量扫按 couple_id + id 游标）',
  `sender_id` bigint NOT NULL COMMENT '发送方用户编号',
  `receiver_id` bigint NOT NULL COMMENT '接收方用户编号（快照，解绑后审计归对）',
  `kind` tinyint NOT NULL COMMENT '消息类型（1文本悄悄话 2心愿消息）',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本内容，或心愿文案快照',
  `wish_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心愿 key 快照（kind=2 时有值）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心愿颜色快照（hex，kind=2 时有值）',
  `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心愿图标快照（icon name，kind=2 时有值）',
  `audit_status` tinyint NOT NULL DEFAULT 1 COMMENT '审核状态（0待审 1通过 2命中；首期恒写 1 兜底）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_time`(`couple_id` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '悄悄话/心愿消息表（情侣业务）';

-- ----------------------------
-- 菜单/按钮权限 SQL（M3，INTEGRATION-DESIGN.md §6.6 / §5.3 权限串）
-- 结构：「情侣业务」目录(type=1) → 用户/情侣/待办/消息 四管理菜单(type=2) → 按钮(type=3)；
-- 挂接：SELECT @parentId := LAST_INSERT_ID()（codegen sql.vm:23-49 同款）；
-- 权限串与 §5.3 表格逐字符一致（M-4：串不一致 = 403）；
-- 幂等（M-3）：头部先 DELETE 同 permission / component_name / 目录名旧数据再插；
-- 心情无 admin 管理页与权限（D-13，§8.1 四页为 user/couple/todo/message）。
-- 注意：system_user 等超管角色自动拥有全部菜单，不写 role_menu 分配 SQL（§6.6 定案）。
-- ----------------------------
-- 幂等清理：按钮（按 permission）→ 管理菜单（按 component_name）→ 目录（按 type+name），先子后父
DELETE FROM `system_menu` WHERE `permission` IN ('love:user:query', 'love:user:update', 'love:user:delete', 'love:couple:query', 'love:couple:update', 'love:todo:query', 'love:todo:delete', 'love:message:query', 'love:message:delete');
DELETE FROM `system_menu` WHERE `component_name` IN ('LoveUser', 'LoveCouple', 'LoveTodo', 'LoveMessage');
DELETE FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务';

-- 目录：「情侣业务」
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('情侣业务', '', 1, 30, 0, 'love', 'fa:heart', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @directoryId := LAST_INSERT_ID();

-- 管理菜单一：C 端用户管理（component_name 唯一 = LoveUser）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('C 端用户管理', '', 2, 1, @directoryId, 'user', 'ep:avatar', 'love/user/index', 'LoveUser', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('用户查询', 'love:user:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('用户更新', 'love:user:update', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('用户删除', 'love:user:delete', 3, 3, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 管理菜单二：情侣关系管理（component_name 唯一 = LoveCouple）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('情侣关系管理', '', 2, 2, @directoryId, 'couple', 'ep:user', 'love/couple/index', 'LoveCouple', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('情侣查询', 'love:couple:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('情侣解绑', 'love:couple:update', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 管理菜单三：待办管理（component_name 唯一 = LoveTodo）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('待办管理', '', 2, 3, @directoryId, 'todo', 'ep:list', 'love/todo/index', 'LoveTodo', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('待办查询', 'love:todo:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('待办删除', 'love:todo:delete', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 管理菜单四：消息管理（component_name 唯一 = LoveMessage）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('消息管理', '', 2, 4, @directoryId, 'message', 'ep:chat-line-round', 'love/message/index', 'LoveMessage', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('消息查询', 'love:message:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('消息删除', 'love:message:delete', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ----------------------------
-- M3 实施修正：love_user.mobile 列宽 16 → 32（INTEGRATION-DESIGN.md §4 love_user.mobile 注记）
-- 背景：admin 删除用户按本文件头部约定将 mobile 改写为 concat(mobile, '#', id) 释放 uk_mobile 唯一键
--      （yudao 惯例手法），11 位手机号 + '#' + 5 位 id 即达 17 字符，varchar(16) 在 MySQL 严格模式下
--      报 Data too long（id ≥ 10000 后 admin 删用户必 500），放宽到 32 后 id 增长不爆列；
--      Java 侧 concat 改写逻辑不变，仅列宽修正。
-- 重放安全：与建表段「DROP 后重建会清数据」不同，ALTER MODIFY 为幂等 DDL（列宽/默认值收敛到目标值），
--      重复执行无副作用、不清任何数据；本段随 M3 三表 DDL/菜单段同一执行时机（M3 段此前尚未执行）。
-- ----------------------------
ALTER TABLE `love_user`
    MODIFY COLUMN `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '手机号码（M3 实施修正 16→32：容纳 admin 删除时 concat(mobile,#,id) 改写释放 uk_mobile 唯一键，id 增长不爆列）';

-- ----------------------------------------------------------------------------
-- M3.5 段：心情选项自定义（情侣私有心情字典 + 后台默认档配置，2026-10-16）
-- 设计（INTEGRATION-DESIGN.md D-14 / §12.5.2「先快照后实体」）：
-- 1. love_mood_option_default = B 端管理的默认模板，绑定事务内复制给新 couple（播种即复制，
--    admin 后续改模板不回灌已绑定情侣）；
-- 2. love_mood_option = 情侣私有字典（app 读写，双方共享）；不建 (couple_id,label) uk——
--    逻辑删除行占位 uk 是 uk_user_date 已踩过的坑，重名由 service 查重兜底；
-- 3. love_mood_record 落库时快照 option 的 label/emoji/color：删除选项后历史月历渲染不变灰
--    （love_message 心愿快照同哲学）；⚠ 不复用 mood_type 存 optionId——存量行 mood_type=1-5
--    会与真实 option id 撞号 → 跨 couple 渲染错乱，故新增 option_id 列、mood_type 废弃置 NULL。
-- 执行注意：① ADD COLUMN 非幂等（MySQL 无 IF NOT EXISTS），本段为一次性迁移段——重放报
--    duplicate column 属预期，跳过本段 ALTER/建表即可（M3 段先例：MODIFY 幂等可重放、ADD 不幂等）；
--    ② emoji 行要求客户端连接 utf8mb4 执行（Navicat/DataGrip 默认满足，GBK 控制台勿直灌）；
--    ③ 段内 UPDATE/INSERT...SELECT 均带幂等条件，ALTER 成功后重放无副作用。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- Table structure for love_mood_option_default（心情默认选项模板，M3.5）
-- 说明：B 端管理对象（love:mood-option-default:* 权限）；status=启用 的行参与绑定播种
-- ----------------------------
DROP TABLE IF EXISTS `love_mood_option_default`;
CREATE TABLE `love_mood_option_default`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '默认选项编号',
  `label` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '心情名（如 开心）',
  `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表情（utf8mb4 文本，如 😄）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '颜色 hex（如 #FFB93C，与心愿消息 color 同款约定）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示顺序（播种行继承此值；越小越靠前）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行不参与绑定播种）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '心情默认选项模板表（情侣业务，B 端配置）';

-- 初始 5 行 = 原 MoodTypeEnum + 客户端配色原样迁入（M3.5 前端上 5 档的一比一搬家，
-- 存量记录回填与现有 UI 观感完全一致；幂等：已存在同 label+deleted=0 行则跳过）
INSERT INTO `love_mood_option_default` (`label`, `emoji`, `color`, `sort`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '开心' AS `label`, '😄' AS `emoji`, '#FFB93C' AS `color`, 1 AS `sort`, 0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
  UNION ALL SELECT '平静', '😌', '#6C8BFF', 2, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '难过', '🥺', '#8FA3BF', 3, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '生气', '😠', '#FF5A5A', 4, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '疲惫', '😪', '#9B6BFF', 5, 0, '1', NOW(), '1', NOW(), b'0'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_mood_option_default` d WHERE d.`deleted` = b'0' AND d.`label` = seed.`label`
);

-- ----------------------------
-- Table structure for love_mood_option（心情选项，情侣私有字典，M3.5）
-- 说明：
-- 1. couple_id 维度：双方共享同一份字典；绑定事务播种、app 端增删（写恒 POST）；
-- 2. sort 恒 999 = 用户新增（落尾）；播种行继承模板 sort（1..5），排序 sort asc, id asc；
-- 3. 解绑不清理（couple_id 留历史值，D-11 同语义）；重绑新 couple_id 重新播种；
-- 4. 逻辑删除（BaseDO）；被删选项的历史记录靠 love_mood_record 快照列渲染，不受影响。
-- ----------------------------
DROP TABLE IF EXISTS `love_mood_option`;
CREATE TABLE `love_mood_option`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '心情选项编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（私有字典归属）',
  `label` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '心情名（如 开心）',
  `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表情（utf8mb4 文本）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '颜色 hex（如 #FFB93C）',
  `sort` int NOT NULL DEFAULT 999 COMMENT '显示顺序（播种行继承模板值；用户新增恒 999 落尾）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple`(`couple_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '心情选项表（情侣私有字典）';

-- ----------------------------
-- love_mood_record 演进（M3.5，一次性 ALTER——重放报 duplicate column 属预期）
-- 新增 option_id + 三快照列；mood_type 废弃置 NULL（新记录不再写值，存量行回填见下）
-- ----------------------------
ALTER TABLE `love_mood_record`
    ADD COLUMN `option_id` bigint NULL DEFAULT NULL COMMENT '心情选项编号（love_mood_option.id，M3.5 起写入）',
    ADD COLUMN `mood_label` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心情名快照（记录时点，删除选项后历史渲染不依赖字典）',
    ADD COLUMN `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '表情快照（记录时点）',
    ADD COLUMN `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '颜色 hex 快照（记录时点）',
    MODIFY COLUMN `mood_type` tinyint NULL DEFAULT NULL COMMENT '（废弃）旧 1-5 心情枚举，仅存量行有值；M3.5 起写 NULL（新值 = option_id + 快照列）';

-- 存量行回填快照（幂等：color IS NULL 才命中；映射 = 原 MoodTypeEnum + 客户端配色）
UPDATE `love_mood_record`
SET `mood_label` = CASE `mood_type`
        WHEN 1 THEN '开心'
        WHEN 2 THEN '平静'
        WHEN 3 THEN '难过'
        WHEN 4 THEN '生气'
        WHEN 5 THEN '疲惫'
        ELSE `mood_label` END,
    `emoji` = CASE `mood_type`
        WHEN 1 THEN '😄'
        WHEN 2 THEN '😌'
        WHEN 3 THEN '🥺'
        WHEN 4 THEN '😠'
        WHEN 5 THEN '😪'
        ELSE `emoji` END,
    `color` = CASE `mood_type`
        WHEN 1 THEN '#FFB93C'
        WHEN 2 THEN '#6C8BFF'
        WHEN 3 THEN '#8FA3BF'
        WHEN 4 THEN '#FF5A5A'
        WHEN 5 THEN '#9B6BFF'
        ELSE `color` END
WHERE `color` IS NULL AND `mood_type` BETWEEN 1 AND 5;

-- 存量情侣补播种（幂等：已有选项的 couple 跳过——M3.5 前绑定的 couple 不重绑也能拿到基础选项）
INSERT INTO `love_mood_option` (`couple_id`, `label`, `emoji`, `color`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT c.`id`, d.`label`, d.`emoji`, d.`color`, d.`sort`, '1', NOW(), '1', NOW(), b'0'
FROM `love_couple` c
JOIN `love_mood_option_default` d
    ON d.`deleted` = b'0' AND d.`status` = 0
WHERE NOT EXISTS (
    SELECT 1 FROM `love_mood_option` o WHERE o.`couple_id` = c.`id` AND o.`deleted` = b'0'
);

-- 存量行回填 option_id（尽力而为：couple + label 命中已播种选项则挂上；不命中保持 NULL，
-- 快照列已保渲染，option_id 仅为审计链路，NULL 不影响端上）
UPDATE `love_mood_record` r
JOIN `love_mood_option` o
    ON o.`couple_id` = r.`couple_id` AND o.`deleted` = b'0' AND o.`label` = r.`mood_label`
SET r.`option_id` = o.`id`
WHERE r.`option_id` IS NULL AND r.`mood_label` IS NOT NULL;

-- ----------------------------
-- 菜单/按钮权限 SQL（M3.5，「情侣业务」目录下第五个管理页）
-- 挂接：目录 id 按 type+name 回查（本段晚于 M3 菜单段执行，不能依赖 M3 段的 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:mood-option-default:query', 'love:mood-option-default:create', 'love:mood-option-default:update', 'love:mood-option-default:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveMoodOptionDefault';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单五：心情默认选项（component_name 唯一 = LoveMoodOptionDefault）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('心情默认选项', '', 2, 5, @loveDirectoryId, 'mood-option-default', 'ep:mood', 'love/moodOptionDefault/index', 'LoveMoodOptionDefault', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('默认选项查询', 'love:mood-option-default:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('默认选项新增', 'love:mood-option-default:create', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('默认选项更新', 'love:mood-option-default:update', 3, 3, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('默认选项删除', 'love:mood-option-default:delete', 3, 4, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- M5 段：纪念日表 + 菜单/按钮权限（INTEGRATION-DESIGN.md §12.4 预留落位 / §5.2 anniversary 组 / D-17）
-- 本段追加于 M3.5 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- love_anniversary 继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），
-- 与 love_couple 的物理删除路线相反；解绑/重绑后数据保留，couple_id 留历史值（D-11）。
-- 无 update 端点（删了重加）；repeat_type tinyint 值域 1=每年 2=一次性，预留月/周扩展；
-- 倒数算术全在端上（GMT+8 日序），服务端只存原始日期不做任何推算。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `love_anniversary`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '纪念日编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（couple 共享，列表按此过滤）',
  `owner_id` bigint NOT NULL COMMENT '创建人用户编号（仅记录归属，双方均可见可操作）',
  `title` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '纪念日名称（恋爱纪念日/TA 的生日/领证日…）',
  `anniv_date` date NOT NULL COMMENT '纪念日日期（原始日期，每年重复的下一次发生日由端上推算，D-17）',
  `repeat_type` tinyint NOT NULL DEFAULT 1 COMMENT '重复类型（1=每年重复 2=一次性；值域收口在 service create）',
  `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'emoji 图标（端上预设字典选一，客户端字典；空串端上按 ❤ 兜底）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple`(`couple_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '纪念日表（情侣业务）';

-- ----------------------------
-- 菜单/按钮权限 SQL（M5，「情侣业务」目录下第六个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M3.5 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:anniversary:query', 'love:anniversary:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveAnniversary';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单六：纪念日（component_name 唯一 = LoveAnniversary）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('纪念日', '', 2, 6, @loveDirectoryId, 'anniversary', 'ep:calendar', 'love/anniversary/index', 'LoveAnniversary', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('纪念日查询', 'love:anniversary:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('纪念日删除', 'love:anniversary:delete', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
