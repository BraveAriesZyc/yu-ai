-- ============================================================================
-- 拆分 2/3:love 业务表结构(20 张,M1→M29 全量终态)
-- 拆分自 2026-10-26.sql(源文件保留未动)
-- 结构核验:✓ 20 张全对得上 love_baby_server/sql/mysql/love.sql 全量重放终态
--   (基线 CREATE + 追加 ALTER 段 + M15/M16/M24 幂等预编译段 + 索引段,逐一归一比对)。
--   两处注释级瑕疵(不影响结构与数据):
--   ① love_message.kind 注释未含 M23 补记(M23 本为零 DDL,仅文档级差异);
--   ② love_mood_option_default.emoji 注释中 😄 存成 ?(建表执行连接未走 utf8mb4)。
--   导入顺序:先本文件与拆分1,最后拆分3 数据。
-- ============================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for love_anniversary
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_anniversary`;
CREATE TABLE `love_anniversary`  (
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
  INDEX `idx_couple`(`couple_id` ASC) USING BTREE,
  INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '纪念日表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_battery_snapshot
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_battery_snapshot`;
CREATE TABLE `love_battery_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '快照编号',
  `user_id` bigint NOT NULL COMMENT '用户编号（uk 一人一行，upsert 语义）',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（重绑后上报刷新到当前 couple）',
  `battery_level` tinyint NOT NULL DEFAULT 0 COMMENT '电量百分比 0-100（-1=未知）',
  `charging_status` tinyint NOT NULL DEFAULT 0 COMMENT '充电状态（0=未充电 1=充电中 2=已充满）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（即 reportTime）',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电量快照表（情侣业务，每用户一行 upsert）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_checkin
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_checkin`;
CREATE TABLE `love_checkin`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日打卡表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_config
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_config`;
CREATE TABLE `love_config`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务配置表（love 业务 KV，B 端配置，App 只读）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_couple
-- ----------------------------
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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '情侣关系表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_emoji
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_emoji`;
CREATE TABLE `love_emoji`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '聊天表情包字典表（情侣业务，B 端配置，App 只读，M25）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_inspiration
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_inspiration`;
CREATE TABLE `love_inspiration`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 64 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '恋爱灵感表（情侣业务，B 端配置，App 只读）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_location_place
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_location_place`;
CREATE TABLE `love_location_place`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地点记录编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（重绑后新 couple 起新流，旧流随 30 天窗口消亡）',
  `user_id` bigint NOT NULL COMMENT '上报用户编号',
  `lat` decimal(10, 6) NOT NULL COMMENT '纬度（GCJ-02，-90.000000 ~ 90.000000）',
  `lng` decimal(10, 6) NOT NULL COMMENT '经度（GCJ-02，-180.000000 ~ 180.000000）',
  `province` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '省（端上 regeo 解析，直辖市可为空）',
  `city` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '市（端上 regeo 解析，直辖市可为空）',
  `district` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '区/县（端上 regeo 解析，可空）',
  `address` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '完整地址（regeo formatted_address，可空）',
  `poi` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地名/POI（regeo pois[0].name，可空）',
  `report_time` datetime NOT NULL COMMENT '到达时刻（端上 fix 时间，非入库时间）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_user_time`(`couple_id` ASC, `user_id` ASC, `report_time` ASC) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `report_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程地点表（情侣业务，30min 粒度地点记录，append-only，30 天物理保留）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_location_snapshot
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_location_snapshot`;
CREATE TABLE `love_location_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '位置点编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（重绑后新 couple 起新流，旧流随 30 天窗口消亡）',
  `user_id` bigint NOT NULL COMMENT '上报用户编号',
  `lat` decimal(10, 6) NOT NULL COMMENT '纬度（GCJ-02，-90.000000 ~ 90.000000）',
  `lng` decimal(10, 6) NOT NULL COMMENT '经度（GCJ-02，-180.000000 ~ 180.000000）',
  `accuracy` int NOT NULL DEFAULT 0 COMMENT '定位精度（米，0=未知）',
  `report_time` datetime NOT NULL COMMENT '定位时刻（端上 fix 时间，非入库时间）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_user_time`(`couple_id` ASC, `user_id` ASC, `report_time` ASC) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `report_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 516 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '位置点表（情侣业务，append-only 轨迹，30 天物理保留）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_message
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_message`;
CREATE TABLE `love_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（增量扫按 couple_id + id 游标）',
  `sender_id` bigint NOT NULL COMMENT '发送方用户编号',
  `receiver_id` bigint NOT NULL COMMENT '接收方用户编号（快照，解绑后审计归对）',
  `kind` tinyint NOT NULL COMMENT '消息类型（1文本悄悄话 2心愿消息 3图片消息）',
  `content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本内容，或心愿文案快照，或图片完整 URL（kind=3，infra 文件服务上传返回）',
  `wish_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心愿 key 快照（kind=2 时有值）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心愿颜色快照（hex，kind=2 时有值）',
  `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心愿图标快照（icon name，kind=2 时有值）',
  `audit_status` tinyint NOT NULL DEFAULT 1 COMMENT '审核状态（0待审 1通过 2命中；首期恒写 1 兜底）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `recalled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已撤回（M12 墓碑标记：撤回不删行，content 原文留档审计；与 deleted 逻辑删独立）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_time`(`couple_id` ASC, `id` ASC) USING BTREE,
  INDEX `idx_couple_kind`(`couple_id` ASC, `kind` ASC) USING BTREE,
  INDEX `idx_sender_id`(`sender_id` ASC) USING BTREE,
  INDEX `idx_receiver_id`(`receiver_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '悄悄话/心愿消息表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_moment
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_moment`;
CREATE TABLE `love_moment`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '瞬间编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（couple 共享，列表按此过滤；解绑重绑后旧瞬间留历史值）',
  `user_id` bigint NOT NULL COMMENT '发布用户编号（仅上传者本人可删）',
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片完整 URL（infra 文件服务上传返回，@PermitAll 匿名可读）',
  `text` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '配文（可选，≤64 字，空串 = 无配文）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `image_urls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '多图 URL JSON 数组字符串（M15，如 [\"u1\",\"u2\"]，≤9 元素；image_url 列保留为首图冗余，admin 零改动）',
  `image_width` int NOT NULL DEFAULT 0 COMMENT '首图压缩后宽（px，EXIF 矫正；M24；0=未知→端上瀑布流降级 140dp 定高）',
  `image_height` int NOT NULL DEFAULT 0 COMMENT '首图压缩后高（px，EXIF 矫正；M24；0=未知→端上瀑布流降级 140dp 定高）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_id`(`couple_id` ASC, `id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '相册瞬间表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_moment_like
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_moment_like`;
CREATE TABLE `love_moment_like`  (
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
  UNIQUE INDEX `uk_moment_user`(`moment_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_moment_id`(`moment_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '相册瞬间点赞表（情侣业务，M15）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_mood_option
