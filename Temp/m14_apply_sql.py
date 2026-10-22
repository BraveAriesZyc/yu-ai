# -*- coding: utf-8 -*-
"""m14_apply_sql.py — 执行 love.sql 的 M14 段(love_wish 建表 + 菜单/按钮权限),并验证落库结果
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m14_apply_sql.py
幂等:CREATE TABLE IF NOT EXISTS + 菜单先子后父 DELETE(按 permission / component_name),整段可重放
"""
import io

import pymysql

SQL_PATH = r"C:\Users\13248\Desktop\app\love_baby_server\sql\mysql\love.sql"


def main():
    with io.open(SQL_PATH, "r", encoding="utf-8") as f:
        full = f.read()
    marker = "M14 段：恋爱清单表 + 菜单/按钮权限"
    idx = full.find(marker)
    assert idx > 0, "M14 段未找到"
    line_start = full.rfind("\n", 0, idx) + 1
    section = full[line_start:]
    lines = [ln for ln in section.splitlines() if not ln.strip().startswith("--")]
    body = "\n".join(lines)
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    print("statements:", len(stmts))
    assert len(stmts) == 8, "M14 段应恰为 8 条(CREATE+3 菜单维护+3 菜单 INSERT+1 回查... 实际切分见上)"

    conn = pymysql.connect(host="127.0.0.1", user="root", password="123456",
                           database="yudao", charset="utf8mb4")
    cur = conn.cursor()

    # 重放两遍验证幂等(第二遍菜单 DELETE+INSERT 换 id 属预期,行数不增)
    for run in (1, 2):
        for s in stmts:
            cur.execute(s)
        conn.commit()
        print("run %d ok" % run)

    # ---- 验证 1:love_wish 表结构 ----
    cur.execute(
        "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT "
        "FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = 'yudao' AND TABLE_NAME = 'love_wish' ORDER BY ORDINAL_POSITION")
    cols = cur.fetchall()
    names = [c[0] for c in cols]
    print("love_wish columns:", names)
    expect = ["id", "couple_id", "user_id", "title", "done", "done_by", "done_time",
              "creator", "create_time", "updater", "update_time", "deleted"]
    assert names == expect, "列清单不符: %s" % names
    by_name = {c[0]: c for c in cols}
    assert by_name["title"][1].startswith("varchar(64)"), "title 应 varchar(64)"
    assert by_name["done"][1] == "bit(1)" and by_name["done"][2] == "NO", "done 应 bit(1) NOT NULL"
    assert by_name["done_by"][2] == "YES" and by_name["done_time"][2] == "YES", "done_by/done_time 应可空"

    # ---- 验证 2:idx_couple_id 索引 ----
    cur.execute(
        "SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS "
        "WHERE TABLE_SCHEMA = 'yudao' AND TABLE_NAME = 'love_wish' AND INDEX_NAME = 'idx_couple_id' "
        "ORDER BY SEQ_IN_INDEX")
    idx_rows = cur.fetchall()
    assert [(r[2]) for r in idx_rows] == ["couple_id", "id"], "idx_couple_id 应为 (couple_id, id): %s" % (idx_rows,)

    # ---- 验证 3:菜单三行(管理菜单 sort=9 + 两按钮),重放后仍恰 3 行 ----
    cur.execute(
        "SELECT name, permission, type, sort, path, icon, component, component_name "
        "FROM system_menu WHERE component_name = 'LoveWish' "
        "OR permission IN ('love:wish:query', 'love:wish:delete') ORDER BY type, sort")
    menus = cur.fetchall()
    for m in menus:
        print("menu:", m)
    assert len(menus) == 3, "菜单应恰 3 行(重放幂等),实为 %d" % len(menus)
    main_menu = [m for m in menus if m[2] == 2]
    btns = [m for m in menus if m[2] == 3]
    assert len(main_menu) == 1 and len(btns) == 2, "应 1 管理菜单 + 2 按钮"
    mm = main_menu[0]
    assert mm[0] == "恋爱清单" and mm[3] == 9 and mm[4] == "wish" and mm[5] == "ep:collection" \
        and mm[6] == "love/wish/index" and mm[7] == "LoveWish", "管理菜单字段不符: %s" % (mm,)
    perms = sorted(m[1] for m in btns)
    assert perms == ["love:wish:delete", "love:wish:query"], "按钮权限不符: %s" % perms

    # ---- 验证 4:按钮 parent_id 挂在管理菜单下 ----
    cur.execute(
        "SELECT COUNT(*) FROM system_menu b JOIN system_menu p ON b.parent_id = p.id "
        "WHERE p.component_name = 'LoveWish' AND b.type = 3")
    assert cur.fetchone()[0] == 2, "两按钮应挂在 LoveWish 菜单下"

    cur.close()
    conn.close()
    print("M14 SQL apply + verify: ALL PASS")


if __name__ == "__main__":
    main()
