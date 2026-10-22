# -*- coding: utf-8 -*-
"""m10_apply_sql.py — 执行 love.sql 的 M10 段(ALTER love_message kind 注释 + content 500→512),并验证落库结果
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m10_apply_sql.py
"""
import io

import pymysql

SQL_PATH = r"C:\Users\13248\Desktop\app\love_baby_server\sql\mysql\love.sql"


def main():
    with io.open(SQL_PATH, "r", encoding="utf-8") as f:
        full = f.read()
    marker = "M10 段：聊天图片消息"
    idx = full.find(marker)
    assert idx > 0, "M10 段未找到"
    # 回退到 marker 所在行行首,保证注释过滤从完整行开始
    line_start = full.rfind("\n", 0, idx) + 1
    section = full[line_start:]
    lines = [ln for ln in section.splitlines() if not ln.strip().startswith("--")]
    body = "\n".join(lines)
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    print("statements:", len(stmts))
    assert len(stmts) == 1, "M10 段应恰为 1 条 ALTER"

    conn = pymysql.connect(host="127.0.0.1", user="root", password="123456",
                           database="yudao", charset="utf8mb4")
    cur = conn.cursor()
    cur.execute(stmts[0])
    conn.commit()

    # information_schema 断言:列宽 512 / kind 注释含「3图片消息」/ charset 不变
    cur.execute(
        "SELECT COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH, COLUMN_COMMENT "
        "FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = 'yudao' AND TABLE_NAME = 'love_message' "
        "AND COLUMN_NAME IN ('kind', 'content') ORDER BY ORDINAL_POSITION")
    rows = cur.fetchall()
    for name, length, comment in rows:
        print("[verify]", name, "len:", length, "comment:", comment)
    by_name = {r[0]: r for r in rows}
    assert by_name["content"][1] == 512, "content 列宽应为 512,实为 %s" % by_name["content"][1]
    assert "3图片消息" in by_name["kind"][2], "kind 注释应含「3图片消息」"
    assert "kind=3" in by_name["content"][2], "content 注释应含「kind=3」"
    # 存量行不受影响(变长扩列仅元数据变更)
    cur.execute("SELECT COUNT(*) FROM love_message")
    print("[verify] love_message rows:", cur.fetchone()[0])
    conn.close()
    print("OK: M10 ALTER applied & verified")


if __name__ == "__main__":
    main()
