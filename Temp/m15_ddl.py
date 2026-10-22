# -*- coding: utf-8 -*-
"""M15 DDL 落库脚本:love_moment 加 image_urls 列 + 存量回填 + love_moment_like 建表。
幂等可重放(与 love.sql M15 段口径一致)。用法: python -I m15_ddl.py"""
import sys
sys.stdout.reconfigure(encoding="utf-8")
import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="123456",
                       database="yudao", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

# 1) 加列(判列存在,幂等)
cur.execute("""SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='love_moment' AND COLUMN_NAME='image_urls'""")
exists = cur.fetchone()[0]
if exists:
    print("[skip] love_moment.image_urls 列已存在")
else:
    cur.execute("""ALTER TABLE `love_moment` ADD COLUMN `image_urls` text NULL
                   COMMENT '多图 URL JSON 数组字符串(M15,如 ["u1","u2"],≤9 元素;image_url 列保留为首图冗余,admin 零改动)'""")
    print("[ok] 已加列 image_urls")

# 2) 存量单图回填(幂等)
cur.execute("SELECT COUNT(*) FROM love_moment WHERE image_urls IS NULL OR image_urls=''")
before = cur.fetchone()[0]
if before:
    cur.execute("UPDATE love_moment SET image_urls = JSON_ARRAY(image_url) WHERE image_urls IS NULL OR image_urls=''")
    print(f"[ok] 回填 {cur.rowcount} 行(命中 {before})")
else:
    print("[skip] 无需回填(image_urls 全部非空)")
cur.execute("SELECT COUNT(*) FROM love_moment WHERE image_urls IS NULL OR image_urls=''")
assert cur.fetchone()[0] == 0, "回填后仍有空 image_urls"

# 3) 点赞表(IF NOT EXISTS 幂等)
cur.execute("""CREATE TABLE IF NOT EXISTS `love_moment_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '点赞编号',
  `couple_id` bigint NOT NULL COMMENT '情侣关系编号(归属第二道闸,service 已校验;解绑重绑后留历史值)',
  `moment_id` bigint NOT NULL COMMENT '瞬间编号(love_moment.id,不设外键;删瞬间不级联清赞,残留行无害——D-12 残留策略同款哲学)',
  `user_id` bigint NOT NULL COMMENT '点赞用户编号(couple 恒 2 人,双方均可点赞任意瞬间)',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除(继承 BaseDO 惯例;本表 unlike 物理删,恒 0)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_moment_user`(`moment_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_moment_id`(`moment_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '相册瞬间点赞表(情侣业务,M15)'""")
print("[ok] love_moment_like 表就绪")

# 验证
cur.execute("""SELECT COLUMN_TYPE FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='love_moment' AND COLUMN_NAME='image_urls'""")
print("[verify] image_urls:", cur.fetchone()[0])
cur.execute("""SELECT INDEX_NAME, NON_UNIQUE FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='love_moment_like'
               GROUP BY INDEX_NAME, NON_UNIQUE""")
print("[verify] love_moment_like 索引:", cur.fetchall())
cur.execute("SELECT COUNT(*) FROM love_moment")
print("[info] love_moment 现有行数:", cur.fetchone()[0])

cur.close(); conn.close()
print("M15 DDL 全部完成")
