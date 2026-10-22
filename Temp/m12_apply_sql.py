# -*- coding: utf-8 -*-
"""m12_apply_sql.py — 执行 love.sql 的 M12 段(ALTER love_message ADD COLUMN recalled),并验证落库结果
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m12_apply_sql.py
重放:报 1060 duplicate column 属预期(M12 段注释已写明),跳过即可
"""
import io

import pymysql

SQL_PATH = r"C:\Users\13248\Desktop\app\love_baby_server\sql\mysql\love.sql"


def main():
    with io.open(SQL_PATH, "r", encoding="utf-8") as f:
        full = f.read()
    marker = "M12 段：聊天消息撤回"
    idx = full.find(marker)
    assert idx > 0, "M12 段未找到"
    line_start = full.rfind("\n", 0, idx) + 1
    section = full[line_start:]
    lines = [ln for ln in section.splitlines() if not ln.strip().startswith("--")]
    body = "\n".join(lines)
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    print("statements:", len(stmts))
    assert len(stmts) == 1, "M12 段应恰为 1 条 ALTER"

    conn = pymysql.connect(host="127.0.0.1", user="root", password="123456",
                           database="yudao", charset="utf8mb4")
    cur = conn.cursor()
    cur.execute(stmts[0])
    conn.commit()

    # information_schema 断言:列存在 / bit(1) / NOT NULL / 默认 b'0'
    cur.execute(
        "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT "
        "FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = 'yudao' AND TABLE_NAME = 'love_message' "
        "AND COLUMN_NAME = 'recalled'")
    rows = cur.fetchall()
    assert len(rows) == 1, "recalled 列应存在,实为 %d 行" % len(rows)
    name, ctype, nullable, default, comment = rows[0]
    print("[verify]", name, "type:", ctype, "nullable:", nullable, "default:", default)
    print("[verify] comment:", comment)
    assert ctype == "bit(1)", "应为 bit(1),实为 %s" % ctype
    assert nullable == "NO", "应为 NOT NULL"
    assert str(default) in ("0", "b'0'"), "默认值应为 b'0',实为 %s" % default
    # 存量行自动 b'0'(bit(1) pymysql 读回 bytes/bool 视驱动版本,统一按 int 比对)
    cur.execute("SELECT COUNT(*) FROM love_message WHERE recalled <> 0")
    nonzero = cur.fetchone()[0]
    assert nonzero == 0, "存量行应全为 0,非 0 行 %d" % nonzero
    cur.execute("SELECT COUNT(*) FROM love_message")
    print("[verify] love_message rows(all recalled=0):", cur.fetchone()[0])
    conn.close()
    print("OK: M12 ALTER applied & verified")


if __name__ == "__main__":
    main()