-- ----------------------------
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
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '心情选项表（情侣私有字典）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_mood_option_default
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_mood_option_default`;
CREATE TABLE `love_mood_option_default`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '默认选项编号',
  `label` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '心情名（如 开心）',
  `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表情（utf8mb4 文本，如 ?）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '颜色 hex（如 #FFB93C，与心愿消息 color 同款约定）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示顺序（播种行继承此值；越小越靠前）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行不参与绑定播种）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '心情默认选项模板表（情侣业务，B 端配置）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_mood_record
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_mood_record`;
CREATE TABLE `love_mood_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '心情记录编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号',
  `user_id` bigint NOT NULL COMMENT '记录用户编号',
  `mood_date` date NOT NULL COMMENT '心情日期（一天一条，uk_user_date 组成列）',
  `mood_type` tinyint NULL DEFAULT NULL COMMENT '（废弃）旧 1-5 心情枚举，仅存量行有值；M3.5 起写 NULL（新值 = option_id + 快照列）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `option_id` bigint NULL DEFAULT NULL COMMENT '心情选项编号（love_mood_option.id，M3.5 起写入）',
  `mood_label` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '心情名快照（记录时点，删除选项后历史渲染不依赖字典）',
  `emoji` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '表情快照（记录时点）',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '颜色 hex 快照（记录时点）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_date`(`user_id` ASC, `mood_date` ASC) USING BTREE,
  INDEX `idx_couple_month`(`couple_id` ASC, `mood_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日心情记录表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_share_setting
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_share_setting`;
CREATE TABLE `love_share_setting`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设置编号',
  `user_id` bigint NOT NULL COMMENT '用户编号（uk 一人一行）',
  `battery_share` bit(1) NOT NULL DEFAULT b'1' COMMENT '电量共享开关（默认开，低敏）',
  `location_share` bit(1) NOT NULL DEFAULT b'0' COMMENT '位置共享开关（默认关，强隐私 §12.4）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '共享开关表（情侣业务，每用户一行懒创建）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_todo
-- ----------------------------
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
  INDEX `idx_couple`(`couple_id` ASC) USING BTREE,
  INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '待办表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_user
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_user`;
CREATE TABLE `love_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '手机号码（M3 实施修正 16→32：容纳 admin 删除时 concat(mobile,#,id) 改写释放 uk_mobile 唯一键，id 增长不爆列）',
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
  `chat_read_cursor` bigint NOT NULL DEFAULT 0 COMMENT '聊天已读游标（已读到的最大 love_message.id；0=未读过。全局消息 id 只增不减，解绑重绑新 couple 的消息 id 必大于旧游标）',
  `sex` tinyint NULL DEFAULT NULL COMMENT '性别 0-女 1-男 NULL-未设置（与系统字典语义相反）',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `signature` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '个性签名',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_mobile`(`mobile` ASC) USING BTREE,
  INDEX `idx_invite_code`(`invite_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'C 端用户表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_wish
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_wish`;
CREATE TABLE `love_wish`  (
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
  INDEX `idx_couple_id`(`couple_id` ASC, `id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '恋爱清单表（情侣业务）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_wish_template
-- ----------------------------
-- ----------------------------
DROP TABLE IF EXISTS `love_wish_template`;
CREATE TABLE `love_wish_template`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '心愿墙挑选模板表（情侣业务，B 端配置，App 只读，M25）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_daily_question
-- ----------------------------
-- ----------------------------
-- M32 每日一问题库表（2026-10-26 追加）：B 端全 CRUD（LoveDailyQuestionController）。
-- 无 uk：内容级重复靠运营自律（同 wish_template 惯例）；改题/删题不影响历史——
-- 当日题以 love_daily_question_day 快照行落地（D-19a）。
DROP TABLE IF EXISTS `love_daily_question`;
CREATE TABLE `love_daily_question`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目编号',
  `content` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容（≤200 字；day 表持快照，改题不影响已出历史）',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分类（自由文本不做字典，建议：情感/日常/回忆/脑洞/未来；空串=未分类）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用；停用行退出当日轮换池，已出过的天走 day 快照不受影响）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日一问题库表（情侣业务，B 端全 CRUD，M32）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_daily_question_day
-- ----------------------------
-- ----------------------------
-- M32 每日一问当日题表（2026-10-26 追加）：懒生成 get-or-create（无定时任务），按启用池
-- id asc 稳定序 + 日期取模轮换取题；uk_question_date 一天一行。快照列（D-19a）使题库
-- 增删改/停用不影响已出过的天，历史永不漂移。无删除路径。
DROP TABLE IF EXISTS `love_daily_question_day`;
CREATE TABLE `love_daily_question_day`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '当日题编号',
  `question_date` date NOT NULL COMMENT '题目日期（uk 一天一行；懒生成，同一天恒同题）',
  `question_id` bigint NOT NULL COMMENT '题库题目编号（溯源；展示一律以快照列为准）',
  `content` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容快照（D-19a：生成即与题库解耦，改题/删题不影响历史）',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分类快照（同上）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_question_date`(`question_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日一问当日题表（懒生成快照行，无删除路径，M32）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for love_daily_answer
-- ----------------------------
-- ----------------------------
-- M32 每日一问作答表（2026-10-26 追加）：覆盖写 + 揭晓锁（双方都提交即锁定，D-21）。
-- uk_user_question 一人一题一条；answer_date 冗余恒 = day.question_date，供
-- couple+日期区间查询走 idx_couple_date。无删除路径、无补答（只能答当天）。
DROP TABLE IF EXISTS `love_daily_answer`;
CREATE TABLE `love_daily_answer`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '作答编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（覆盖写时刷新；解绑重绑后当日改答案同步新 couple）',
  `user_id` bigint NOT NULL COMMENT '作答人用户编号',
  `question_id` bigint NOT NULL COMMENT '题库题目编号（与 day.question_id 同源）',
  `answer_date` date NOT NULL COMMENT '作答日期（冗余恒 = day.question_date，供 couple+日期区间查询）',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '答案内容（≤500 字，端上渲染层与服务端 @Length 双保险）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_question`(`user_id` ASC, `question_id` ASC) USING BTREE,
  INDEX `idx_couple_date`(`couple_id` ASC, `answer_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '每日一问作答表（覆盖写+揭晓锁，无删除路径，M32）' ROW_FORMAT = Dynamic;

