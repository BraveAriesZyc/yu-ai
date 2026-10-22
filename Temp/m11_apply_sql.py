# -*- coding: utf-8 -*-
"""m11_apply_sql.py — 执行 love.sql 的 M11 段(ALTER love_user ADD COLUMN chat_read_cursor),并验证落库结果
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m11_apply_sql.py
重放:报 1060 duplicate column 属预期(M11 段注释已写明),跳过即可
"""
import io

import pymysql

SQL_PATH = r"C:\Users\13248\Desktop\app\love_baby_server\sql\mysql\love.sql"


def main():
    with io.open(SQL_PATH, "r", encoding="utf-8") as f:
        full = f.read()
    marker = "M11 段：聊天未读红点已读游标"
    idx = full.find(marker)
    assert idx > 0, "M11 段未找到"
    line_start = full.rfind("\n", 0, idx) + 1
    section = full[line_start:]
    lines = [ln for ln in section.splitlines() if not ln.strip().startswith("--")]
    body = "\n".join(lines)
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    print("statements:", len(stmts))
    assert len(stmts) == 1, "M11 段应恰为 1 条 ALTER"

    conn = pymysql.connect(host="127.0.0.1", user="root", password="123456",
                           database="yudao", charset="utf8mb4")
    cur = conn.cursor()
    cur.execute(stmts[0])
    conn.commit()

    # information_schema 断言:列存在 / bigint / NOT NULL / 默认 0
    cur.execute(
        "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT "
        "FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = 'yudao' AND TABLE_NAME = 'love_user' "
        "AND COLUMN_NAME = 'chat_read_cursor'")
    rows = cur.fetchall()
    assert len(rows) == 1, "chat_read_cursor 列应存在,实为 %d 行" % len(rows)
    name, dtype, nullable, default, comment = rows[0]
    print("[verify]", name, "type:", dtype, "nullable:", nullable, "default:", default)
    print("[verify] comment:", comment)
    assert dtype == "bigint", "应为 bigint,实为 %s" % dtype
    assert nullable == "NO", "应为 NOT NULL"
    assert str(default) in ("0", "0"), "默认值应为 0,实为 %s" % default
    # 存量行自动 0
    cur.execute("SELECT COUNT(*) FROM love_user WHERE chat_read_cursor <> 0")
    nonzero = cur.fetchone()[0]
    assert nonzero == 0, "存量行应全为 0,非 0 行 %d" % nonzero
    cur.execute("SELECT COUNT(*) FROM love_user")
    print("[verify] love_user rows(all cursor=0):", cur.fetchone()[0])
    conn.close()
    print("OK: M11 ALTER applied & verified")


if __name__ == "__main__":
    main()
