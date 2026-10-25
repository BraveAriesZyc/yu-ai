-- ----------------------------------------------------------------------------
-- M28 增量：行动轨迹地点记录（情侣业务）
-- 本文件为独立增量（按 2026-10-23 约定：日期+任务名，存量库只执行新增量，不再全量重放 love.sql）；
-- love.sql 基线尾部同步追加本段（新库一次建成），历史段一字不动。
-- 段内幂等：CREATE TABLE IF NOT EXISTS（M8 段起惯例）。
-- 本期纯 app-api（anniversary 前例），无 system_menu 段。
-- ============================================================================

-- ----------------------------
-- 表1：love_location_place 行程地点表
-- 语义：地点粒度的 append-only 记录（30min 粒度 + 换地即记，无 uk、无更新通道）；
--       由 location/report 顺路落库：距本人最新 place ≥30min 或位移 ≥300m 任一成立才插行
--       （服务端时钟与库内数据判定，防多端/绕过客户端）；snapshot 吞点分支（3s 限频）
--       不落 place，3s << 30min 无漏记影响；
--       坐标统一存 GCJ-02（随 love_location_snapshot 同源上报，staticmap/marker 直接可用）；
--       省/市/区/地名/POI 由端上 regeo 解析随点上报（服务端无高德 key 不做逆地理），
--       解析失败可空，UI 降级显坐标；直辖市 city 可能空（高德 addressComponent city=[]）；
--       30 天保留策略：report 时顺带物理清除该用户 30 天前旧行（Mapper 手写 @Delete 物理
--       SQL——BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删，love_location_snapshot 同款）；
--       双索引：idx_couple_user_time 供列表区间扫，idx_user_time 供节流查最新 + 按用户清理
--       （前者最左前缀是 couple_id，按 user 扫不动）；
--       解绑/重绑：历史行 couple_id 留旧值，列表按 (couple_id, user_id) 过滤天然隐没，
--       随 30 天窗口物理消亡（D-11，love_location_snapshot 同口径）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `love_location_place`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地点记录编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号（重绑后新 couple 起新流，旧流随 30 天窗口消亡）',
  `user_id` bigint NOT NULL COMMENT '上报用户编号',
  `lat` decimal(10, 6) NOT NULL COMMENT '纬度（GCJ-02，-90.000000 ~ 90.000000）',
  `lng` decimal(10, 6) NOT NULL COMMENT '经度（GCJ-02，-180.000000 ~ 180.000000）',
  `province` varchar(32) NULL DEFAULT NULL COMMENT '省（端上 regeo 解析，直辖市可为空）',
  `city` varchar(32) NULL DEFAULT NULL COMMENT '市（端上 regeo 解析，直辖市可为空）',
  `district` varchar(32) NULL DEFAULT NULL COMMENT '区/县（端上 regeo 解析，可空）',
  `address` varchar(128) NULL DEFAULT NULL COMMENT '完整地址（regeo formatted_address，可空）',
  `poi` varchar(64) NULL DEFAULT NULL COMMENT '地名/POI（regeo pois[0].name，可空）',
  `report_time` datetime NOT NULL COMMENT '到达时刻（端上 fix 时间，非入库时间）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_couple_user_time`(`couple_id` ASC, `user_id` ASC, `report_time` ASC) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `report_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程地点表（情侣业务，30min 粒度地点记录，append-only，30 天物理保留）';
