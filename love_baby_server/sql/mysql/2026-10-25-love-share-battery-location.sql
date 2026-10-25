-- ----------------------------------------------------------------------------
-- M26 增量：电量互看 + 位置/轨迹共享（情侣业务）
-- 本文件为独立增量（按 2026-10-23 约定：日期+任务名，存量库只执行新增量，不再全量重放 love.sql）；
-- love.sql 基线尾部同步追加本段（新库一次建成），历史段一字不动。
-- 段内幂等：CREATE TABLE IF NOT EXISTS（M8 段起惯例）。
-- 本期纯 app-api（anniversary 前例），无 system_menu 段。
-- ============================================================================

-- ----------------------------
-- 表1：love_battery_snapshot 电量快照表
-- 语义：每用户一行 upsert 快照（永不删行、无 delete 通道）；
--       update_time 即上报时间（不建专列，VO 暴露为 reportTime）；
--       解绑/重绑后行保留，couple_id 在下次上报时刷新到当前 couple（checkin「当日再打卡刷新」同口径）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `love_battery_snapshot`  (
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电量快照表（情侣业务，每用户一行 upsert）';

-- ----------------------------
-- 表2：love_location_snapshot 位置点表
-- 语义：append-only 轨迹点（无 uk、无更新通道）；
--       坐标统一存 GCJ-02（高德系，端上 WGS-84→GCJ-02 纠偏后上传，staticmap/regeo 直接可用）；
--       30 天保留策略：report 时顺带物理清除该用户 30 天前旧点（Mapper 手写 @Delete 物理 SQL——
--       BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删，CheckinMapper.reviveDeletedByUserIdAndDate 同款先例）；
--       双索引：idx_couple_user_time 供轨迹区间扫，idx_user_time 供按用户清理（前者最左前缀是 couple_id，按 user 扫不动）；
--       解绑/重绑：历史点 couple_id 留旧值，trail 按 (couple_id, user_id) 过滤天然隐没，随 30 天窗口物理消亡（D-11）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `love_location_snapshot`  (
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '位置点表（情侣业务，append-only 轨迹，30 天物理保留）';

-- ----------------------------
-- 表3：love_share_setting 共享开关表
-- 语义：每用户一行开关（懒创建：无行回默认值，首次 update 才落行）；
--       电量默认开（低敏），位置默认关（§12.4 强隐私：默认不采、双方显式开关）；
--       开关关 = 服务端拒收上报 + 对方查询只见 shareEnabled:false，双端同断。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `love_share_setting`  (
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '共享开关表（情侣业务，每用户一行懒创建）';
