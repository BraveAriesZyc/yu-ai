# -*- coding: utf-8 -*-
"""
m5_admin_smoke.py — 管理后台五域 admin-api 冒烟(M3 收尾计划步骤 1)

前置:
  1. fat jar 含 love admin 代码(mvn -pl yudao-server -am package 重打)
  2. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法:
  cd love_baby_server && PYTHONIOENCODING=utf-8 python -I Temp/m5_admin_smoke.py 48081

断言清单(对照计划):
  S1 登录(admin/admin123,captcha 已关)拿 token
  S2 造数(pymysql):冒烟A/B 用户 + couple + todo x2 + message x2(SMOKE5 标记)
  S3 五域 page 各一发:code=0 且冒烟行在列表
  S4 筛选:user.mobile / couple.userANickname(join 列) / todo.coupleId / message.kind+coupleId
  S5 user/update 改昵称 → page 回读一致
  S6 mood-option-default 全 CRUD(create 默认启用 → update → page 回读 → delete 后不再出现)
  S7 couple/unbind 物理删(库查证 0 行)
  S8 user/delete 级联库证(deleted=1 + mobile 改写 + todo/message 连带 + 再删返 1_003_005_004)
  S9 清理:物理解删全部冒烟行,还原现场
"""
import json
import sys
import urllib.request
import urllib.error
from datetime import datetime

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MARK = "SMOKE5"
MOBILE_A = "13900005001"
MOBILE_B = "13900005002"

passed = []
failed = []


def check(name, cond, detail=""):
    tag = "PASS" if cond else "FAIL"
    (passed if cond else failed).append(name)
    print("[%s] %s %s" % (tag, name, detail))


def api(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + "/admin-api" + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"code": e.code, "msg": "HTTP %d" % e.code}


def db():
    return pymysql.connect(host="127.0.0.1", user="root", password="123456",
                           database="yudao", charset="utf8mb4")


def scalar(cur, sql, args=None):
    cur.execute(sql, args or ())
    row = cur.fetchone()
    return row[0] if row else None


def insert_row(cur, table, cols):
    """按 DESCRIBE 实际列过滤插入(love_couple 无 deleted,不写死列清单)"""
    cur.execute("DESCRIBE " + table)
    existing = {r[0] for r in cur.fetchall()}
    use = {k: v for k, v in cols.items() if k in existing}
    sql = "INSERT INTO %s (%s) VALUES (%s)" % (
        table, ",".join(use), ",".join(["%s"] * len(use)))
    cur.execute(sql, list(use.values()))
    return cur.lastrowid


