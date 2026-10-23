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
--    （M12 修订：发送方 2 分钟内可撤回自己发的消息，撤回=recalled 墓碑列标记不删行，见文末 M12 段）
-- ----------------------------
DROP TABLE IF EXISTS `love_message`;
CREATE TABLE `love_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（增量扫按 couple_id + id 游标）',
  `sender_id` bigint NOT NULL COMMENT '发送方用户编号',
  `receiver_id` bigint NOT NULL COMMENT '接收方用户编号（快照，解绑后审计归对）',
  `kind` tinyint NOT NULL COMMENT '消息类型（1文本悄悄话 2心愿消息 3图片消息 4语音 5视频 6贴纸 7自定义卡片，M23 补 4-7：语音/视频为 {"url","dur"/"cover","dur"} JSON 快照、贴纸为 key 明文、卡片为 {"type","title","sub","icon"} 快照）',
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

-- ============================================================================
-- M8 段：每日打卡表 + 菜单/按钮权限（INTEGRATION-DESIGN.md §12.4 预留落位 / §5.2 checkin 组）
-- 本段追加于 M5 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- love_checkin 继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），与 love_anniversary 同路线；
-- 解绑/重绑后数据保留，couple_id 留历史值，当日再打卡刷新到当前 couple（MoodServiceImpl 同款口径）。
-- uk_user_date(user_id, checkin_date) 一人一天一条（照 love_mood_record 的 uk_user_date 先例）：
-- 逻辑删除行仍占 uk 位，重打走复活回退（CheckinMapper.reviveDeletedByUserIdAndDate 原生 UPDATE）；
-- 无补打/无内容/无撤销端点（撤销仅 admin delete）；共同连续 streak 等聚合在 service 内存计算（M8）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `love_checkin`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '打卡编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（couple 共享，列表/月历/汇总按此过滤）',
  `user_id` bigint NOT NULL COMMENT '打卡用户编号（uk_user_date 一人一天一条，双人各自记录）',
  `checkin_date` date NOT NULL COMMENT '打卡日期（服务端当天，无补打）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_date`(`user_id` ASC, `checkin_date` ASC) USING BTREE,
  INDEX `idx_couple_date`(`couple_id` ASC, `checkin_date` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日打卡表（情侣业务）';

-- ----------------------------
-- 菜单/按钮权限 SQL（M8，「情侣业务」目录下第七个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M3.5/M5 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:checkin:query', 'love:checkin:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveCheckin';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单七：每日打卡（component_name 唯一 = LoveCheckin，M5 实况同款无 Menu 后缀）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('每日打卡', '', 2, 7, @loveDirectoryId, 'checkin', 'ep:alarm-clock', 'love/checkin/index', 'LoveCheckin', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('打卡查询', 'love:checkin:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('打卡删除', 'love:checkin:delete', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- M9 段：相册瞬间表 + 菜单/按钮权限（INTEGRATION-DESIGN.md §12.4 预留落位 / §5.2 moment 组）
-- 本段追加于 M8 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- love_moment 继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），与 love_checkin 同路线；
-- 媒资复用 infra 文件服务（POST /app-api/infra/file/upload，M4 已通），本表只存 image_url 字符串
-- 不建存储（§12.4 落位表口径）；图片本体不级联删（D-12 残留策略同款哲学）。
-- 不建 uk（同人可发多条），逻辑删后不存在「删行占 uk 位」问题，重新发布恒走 insert 新行。
-- 删除权限 = 仅上传者本人（1_003_008_001 用户维度校验，防误删伴侣的瞬间）；admin 兜底可删任何。
-- 无 uk / 无补发历史时间 / 无 update 端点（删了重发，D-17 口径）；列表游标分页 id 倒序（M9）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `love_moment`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '瞬间编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（couple 共享，列表按此过滤；解绑重绑后旧瞬间留历史值）',
  `user_id` bigint NOT NULL COMMENT '发布用户编号（仅上传者本人可删）',
  `image_url` varchar(512) NOT NULL COMMENT '图片完整 URL（infra 文件服务上传返回，@PermitAll 匿名可读）',
  `text` varchar(64) NOT NULL DEFAULT '' COMMENT '配文（可选，≤64 字，空串 = 无配文）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_id`(`couple_id` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '相册瞬间表（情侣业务）';

-- ----------------------------
-- 菜单/按钮权限 SQL（M9，「情侣业务」目录下第八个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M3.5/M5/M8 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:moment:query', 'love:moment:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveMoment';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单八：相册瞬间（component_name 唯一 = LoveMoment，M5/M8 实况同款无 Menu 后缀）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('相册瞬间', '', 2, 8, @loveDirectoryId, 'moment', 'ep:picture', 'love/moment/index', 'LoveMoment', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('瞬间查询', 'love:moment:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('瞬间删除', 'love:moment:delete', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ----------------------------
-- M10 段：聊天图片消息（kind=3，content 列存图片 URL；INTEGRATION-DESIGN.md §5.2 message 组）
-- 重放安全：MODIFY 为幂等 DDL（列宽/注释收敛到目标值，love_user.mobile M3 修正段先例）；
-- varchar(500)→512 变长扩列：仅元数据变更、存量行不动、在线 DDL 秒级；
-- ⚠ 上线后禁止回改 512→500（存量超长行会截断报错）。
-- ----------------------------
ALTER TABLE `love_message`
    MODIFY COLUMN `kind` tinyint NOT NULL COMMENT '消息类型（1文本悄悄话 2心愿消息 3图片消息）',
    MODIFY COLUMN `content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本内容，或心愿文案快照，或图片完整 URL（kind=3，infra 文件服务上传返回）';

-- ----------------------------
-- M11 段：聊天未读红点已读游标（INTEGRATION-DESIGN.md §5.2 message 组）
-- love_user 加已读游标列：user 同时至多在一个 couple（uk_user_a/uk_user_b 终审），游标为
-- 全局消息 id 只增不减——解绑重绑新 couple_id 的消息 id 必大于旧游标，语义自然，无需清零；
-- 未读口径 = couple 内 id > chat_read_cursor 且 sender_id <> 自己（admin 逻辑删不计，MP @TableLogic）。
-- 执行注意：ADD COLUMN 非幂等（MySQL 无 IF NOT EXISTS），本段为一次性迁移段——重放报
--    duplicate column（1060）属预期，跳过本段 ALTER 即可（M3.5 段执行注意同款先例）；
--    上线顺序必须 SQL 先于新代码（新代码 DO 映射依赖本列）。
-- ----------------------------
ALTER TABLE `love_user`
    ADD COLUMN `chat_read_cursor` bigint NOT NULL DEFAULT 0 COMMENT '聊天已读游标（已读到的最大 love_message.id；0=未读过。全局消息 id 只增不减，解绑重绑新 couple 的消息 id 必大于旧游标）';

-- ----------------------------
-- M12 段：聊天消息撤回（INTEGRATION-DESIGN.md §5.2 message 组 / D-12 修订）
-- love_message 加撤回墓碑列：撤回=标记不删行——content 原文保留（admin 审计可看原文），
-- 与 admin 逻辑删（deleted）双列独立；message/list 回包照常含已撤回行（端上按 recalled 渲染
-- 灰条提示），wish-list 排除已撤回行（心愿时间线不呈现墓碑）。
-- 执行注意：ADD COLUMN 非幂等（MySQL 无 IF NOT EXISTS），本段为一次性迁移段——重放报
--    duplicate column（1060）属预期，跳过本段 ALTER 即可（M11 段执行注意同款先例）；
--    上线顺序必须 SQL 先于新代码（新代码 DO 映射依赖本列）。
-- ----------------------------
ALTER TABLE `love_message`
    ADD COLUMN `recalled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已撤回（M12 墓碑标记：撤回不删行，content 原文留档审计；与 deleted 逻辑删独立）';

-- ============================================================================
-- M14 段：恋爱清单表 + 菜单/按钮权限（INTEGRATION-DESIGN.md §12.4 预留落位 / §5.2 wish 组）
-- 本段追加于 M12 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- love_wish 继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），与 love_moment 同路线；
-- 解绑/重绑后数据保留，couple_id 留历史值，重绑产生新 couple_id（D-11）。
-- 与 love_message kind=2 心愿卡消息【并存不替代】（D-19a）：kind=2 是快照式聊天消息，
-- 本表是可勾选完成的共享清单实体；wish_key 不升级外键（快照哲学）。
-- 无 uk（couple 双方均可添加多条，moment 先例），逻辑删后重新添加恒走 insert 新行；
-- done 对应 bit(1)（MyBatis Plus 自动映射 Boolean），done_by/done_time 可空（未完成 NULL）；
-- 完成消息「恰发一条」由 WishMapper.markDone 的 CAS（WHERE done = b'0'）单闸保证（D-19b），
-- 无 celebrated 类防重发列；删除权限 = 仅创建者本人（1_003_009_001 用户维度校验，防误删），
-- 不存在/跨 couple 统一 1_003_009_000 防探测；admin 兜底可删任何。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `love_wish`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '心愿编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（couple 共享，双方互见；解绑重绑后旧心愿留历史值）',
  `user_id` bigint NOT NULL COMMENT '创建人用户编号（仅创建者本人可删，D-19e）',
  `title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '心愿标题（≤64 字，service trim 后落库）',
  `done` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否完成（bit(1)↔Boolean 自动映射）',
  `done_by` bigint NULL DEFAULT NULL COMMENT '完成人用户编号（勾选者，可与创建人不同；未完成为 NULL）',
  `done_time` datetime NULL DEFAULT NULL COMMENT '完成时间（未完成为 NULL）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_id`(`couple_id` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '恋爱清单表（情侣业务）';

-- ----------------------------
-- 菜单/按钮权限 SQL（M14，「情侣业务」目录下第九个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M3.5/M5/M8/M9 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:wish:query', 'love:wish:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveWish';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单九：恋爱清单（component_name 唯一 = LoveWish，M5/M8/M9 实况同款无 Menu 后缀）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('恋爱清单', '', 2, 9, @loveDirectoryId, 'wish', 'ep:collection', 'love/wish/index', 'LoveWish', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('心愿查询', 'love:wish:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('心愿删除', 'love:wish:delete', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- M15 段：相册二期 多图 + 点赞（INTEGRATION-DESIGN.md §5.2 moment 组扩展 / D-20 组）
-- 本段追加于 M14 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- ① love_moment 加 image_urls（TEXT NULL，多图 URL JSON 数组字符串，≤9 元素，D-20a）；
--    image_url 列保留 = 首图冗余（admin LoveMomentRespVO / web 管理页只读 imageUrl，
--    管理后台零改动）；存量单图回填 [image_url]，老版 App 只传 imageUrl 由服务端归一兼容。
-- ② love_moment_like 点赞表（D-20b）：点赞 = INSERT IGNORE 走 uk_moment_user 吸收并发
--    重复（幂等）；取消点赞 = 【物理 DELETE】（显式 SQL）——uk 与逻辑删除冲突的唯一解
--    （墓碑行会永久占 uk 位），故本表 deleted 恒 0；删瞬间不级联清赞（D-20h，残留行
--    永不被查询，D-12 残留策略同款哲学）。
-- 无新菜单段：admin 侧零改动（无新端点/无新权限），M9 相册菜单沿用。
-- 执行注意：上线顺序必须 SQL 先于新代码（新代码 DO 映射依赖 image_urls 列）。
-- ============================================================================

-- 1) 加列（幂等重放：information_schema 判列存在 + PREPARE 动态执行——
--    M11/M12 段的 ADD COLUMN 为一次性迁移段（重放报 1060 属预期），本段升级为可重放）
SET @m15_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_moment' AND COLUMN_NAME = 'image_urls');
SET @m15_ddl := IF(@m15_col = 0,
    'ALTER TABLE `love_moment` ADD COLUMN `image_urls` text NULL COMMENT ''多图 URL JSON 数组字符串（M15，如 [\"u1\",\"u2\"]，≤9 元素；image_url 列保留为首图冗余，admin 零改动）''',
    'SELECT 1');
PREPARE m15_stmt FROM @m15_ddl;
EXECUTE m15_stmt;
DEALLOCATE PREPARE m15_stmt;

-- 2) 存量单图回填（幂等：回填后 image_urls 非空，WHERE 不再命中）
UPDATE `love_moment`
   SET `image_urls` = JSON_ARRAY(`image_url`)
 WHERE `image_urls` IS NULL OR `image_urls` = '';

-- 3) 点赞表（unlike 物理删 → deleted 恒 0，uk 无墓碑占位；couple 恒 2 人，双方均可赞任意瞬间）
CREATE TABLE IF NOT EXISTS `love_moment_like`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '点赞编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（归属第二道闸，service 已校验；解绑重绑后留历史值）',
  `moment_id` bigint NOT NULL COMMENT '瞬间编号（love_moment.id，不设外键；删瞬间不级联清赞，残留行无害——D-12 残留策略同款哲学）',
  `user_id` bigint NOT NULL COMMENT '点赞用户编号（couple 恒 2 人，双方均可点赞任意瞬间）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（继承 BaseDO 惯例；本表 unlike 物理删，恒 0）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_moment_user`(`moment_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_moment_id`(`moment_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '相册瞬间点赞表（情侣业务，M15）';

-- ---------------------------------------------------------------------------
-- M16 用户详细资料（2026-10-18）：love_user 加 sex / birthday / signature 三列。
-- M15 段同款可重放模板（information_schema + PREPARE，重放安全）；
-- sex 语义 0=女 1=男（自建语义，与 yudao 系统字典 0=男/1=女 相反，web 端本地 MAP 同步）
-- ---------------------------------------------------------------------------
SET @m16_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_user' AND COLUMN_NAME = 'sex');
SET @m16_ddl := IF(@m16_col = 0,
    'ALTER TABLE `love_user` ADD COLUMN `sex` tinyint NULL COMMENT ''性别 0-女 1-男 NULL-未设置（与系统字典语义相反）''',
    'SELECT 1');
PREPARE m16_stmt FROM @m16_ddl;
EXECUTE m16_stmt;
DEALLOCATE PREPARE m16_stmt;

SET @m16_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_user' AND COLUMN_NAME = 'birthday');
SET @m16_ddl := IF(@m16_col = 0,
    'ALTER TABLE `love_user` ADD COLUMN `birthday` date NULL COMMENT ''生日''',
    'SELECT 1');
PREPARE m16_stmt FROM @m16_ddl;
EXECUTE m16_stmt;
DEALLOCATE PREPARE m16_stmt;

SET @m16_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_user' AND COLUMN_NAME = 'signature');
SET @m16_ddl := IF(@m16_col = 0,
    'ALTER TABLE `love_user` ADD COLUMN `signature` varchar(100) NULL COMMENT ''个性签名''',
    'SELECT 1');
PREPARE m16_stmt FROM @m16_ddl;
EXECUTE m16_stmt;
DEALLOCATE PREPARE m16_stmt;

-- ============================================================================
-- M17 段：恋爱灵感表 + 种子内容 + 菜单/按钮权限（首页「今日情话」「约会灵感」两卡落位）
-- 本段追加于 M16 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- love_inspiration = 全局共享的运营内容（无 couple_id / user_id 归属，所有情侣共读同一池），
-- 继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），mood_option_default 同路线；
-- kind=1 情话（App「今日情话」池，服务端按日轮换 dateKey 取模 + 随机换一句）；
-- kind=2 约会灵感（App「约会灵感」列表池，全量拉取端上分类筛选）；status=1 停用行两端均不可见。
-- App 只读零错误路径（池空走端上/服务端兜底文案，不打错）；admin 全量 CRUD（B 端配置对象）。
-- 无 uk（内容无唯一性约束，重复靠运营自律）；idx_kind 普通索引（两池各自按 kind 查询）。
-- 上线顺序必须 SQL 先于新代码（新代码 DO 映射依赖本表）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `love_inspiration`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '灵感编号',
  `kind` tinyint NOT NULL COMMENT '类型（1-情话 2-约会灵感）',
  `title` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标题（约会灵感用，情话为 NULL）',
  `content` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容（情话正文 / 约会灵感描述）',
  `category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分类（情话: 甜宠/日常/表白；约会灵感: 室内/户外/美食/观影/手作；App 端动态聚合 chips）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行不出现在 App）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_kind`(`kind` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '恋爱灵感表（情侣业务，B 端配置，App 只读）';

-- ----------------------------
-- 种子内容（M17，48 行：情话 24 + 约会灵感 24（室内 5/户外 5/美食 5/观影 5/手作 4）；幂等：已存在同 kind+content+deleted=0 行则跳过，
-- M3.5 种子 INSERT...SELECT...UNION ALL 模板同款；creator='1'、NOW()、不写显式 id）
-- ----------------------------
INSERT INTO `love_inspiration` (`kind`, `title`, `content`, `category`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  -- 情话 kind=1（甜宠 8 / 日常 8 / 表白 8）
  SELECT 1 AS `kind`, NULL AS `title`, '我今天真的很想你，比昨天多一点，比明天少一点。' AS `content`, '甜宠' AS `category`, 0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
  UNION ALL SELECT 1, NULL, '全世界那么多人，为什么偏偏遇见你——因为你是我的例外。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '你一笑，我就沦陷了，这题对我来说超纲了。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '别人问我最近过得怎么样，我说挺好的，因为有你。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '我想把世界上最好的都给你，后来发现世界上最好的就是你。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '你是我温暖的手套，冰镇的啤酒，带着阳光味道的衬衫。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '我的偏爱有限，全部留给你刚好用完。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '遇见你之后，我的口头禅从「随便」变成了「听你的」。', '甜宠', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '今天也要提醒你：有人在偷偷喜欢你，就是我。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '一日三餐，四季冷暖，都想和你一起过。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '买菜做饭洗碗的时候，就觉得日子因为有你在发光。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '不用刻意纪念，和你在一起的每一天都值得记下来。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '下班回家的路上想好了今晚吃什么，最后还是决定：想你。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '今天也没发生什么大事，就是又喜欢你了一整天。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '你在闹，我在笑，这就是我想要的生活。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '天气预报说今天有雨，我带了两把伞，一把给你。', '日常', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '我喜欢你，不是一时兴起，而是蓄谋已久。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '我见过很多风景，但都不及你朝我走来的样子。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '余生请多指教，我打算赖上你了。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '情话是我学不会的套路，但喜欢你是我的天赋。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '你不用多好，我喜欢就好；我没有多好，你不嫌弃就好。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '山野万里，你是我藏在星星里的浪漫。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '我最近手头有点紧，想借你的手牵一牵。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 1, NULL, '从遇见你开始，我的人生就只有两种状态：想你，和见你。', '表白', 0, '1', NOW(), '1', NOW(), b'0'
  -- 约会灵感 kind=2（室内 5 / 户外 5 / 美食 5 / 观影 5 / 手作 4）
  UNION ALL SELECT 2, '一起下厨做一顿饭', '挑一道都想吃的菜，分工一个掌勺一个打下手，摆盘拍照记录成果。', '室内', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '拼图 / 积木挑战', '选一幅 500 片以上的拼图或一套积木，一下午的专注时光，完成后装裱留念。', '室内', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '双人桌游之夜', '翻翻家里落灰的桌游，或去桌游吧玩几局双人对战，输的人负责倒水。', '室内', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '居家电影马拉松', '选一个系列连看两三部，配上爆米花和小零食，窝在沙发里一整天。', '室内', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, 'DIY 陶艺体验', '找一家陶艺工作室，亲手做一个杯子或小摆件，交换成品当纪念。', '室内', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '城市漫游 City Walk', '挑一条没走过的老街区，慢慢走慢慢拍，遇见好看的小店就进去逛逛。', '户外', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '公园野餐日', '带上野餐垫、水果和小音箱，找一片树荫躺一下午，看云发呆。', '户外', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '骑行探店', '租两辆共享单车，沿着河或绿道骑到郊外的咖啡馆，累并快乐着。', '户外', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '看一场日出或日落', '早起去山顶或江边看日出，或者傍晚找视野好的天台看日落，拍下剪影合影。', '户外', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '夜市扫街', '从街头吃到街尾，每人负责选三样，互相投喂打分，评选本日最佳。', '美食', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '火锅 + 双人纪录片', '晚上吃热气腾腾的火锅，回家路上挑一部美食纪录片，边吃边聊。', '美食', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '烘焙半成品日', '去 DIY 烘焙坊做饼干或蛋糕，失败也无所谓，糊了的也是限量款。', '美食', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '便利店晚餐企划', '每人 30 元预算在便利店凑一顿晚餐，坐在窗边吃，给每种食物打分。', '美食', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '私人影院双人厅', '订一间双人点播影院，自己挑片，瘫着看完一部想看很久的电影。', '观影', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '老片重映打卡', '关注本地影院的重映排片，在大银幕重温一部经典，感受岁月的味道。', '观影', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '各选一部互赌评分', '每人各选一部对方没看过的片子，看完互打分，输的人请下一顿。', '观影', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '露天电影 / 音乐节', '关注本地露天放映或小型音乐节活动，带上小毯子去现场，氛围感拉满。', '观影', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '手写交换日记', '买一本好看的笔记本，轮流每周写一篇给对方看，写满一年再回头翻。', '手作', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '纪念相册整理夜', '把手机里的合影挑出来打印，做成一本实体相册，标注日期和当时的故事。', '手作', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '情侣手链 DIY', '去手作店编两条手链或串珠，各戴一条，出门就是显眼包情侣。', '手作', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '给对方做一件小礼物', '折纸、绘画、粘土不限，本周内偷偷准备，周末交换揭晓。', '手作', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '逛一场博物馆 / 展览', '挑一个双方都感兴趣的展，租两台讲解器，逛完在文创店挑一枚冰箱贴。', '户外', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '早午餐 Brunch 之约', '周末睡到自然醒，找一家评价好的 Brunch 店，慢悠悠吃到中午，顺便散步消食。', '美食', 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT 2, '新片首日场', '盯一部两人都想看的上映新片，买首日场 or 首周末场，看完趁热打铁聊剧情。', '观影', 0, '1', NOW(), '1', NOW(), b'0'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_inspiration` d WHERE d.`deleted` = b'0' AND d.`kind` = seed.`kind` AND d.`content` = seed.`content`
);

-- ----------------------------
-- 菜单/按钮权限 SQL（M17，「情侣业务」目录下第十个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M3.5/M5/M8/M9/M14 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:inspiration:query', 'love:inspiration:create', 'love:inspiration:update', 'love:inspiration:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveInspiration';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单十：恋爱灵感（component_name 唯一 = LoveInspiration，M5/M8/M9/M14 实况同款无 Menu 后缀）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('恋爱灵感', '', 2, 10, @loveDirectoryId, 'inspiration', 'ep:magic-stick', 'love/inspiration/index', 'LoveInspiration', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('灵感查询', 'love:inspiration:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('灵感新增', 'love:inspiration:create', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('灵感编辑', 'love:inspiration:update', 3, 3, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('灵感删除', 'love:inspiration:delete', 3, 4, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
-- M20 段：上线准备批 索引补齐（8 条普通索引，纯追加可重放——STATISTICS 判重 + PREPARE，M15 段同款模板）
-- 纯索引 DDL 与新代码无先后依赖；收益锚点 = M18 删用户级联 OR 两侧/user_id/owner_id 与 admin 筛选（明细见提交说明）
-- ============================================================================

-- love_message.idx_couple_kind
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_message' AND INDEX_NAME = 'idx_couple_kind');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_message` ADD INDEX `idx_couple_kind`(`couple_id` ASC, `kind` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_message.idx_sender_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_message' AND INDEX_NAME = 'idx_sender_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_message` ADD INDEX `idx_sender_id`(`sender_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_message.idx_receiver_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_message' AND INDEX_NAME = 'idx_receiver_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_message` ADD INDEX `idx_receiver_id`(`receiver_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_moment.idx_user_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_moment' AND INDEX_NAME = 'idx_user_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_moment` ADD INDEX `idx_user_id`(`user_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_wish.idx_user_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_wish' AND INDEX_NAME = 'idx_user_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_wish` ADD INDEX `idx_user_id`(`user_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_moment_like.idx_user_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_moment_like' AND INDEX_NAME = 'idx_user_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_moment_like` ADD INDEX `idx_user_id`(`user_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_todo.idx_owner_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_todo' AND INDEX_NAME = 'idx_owner_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_todo` ADD INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- love_anniversary.idx_owner_id
SET @m20_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_anniversary' AND INDEX_NAME = 'idx_owner_id');
SET @m20_ddl := IF(@m20_idx = 0,
    'ALTER TABLE `love_anniversary` ADD INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE m20_stmt FROM @m20_ddl;
EXECUTE m20_stmt;
DEALLOCATE PREPARE m20_stmt;

-- ============================================================================
-- M23 段：业务配置表 + 种子配置 + 菜单/按钮权限（love 业务专属 KV 配置，B 端配置 App 只读）
-- 本段追加于 M20 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- love_config = 全局共享的运营 KV 配置（无 couple_id / user_id 归属，所有用户共读同一池），
-- 继承 BaseDO 走【逻辑删除】（deleted 列 + DO 侧 @TableLogic），love_inspiration 同路线。
-- config_key 是 App 读取契约（小写字母数字下划线，服务端 @Pattern 收口）；
-- status=1 停用行不下发 App（端上 LoveConfigStore 对缺键走内置兜底值）。
-- App 只读单端点（GET /love/config/list 回 configKey→configValue 映射）零错误路径
-- （空表/全停用回空 Map）；admin 全量 CRUD（config_key 唯一性 service 层校验）。
-- 无 DB uk（逻辑删除行残留 + uk 会挡同键重建，唯一性靠 service selectByKey 校验，
-- yudao infra_config 同哲学）；idx_config_key 普通索引（App 全量拉取 + admin 键筛选）。
-- 首个配置项 = 客服微信号（App 我的页「帮助与客服」行复制引导，原写死占位值迁入配置）。
-- 上线顺序必须 SQL 先于新代码（新代码 DO 映射依赖本表）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `love_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置编号',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称（管理端展示用，如「客服微信号」）',
  `config_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键（App 读取契约：小写字母开头+小写字母数字下划线，如 customer_service_wechat）',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值（App 按键读取；停用/缺键端上走内置兜底值）',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注（用途与消费位置；提醒 config_key 是 App 契约勿随意改）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行不下发 App）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_config_key`(`config_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务配置表（love 业务 KV，B 端配置，App 只读）';

-- ----------------------------
-- 种子配置（M23，1 行：客服微信号——原 MinePage 写死占位值迁入配置，端上同值兜底双保险）；
-- 幂等：已存在同 config_key 且 deleted=0 行则跳过（M17 种子模板同款）
-- ----------------------------
INSERT INTO `love_config` (`name`, `config_key`, `config_value`, `remark`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '客服微信号' AS `name`, 'customer_service_wechat' AS `config_key`, 'lovebaby-helper' AS `config_value`,
         '我的页「帮助与客服」行点击复制并引导添加微信；config_key 为 App 契约勿随意改动' AS `remark`,
         0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_config` d WHERE d.`deleted` = b'0' AND d.`config_key` = seed.`config_key`
);

-- ----------------------------
-- 菜单/按钮权限 SQL（M23，「情侣业务」目录下第十一个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M17 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M17 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:config:query', 'love:config:create', 'love:config:update', 'love:config:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveConfig';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单十一：业务配置（component_name 唯一 = LoveConfig，M17 实况同款无 Menu 后缀）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('业务配置', '', 2, 11, @loveDirectoryId, 'config', 'ep:setting', 'love/config/index', 'LoveConfig', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('配置查询', 'love:config:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('配置新增', 'love:config:create', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('配置编辑', 'love:config:update', 3, 3, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('配置删除', 'love:config:delete', 3, 4, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- M24 段：love_moment 加首图宽高两列（2026-10-22，相册页瀑布流 M24）
-- 端上 MomentCard 图块按「首图压缩后宽高比」自适应高度（瀑布流错落），宽高取自
-- App 端 Glide 压缩（centerInside + EXIF 自动矫正）后的 Bitmap，随 uploadImage
-- 回包顶层 width/height 键带回 → 发布端点透传落库。
-- 0 = 未知（存量行 / 老版 App / 非常规链路）→ 端上降级 140dp 定高（M9 现状），
-- 双向兼容零迁移：旧数据列默认 0、老版 App 不传、新 App 读 0 走降级。
-- 本段追加于 M23 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- 上线顺序沿 M15 段惯例 SQL 先于新代码（本段列带 DEFAULT 0，实际双向兼容）。
-- ============================================================================
SET @m24_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_moment' AND COLUMN_NAME = 'image_width');
SET @m24_ddl := IF(@m24_col = 0,
    'ALTER TABLE `love_moment` ADD COLUMN `image_width` int NOT NULL DEFAULT 0 COMMENT ''首图压缩后宽（px，EXIF 矫正；M24；0=未知→端上瀑布流降级 140dp 定高）''',
    'SELECT 1');
PREPARE m24_stmt FROM @m24_ddl;
EXECUTE m24_stmt;
DEALLOCATE PREPARE m24_stmt;

SET @m24_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_moment' AND COLUMN_NAME = 'image_height');
SET @m24_ddl := IF(@m24_col = 0,
    'ALTER TABLE `love_moment` ADD COLUMN `image_height` int NOT NULL DEFAULT 0 COMMENT ''首图压缩后高（px，EXIF 矫正；M24；0=未知→端上瀑布流降级 140dp 定高）''',
    'SELECT 1');
PREPARE m24_stmt FROM @m24_ddl;
EXECUTE m24_stmt;
DEALLOCATE PREPARE m24_stmt;

-- ============================================================================
-- M25 段：字典数据服务端化（表情包 + 心愿模板两表 + love_config 三键，2026-10-23）
-- 端上四处写死字典改后台可配、接口下发（「零死数据」裁决）：
--   ① love_emoji：聊天表情包字典（原端上 ChatStickers 24 枚）+ 分类；App 表情面板分类 tab
--      动态聚合；点选仍插输入框（TEXT 链，emoji 字面量随消息全文落库，服务端不校验表情）；
--   ② love_wish_template：心愿墙挑选模板（原端上 allWishes 24 条：分类/文案/渐变双色/图标）；
--      id 即 App 送出 wishKey——kind=2 心愿消息为快照式四字段上送（服务端不校验、气泡渲染
--      不查字典），删/停模板不影响历史消息，双向兼容；
--   ③ love_config 三 key：quick_stickers（聊天快捷贴纸条文案）/ mood_emoji_presets（心情新增
--      浮层 emoji 预设）/ mood_color_presets（心情新增浮层色板）——JSON 字符串数组，端上解析
--      失败或缺 key 走内置兜底（兜底值与种子同值）。
-- 心情主链（couple 私有字典 love_mood_option）M3.5 已服务端化，本段不涉及。
-- 端上三级兜底：新端点空表/全停用/拉取失败回落端上内置字典，UI 永不空白；
-- 老版 App 不请求新端点，服务端先上不影响老版。上线顺序 SQL 先于新代码（Mapper 依赖两表）。
-- 本段追加于 M24 段之后；历史段一字不动；本文件只追加、不修改历史段，保持可重放。
-- ============================================================================

-- ----------------------------
-- Table structure for love_emoji（聊天表情包字典表，M25）
-- 说明：B 端配置 App 只读（同 love_inspiration 路线，继承 BaseDO 逻辑删除）；
-- 无 uk（emoji 内容无唯一性约束，同分类重复靠运营自律，重放种子靠 NOT EXISTS 判重）；
-- idx_category 覆盖 App 聚合 tab 与 admin 分类筛选（category + sort + id 稳定序）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `love_emoji`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '表情编号',
  `category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分类（App 端动态聚合 tab；建议：爱意/情绪/手势/庆祝，可自由新增；空串归「常用」桶）',
  `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'emoji 字符（App 点选直插输入框，随消息全文落库快照）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示顺序（同分类内 sort asc, id asc，越小越靠前）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行不出现在 App）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC, `sort` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '聊天表情包字典表（情侣业务，B 端配置，App 只读，M25）';

-- ----------------------------
-- Table structure for love_wish_template（心愿墙挑选模板表，M25）
-- 说明：B 端配置 App 只读（love_inspiration 同路线）；id 即 App 送出 wishKey（快照式，
-- 历史消息渲染不依赖本表，删/停模板零影响）；无 uk（title 重复靠运营自律）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `love_wish_template`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板编号（=App 送出 wishKey；快照式：历史消息渲染不依赖本表）',
  `category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分类（App 端动态聚合 chips；建议：想亲亲/想抱抱/想吃点/想念/撒个娇/和好，可自由新增）',
  `title` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题（气泡主文案 + 卡片首行，随消息快照上送）',
  `subtitle` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '副标题（卡片第二行，仅挑选列表展示，不随消息上送）',
  `color_start` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#FF8FA3' COMMENT '卡片渐变起始色 hex #RRGGBB（sendWishes 上送的主色）',
  `color_end` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#FF5A8C' COMMENT '卡片渐变结束色 hex #RRGGBB',
  `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Heart' COMMENT '图标（端上 AppIconType 枚举 name；未知名端上兜底 Heart）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示顺序（sort asc, id asc）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行不出现在 App）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC, `sort` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '心愿墙挑选模板表（情侣业务，B 端配置，App 只读，M25）';

-- ----------------------------
-- 种子（M25，两表各 24 行，幂等：NOT EXISTS 判重——emoji 按 category+emoji、wish 按 title；
-- 内容 = 原端上 ChatStickers / allWishes 字典一比一迁入，替换前后 App 观感一致；
-- INSERT...SELECT...UNION ALL 模板同 M17 种子段；creator='1'、NOW()、不写显式 id）
-- ----------------------------
INSERT INTO `love_emoji` (`category`, `emoji`, `sort`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  -- 爱意 8（原 ORDER 前段 + rose/star/moon，组内沿用原展示相对顺序，下同）
  SELECT '爱意' AS `category`, '🤗' AS `emoji`, 1 AS `sort`, 0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
  UNION ALL SELECT '爱意', '😘', 2, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '爱意', '🥰', 3, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '爱意', '❤️', 4, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '爱意', '😊', 5, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '爱意', '🌹', 6, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '爱意', '⭐', 7, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '爱意', '🌙', 8, 0, '1', NOW(), '1', NOW(), b'0'
  -- 情绪 6
  UNION ALL SELECT '情绪', '😂', 9, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '情绪', '😭', 10, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '情绪', '😠', 11, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '情绪', '😲', 12, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '情绪', '🤔', 13, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '情绪', '😴', 14, 0, '1', NOW(), '1', NOW(), b'0'
  -- 手势 6
  UNION ALL SELECT '手势', '👍', 15, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '手势', '👌', 16, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '手势', '👏', 17, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '手势', '👋', 18, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '手势', '🙏', 19, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '手势', '💪', 20, 0, '1', NOW(), '1', NOW(), b'0'
  -- 庆祝 4
  UNION ALL SELECT '庆祝', '🎉', 21, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '庆祝', '🎂', 22, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '庆祝', '🐶', 23, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '庆祝', '👻', 24, 0, '1', NOW(), '1', NOW(), b'0'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_emoji` d WHERE d.`deleted` = b'0' AND d.`category` = seed.`category` AND d.`emoji` = seed.`emoji`
);

INSERT INTO `love_wish_template` (`category`, `title`, `subtitle`, `color_start`, `color_end`, `icon`, `sort`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  -- 想亲亲 4（原 catColors「想亲亲」双色 FF9AC4/FF5A8C）
  SELECT '想亲亲' AS `category`, '求亲亲' AS `title`, 'mua 一口就跑' AS `subtitle`, '#FF9AC4' AS `color_start`, '#FF5A8C' AS `color_end`, 'Heart' AS `icon`, 1 AS `sort`, 0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
  UNION ALL SELECT '想亲亲', '贴贴脸', '脸贴脸蹭蹭', '#FF9AC4', '#FF5A8C', 'Star', 2, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想亲亲', '早安吻', '叫醒服务', '#FF9AC4', '#FF5A8C', 'Bell', 3, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想亲亲', '晚安吻', '睡前专属', '#FF9AC4', '#FF5A8C', 'Moon', 4, 0, '1', NOW(), '1', NOW(), b'0'
  -- 想抱抱 4（FFB27A/FF8A5C）
  UNION ALL SELECT '想抱抱', '充电式抱抱', '充到 100%', '#FFB27A', '#FF8A5C', 'Heart', 5, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想抱抱', '熊抱', '抱到悬浮', '#FFB27A', '#FF8A5C', 'Star', 6, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想抱抱', '背后抱', '突然袭击', '#FFB27A', '#FF8A5C', 'Bell', 7, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想抱抱', '举高高', '转圈圈', '#FFB27A', '#FF8A5C', 'Sun', 8, 0, '1', NOW(), '1', NOW(), b'0'
  -- 想吃点 4（FFD98E/FFB25C）
  UNION ALL SELECT '想吃点', '蛋糕一块', '草莓味的', '#FFD98E', '#FFB25C', 'Heart', 9, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想吃点', '奶茶三分甜', '去冰加珍珠', '#FFD98E', '#FFB25C', 'Star', 10, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想吃点', '火锅一顿', '鸳鸯锅安排', '#FFD98E', '#FFB25C', 'Sun', 11, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想吃点', '冰淇淋', '甜到心里', '#FFD98E', '#FFB25C', 'Moon', 12, 0, '1', NOW(), '1', NOW(), b'0'
  -- 想念 4（8FB8FF/6C8BFF）
  UNION ALL SELECT '想念', '想你了', '超想那种', '#8FB8FF', '#6C8BFF', 'Heart', 13, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想念', '视频一下', '看看彼此', '#8FB8FF', '#6C8BFF', 'Bell', 14, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想念', '来见我', '立刻马上', '#8FB8FF', '#6C8BFF', 'Star', 15, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '想念', '写封情书', '手写才有诚意', '#8FB8FF', '#6C8BFF', 'Moon', 16, 0, '1', NOW(), '1', NOW(), b'0'
  -- 撒个娇 4（C09AFF/9B6BFF）
  UNION ALL SELECT '撒个娇', '夸夸我', '彩虹屁来十句', '#C09AFF', '#9B6BFF', 'Star', 17, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '撒个娇', '捏捏脸', '轻轻的那种', '#C09AFF', '#9B6BFF', 'Heart', 18, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '撒个娇', '陪我看剧', '要抱着看', '#C09AFF', '#9B6BFF', 'Moon', 19, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '撒个娇', '哄睡服务', '讲个故事', '#C09AFF', '#9B6BFF', 'Bell', 20, 0, '1', NOW(), '1', NOW(), b'0'
  -- 和好 4（9BEBB5/4EB087）
  UNION ALL SELECT '和好', '抱抱和好', '不许生气了', '#9BEBB5', '#4EB087', 'Heart', 21, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '和好', '请你吃饭', '你选地方', '#9BEBB5', '#4EB087', 'Sun', 22, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '和好', '看场电影', '我买爆米花', '#9BEBB5', '#4EB087', 'Star', 23, 0, '1', NOW(), '1', NOW(), b'0'
  UNION ALL SELECT '和好', '散步谈心', '手牵手那种', '#9BEBB5', '#4EB087', 'Moon', 24, 0, '1', NOW(), '1', NOW(), b'0'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_wish_template` d WHERE d.`deleted` = b'0' AND d.`title` = seed.`title`
);

-- ----------------------------
-- love_config 种子（M25，3 行：快捷贴纸条 + 心情两预设，幂等按 config_key，M23 种子模板）；
-- value 均为 JSON 字符串数组（≤100 字符，varchar(500) 充裕）；端上解析失败/缺 key 走内置兜底
-- ----------------------------
INSERT INTO `love_config` (`name`, `config_key`, `config_value`, `remark`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '聊天快捷贴纸条' AS `name`, 'quick_stickers' AS `config_key`, '["贴贴","mua~","想你了","举高高","晚安鸭"]' AS `config_value`,
         '聊天输入区顶部快捷贴纸条文案（M25，原端上写死迁入）；JSON 字符串数组；缺行/停用/解析失败端上走内置兜底 5 条' AS `remark`,
         0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_config` d WHERE d.`deleted` = b'0' AND d.`config_key` = seed.`config_key`
);

INSERT INTO `love_config` (`name`, `config_key`, `config_value`, `remark`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '心情emoji预设' AS `name`, 'mood_emoji_presets' AS `config_key`, '["😄","😊","🥰","😌","😎","🥺","😭","😤","😠","😴","🤒","🥳"]' AS `config_value`,
         '心情新增浮层 emoji 预设（M25，原端上写死迁入）；JSON 字符串数组；⚠ 全部 Unicode 11 及以前字形（老系统字体可渲染），勿随手加新版；解析失败端上走内置兜底 12 枚' AS `remark`,
         0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_config` d WHERE d.`deleted` = b'0' AND d.`config_key` = seed.`config_key`
);

INSERT INTO `love_config` (`name`, `config_key`, `config_value`, `remark`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '心情色板预设' AS `name`, 'mood_color_presets' AS `config_key`, '["#FFB93C","#FF8FA3","#FF5A5A","#FF8A5C","#4EB087","#6C8BFF","#9B6BFF","#8FA3BF"]' AS `config_value`,
         '心情新增浮层色板（M25，原端上写死迁入）；JSON 字符串数组（#RRGGBB hex）；解析失败端上走内置兜底 8 色' AS `remark`,
         0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_config` d WHERE d.`deleted` = b'0' AND d.`config_key` = seed.`config_key`
);

-- ----------------------------
-- 菜单/按钮权限 SQL（M25，「情侣业务」目录下第十二/十三个管理页）
-- 挂接：目录 id 按 type+name 回查（同 M3.5/M17 段理由，不依赖前段 LAST_INSERT_ID）；
-- 幂等：按钮（按 permission）→ 管理菜单（按 component_name）先行 DELETE，先子后父（M-3 同款）。
-- ----------------------------
DELETE FROM `system_menu` WHERE `permission` IN ('love:emoji:query', 'love:emoji:create', 'love:emoji:update', 'love:emoji:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveEmoji';
DELETE FROM `system_menu` WHERE `permission` IN ('love:wish-template:query', 'love:wish-template:create', 'love:wish-template:update', 'love:wish-template:delete');
DELETE FROM `system_menu` WHERE `component_name` = 'LoveWishTemplate';

SELECT @loveDirectoryId := `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1;

-- 管理菜单十二：表情包管理（component_name 唯一 = LoveEmoji，M17 实况同款无 Menu 后缀）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('表情包管理', '', 2, 12, @loveDirectoryId, 'emoji', 'ep:sugar', 'love/emoji/index', 'LoveEmoji', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('表情查询', 'love:emoji:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('表情新增', 'love:emoji:create', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('表情编辑', 'love:emoji:update', 3, 3, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('表情删除', 'love:emoji:delete', 3, 4, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 管理菜单十三：心愿模板管理（component_name 唯一 = LoveWishTemplate；多词权限先例 mood-option-default）
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('心愿模板管理', '', 2, 13, @loveDirectoryId, 'wishTemplate', 'ep:present', 'love/wishTemplate/index', 'LoveWishTemplate', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
SELECT @parentId := LAST_INSERT_ID();
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('模板查询', 'love:wish-template:query', 3, 1, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('模板新增', 'love:wish-template:create', 3, 2, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('模板编辑', 'love:wish-template:update', 3, 3, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES ('模板删除', 'love:wish-template:delete', 3, 4, @parentId, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
