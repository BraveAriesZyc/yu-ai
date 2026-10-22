# -*- coding: utf-8 -*-
"""m5_apply_sql.py — 执行 love.sql 的 M5 段(建表 + 幂等菜单),并验证落库结果
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m5_apply_sql.py
"""
import io

import pymysql

SQL_PATH = r"C:\Users\13248\Desktop\app\love_baby_server\sql\mysql\love.sql"


def main():
    with io.open(SQL_PATH, "r", encoding="utf-8") as f:
        full = f.read()
    marker = "M5 段：纪念日表"
    idx = full.find(marker)
    assert idx > 0, "M5 段未找到"
    # 回退到 marker 所在行行首,保证注释过滤从完整行开始
    line_start = full.rfind("\n", 0, idx) + 1
    section = full[line_start:]
    # 去注释行(段内字符串字面量无分号,按 ; 切分安全)
    lines = [ln for ln in section.splitlines() if not ln.strip().startswith("--")]
    body = "\n".join(lines)
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    print("statements:", len(stmts))

    conn = pymysql.connect(host="127.0.0.1", user="root", password="123456",
                           database="yudao", charset="utf8mb4")
    cur = conn.cursor()
    for s in stmts:
        cur.execute(s)
    conn.commit()

    cur.execute("SELECT COUNT(*) FROM love_anniversary")
    print("[verify] love_anniversary rows:", cur.fetchone()[0])
    cur.execute(
        "SELECT id, name, type, sort, permission, component_name FROM system_menu "
        "WHERE deleted = b'0' AND (component_name = 'LoveAnniversary' "
        "OR permission IN ('love:anniversary:query', 'love:anniversary:delete')) "
        "ORDER BY type, sort")
    for row in cur.fetchall():
        print("[verify] menu:", row)
    conn.close()


if __name__ == "__main__":
    main()