def main():
    conn = db()
    cur = conn.cursor()

    # S9 前置清理(幂等):上轮残留物理解删
    cleanup(cur)
    conn.commit()

    # S1 登录
    r = api("POST", "/system/auth/login",
            body={"username": "admin", "password": "admin123"})
    token = (r.get("data") or {}).get("accessToken", "")
    check("S1 登录", r.get("code") == 0 and token,
          "" if token else json.dumps(r, ensure_ascii=False)[:120])
    if not token:
        cleanup(cur)
        conn.commit()
        return

    # S2 造数
    now = datetime.now()
    uid_a = insert_row(cur, "love_user", {
        "mobile": MOBILE_A, "password": "x", "nickname": MARK + "A",
        "status": 0, "tenant_id": 1, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now, "deleted": 0,
    })
    uid_b = insert_row(cur, "love_user", {
        "mobile": MOBILE_B, "password": "x", "nickname": MARK + "B",
        "status": 0, "tenant_id": 1, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now, "deleted": 0,
    })
    # love_user 有 uk_mobile:若同号残留(逻辑删未清)会撞键 → 提前物理解删已含于 cleanup
    couple_id = insert_row(cur, "love_couple", {
        "user_a_id": uid_a, "user_b_id": uid_b, "status": 0,
        "bind_time": now, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now,
    })
    insert_row(cur, "love_todo", {
        "couple_id": couple_id, "owner_id": uid_a, "title": MARK + " todo1",
        "done": 0, "tenant_id": 1, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now, "deleted": 0,
    })
    insert_row(cur, "love_todo", {
        "couple_id": couple_id, "owner_id": uid_b, "title": MARK + " todo2",
        "done": 0, "tenant_id": 1, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now, "deleted": 0,
    })
    insert_row(cur, "love_message", {
        "couple_id": couple_id, "sender_id": uid_a, "receiver_id": uid_b,
        "kind": 1, "content": MARK + " msg1", "audit_status": 1,
        "tenant_id": 1, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now, "deleted": 0,
    })
    insert_row(cur, "love_message", {
        "couple_id": couple_id, "sender_id": uid_b, "receiver_id": uid_a,
        "kind": 2, "content": MARK + " msg2", "audit_status": 1,
        "wish_key": "heart", "color": "#FF5A8C", "icon": "love",
        "tenant_id": 1, "creator": "1", "updater": "1",
        "create_time": now, "update_time": now, "deleted": 0,
    })
    conn.commit()
    check("S2 造数", uid_a > 0 and uid_b > 0 and couple_id > 0,
          "uidA=%s uidB=%s couple=%s" % (uid_a, uid_b, couple_id))

    # S3 五域 page
    r = api("GET", "/love/user/page?pageNo=1&pageSize=20&mobile=" + MOBILE_A, token)
    items = ((r.get("data") or {}).get("list") or [])
    hit = any(i.get("mobile") == MOBILE_A for i in items)
    check("S3a user/page", r.get("code") == 0 and hit,
          "code=%s total=%s" % (r.get("code"), (r.get("data") or {}).get("total")))

    r = api("GET", "/love/couple/page?pageNo=1&pageSize=20&userANickname=" + MARK + "A", token)
    items = ((r.get("data") or {}).get("list") or [])
    hit = any(i.get("userAId") == uid_a and i.get("userANickname") == MARK + "A" for i in items)
    check("S3b couple/page(join)", r.get("code") == 0 and hit,
          "code=%s total=%s" % (r.get("code"), (r.get("data") or {}).get("total")))

    r = api("GET", "/love/todo/page?pageNo=1&pageSize=20&coupleId=%d" % couple_id, token)
    todo_total = (r.get("data") or {}).get("total", 0)
    check("S3c todo/page", r.get("code") == 0 and todo_total == 2,
          "code=%s total=%s" % (r.get("code"), todo_total))

    r = api("GET", "/love/message/page?pageNo=1&pageSize=20&coupleId=%d&kind=2" % couple_id, token)
    items = ((r.get("data") or {}).get("list") or [])
    hit = any(i.get("kind") == 2 and (i.get("content") or "").startswith(MARK) for i in items)
    check("S3d message/page(kind)", r.get("code") == 0 and hit,
          "code=%s total=%s" % (r.get("code"), (r.get("data") or {}).get("total")))

    r = api("GET", "/love/mood-option-default/page?pageNo=1&pageSize=50", token)
    check("S3e moodOptionDefault/page", r.get("code") == 0
          and (r.get("data") or {}).get("total", 0) >= 5,  # 种子 5 行
          "code=%s total=%s" % (r.get("code"), (r.get("data") or {}).get("total")))

    # S4 筛选零命中负例:不存在手机号 → total=0
    r = api("GET", "/love/user/page?pageNo=1&pageSize=20&mobile=13999990000", token)
    check("S4 筛选负例", r.get("code") == 0
          and (r.get("data") or {}).get("total") == 0,
          "total=%s" % (r.get("data") or {}).get("total"))

    # S5 user/update 改昵称回读
    r = api("PUT", "/love/user/update", token,
            {"id": uid_a, "nickname": MARK + "A2"})
    r2 = api("GET", "/love/user/page?pageNo=1&pageSize=20&mobile=" + MOBILE_A, token)
    items = ((r2.get("data") or {}).get("list") or [])
    check("S5 user/update", r.get("code") == 0
          and any(i.get("nickname") == MARK + "A2" for i in items),
          "code=%s" % r.get("code"))

    # S6 mood-option-default 全 CRUD
    r = api("POST", "/love/mood-option-default/create", token,
            {"label": MARK + " 开心", "emoji": "\U0001F60A", "color": "#FFB93C", "sort": 90})
    new_id = (r.get("data") or {}).get("id", 0) if isinstance(r.get("data"), dict) else (r.get("data") or 0)
    check("S6a create(默认启用)", r.get("code") == 0 and new_id > 0,
          "code=%s id=%s" % (r.get("code"), new_id))
    r = api("PUT", "/love/mood-option-default/update", token,
            {"id": new_id, "label": MARK + " 感动", "emoji": "\U0001F970",
             "color": "#FF5A8C", "sort": 91, "status": 1})
    check("S6b update(含停用)", r.get("code") == 0, "code=%s" % r.get("code"))
    r = api("GET", "/love/mood-option-default/page?pageNo=1&pageSize=50&label=" + MARK, token)
    items = ((r.get("data") or {}).get("list") or [])
    hit = any(i.get("id") == new_id and i.get("status") == 1 for i in items)
    check("S6c page 回读", r.get("code") == 0 and hit, "")
    r = api("DELETE", "/love/mood-option-default/delete?id=%d" % new_id, token)
    check("S6d delete", r.get("code") == 0, "code=%s" % r.get("code"))
    r = api("GET", "/love/mood-option-default/page?pageNo=1&pageSize=50&label=" + MARK, token)
    check("S6e 删后不出现", r.get("code") == 0
          and (r.get("data") or {}).get("total") == 0,
          "total=%s" % (r.get("data") or {}).get("total"))

    # S7 couple/unbind 物理删
    r = api("DELETE", "/love/couple/unbind?id=%d" % couple_id, token)
    cur.execute("SELECT COUNT(*) FROM love_couple WHERE id=%s", (couple_id,))
    gone = scalar(cur, "SELECT COUNT(*) FROM love_couple WHERE id=%s", (couple_id,)) == 0
    check("S7 couple/unbind", r.get("code") == 0 and gone,
          "code=%s" % r.get("code"))

    # S8 user/delete 级联
    r = api("DELETE", "/love/user/delete?id=%d" % uid_a, token)
    check("S8a delete", r.get("code") == 0, "code=%s" % r.get("code"))
    # pymysql REPEATABLE READ:结束旧快照事务,以下 SELECT 才能看到服务端已提交的级联写
    conn.commit()
    row = None
    cur.execute("SELECT deleted, mobile FROM love_user WHERE id=%s", (uid_a,))
    row = cur.fetchone()
    cascaded_mobile = row and row[0] in (1, b"\x01") and ("#" in (row[1] or ""))
    check("S8b 逻辑删+mobile 改写", bool(cascaded_mobile),
          "deleted=%s mobile=%s" % (row[0] if row else None, row[1] if row else None))
    cur.execute("SELECT COUNT(*) FROM love_todo WHERE owner_id=%s AND deleted=0", (uid_a,))
    n = cur.fetchone()[0]
    check("S8c todo 连带逻辑删", n == 0, "剩未删=%d" % n)
    cur.execute(
        "SELECT COUNT(*) FROM love_message WHERE sender_id=%s AND deleted=0", (uid_a,))
    n = cur.fetchone()[0]
    check("S8d message 连带逻辑删", n == 0, "剩未删=%d" % n)
    r = api("DELETE", "/love/user/delete?id=%d" % uid_a, token)
    check("S8e 再删→1_003_005_004", r.get("code") == 1003005004,
          "code=%s msg=%s" % (r.get("code"), r.get("msg")))

    # S9 清理
    cleanup(cur)
    conn.commit()
    check("S9 清理还原", True)

    conn.close()
    print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


def cleanup(cur):
    """物理解删全部冒烟行(按 MARK/专用手机号,幂等;先 couple 后 user)"""
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE nickname LIKE %s) t) "
        "OR user_b_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE nickname LIKE %s) t)",
        (MARK + "%", MARK + "%"))
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s,%s) OR nickname LIKE %s",
                (MOBILE_A, MOBILE_B, MARK + "%"))
    cur.execute("DELETE FROM love_todo WHERE title LIKE %s", (MARK + "%",))
    cur.execute("DELETE FROM love_message WHERE content LIKE %s", (MARK + "%",))
    cur.execute("DELETE FROM love_mood_option_default WHERE label LIKE %s", (MARK + "%",))


if __name__ == "__main__":
    main()
