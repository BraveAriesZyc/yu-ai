# -*- coding: utf-8 -*-
"""M23 DDL 落库脚本:love_config 建表 + 种子配置(客服微信号) + 菜单/按钮权限。
幂等可重放(与 love.sql M23 段口径一致)。用法: python -I m23_apply_sql.py"""
import sys
sys.stdout.reconfigure(encoding="utf-8")
import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="123456",
                       database="yudao", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

# 1) 建表(IF NOT EXISTS 幂等)
cur.execute("""CREATE TABLE IF NOT EXISTS `love_config`  (
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务配置表（love 业务 KV，B 端配置，App 只读）'""")
print("[ok] love_config 表就绪")

# 2) 种子配置(同 config_key 幂等)
cur.execute("""INSERT INTO `love_config` (`name`, `config_key`, `config_value`, `remark`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (
  SELECT '客服微信号' AS `name`, 'customer_service_wechat' AS `config_key`, 'lovebaby-helper' AS `config_value`,
         '我的页「帮助与客服」行点击复制并引导添加微信；config_key 为 App 契约勿随意改动' AS `remark`,
         0 AS `status`, '1' AS `creator`, NOW() AS `create_time`, '1' AS `updater`, NOW() AS `update_time`, b'0' AS `deleted`
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `love_config` d WHERE d.`deleted` = b'0' AND d.`config_key` = seed.`config_key`
)""")
print("[ok] 种子 customer_service_wechat 落库(命中 0 行=已存在跳过):", cur.rowcount)

# 3) 菜单/按钮权限(先子后父 DELETE 幂等重建)
cur.execute("DELETE FROM `system_menu` WHERE `permission` IN ('love:config:query', 'love:config:create', 'love:config:update', 'love:config:delete')")
cur.execute("DELETE FROM `system_menu` WHERE `component_name` = 'LoveConfig'")
cur.execute("SELECT `id` FROM `system_menu` WHERE `type` = 1 AND `name` = '情侣业务' AND `deleted` = b'0' LIMIT 1")
row = cur.fetchone()
assert row, "「情侣业务」目录不存在,先跑历史段"
love_dir_id = row[0]

menu_sql = ("INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, "
            "`component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, "
            "`create_time`, `updater`, `update_time`, `deleted`) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')")
cur.execute(menu_sql, ('业务配置', '', 2, 11, love_dir_id, 'config', 'ep:setting', 'love/config/index', 'LoveConfig'))
parent_id = cur.lastrowid
for i, (label, perm) in enumerate([('配置查询', 'love:config:query'), ('配置新增', 'love:config:create'),
                                   ('配置编辑', 'love:config:update'), ('配置删除', 'love:config:delete')], start=1):
    cur.execute(menu_sql, (label, perm, 3, i, parent_id, '', '', '', None))
print("[ok] 菜单「业务配置」+ 四按钮权限就绪(parent_id=%s, 目录=%s)" % (parent_id, love_dir_id))

# 验证
cur.execute("SELECT `id`, `name`, `config_key`, `config_value`, `status` FROM `love_config` WHERE `deleted` = b'0'")
print("[verify] love_config 行:", cur.fetchall())
cur.execute("SELECT INDEX_NAME, NON_UNIQUE FROM information_schema.STATISTICS "
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'love_config' GROUP BY INDEX_NAME, NON_UNIQUE")
print("[verify] love_config 索引(1=普通索引,无 uk=service 层唯一性):", cur.fetchall())
cur.execute("SELECT COUNT(*) FROM `system_menu` WHERE `component_name` = 'LoveConfig' AND `deleted` = b'0'")
print("[verify] LoveConfig 菜单行数:", cur.fetchone()[0])

cur.close(); conn.close()
print("M23 DDL 全部完成")
