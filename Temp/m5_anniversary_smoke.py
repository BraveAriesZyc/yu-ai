# -*- coding: utf-8 -*-
"""
m5_anniversary_smoke.py — 纪念日(M5)全链路冒烟:app 双通道 + admin 通道

前置:
  1. fat jar 含 anniversary 代码(mvn -pl yudao-server -am package 重打)
  2. dev 库已执行 love.sql M5 段(表 + 菜单)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m5_anniversary_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 A create-invite → B bind(couple1)
  T3 A create ×3 回包全量(id/ownerId/createTime/annivDate 字符串原样/emoji 归一)
  T4 非法入参:2026-02-30 → 1_003_006_002;repeatType=3 → 400;title 超 32 → 400;删不存在 → 1_003_006_000
  T5 A list:3 条、按 annivDate asc、字段完整(emoji 空串非 null)
  T6 B 同 couple 同视图 + B 删 A 的行(couple 共享可操作) → A list 剩 2
  T7 C 未绑定:list/create → 1_003_004_003
  T8 第二对 C+D:D create → A 跨 couple 删 → 1_003_006_001(IDOR)
  T9 admin:page(title 模糊 M5,3 条 + annivDate 字符串)→ delete → 回读剩 2 → 删不存在 → 1_003_006_000
  T10 清理还原(物理解删;孤儿清理放删 couple/user 之后)
"""
import json
import sys
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900006031"
MOBILE_B = "13900006032"
MOBILE_C = "13900006033"
MOBILE_D = "13900006034"
TITLE_PREFIX = "M5"

passed = []
failed = []


def check(name, cond, detail=""):
    tag = "PASS" if cond else "FAIL"
    (passed if cond else failed).append(name)
    print("[%s] %s %s" % (tag, name, detail))


def api(channel, method, path, token=None, body=None):
    req = urllib.request.Request(BASE + "/" + channel + path, method=method)
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


def cleanup(cur):
    """物理解删冒烟数据。⚠ 顺序:先删 couple/user,再清业务孤儿(在世时 NOT IN 恒空转)"""
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s,%s)) t) "
        "OR user_b_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s,%s)) t)",
        (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D) * 2)
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s,%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    cur.execute("DELETE FROM love_anniversary WHERE title LIKE %s", (TITLE_PREFIX + " %",))
    cur.execute(
        "DELETE FROM love_anniversary WHERE couple_id NOT IN "
        "(SELECT id FROM love_couple)")


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code",
        body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login",
            body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", ""), r


def create(pager_token, title, date, repeat, emoji):
    return api("app-api", "POST", "/love/anniversary/create", pager_token,
               {"title": title, "annivDate": date, "repeatType": repeat, "emoji": emoji})


def main():
    conn = db()
    cur = conn.cursor()
    cleanup(cur)
    conn.commit()

    # T1 四号登录
    token_a, ra = member_login(MOBILE_A)
    token_b, rb = member_login(MOBILE_B)
    token_c, rc = member_login(MOBILE_C)
    token_d, rd = member_login(MOBILE_D)
    check("T1 四号 sms-login", all([token_a, token_b, token_c, token_d]),
          "" if all([token_a, token_b, token_c, token_d])
          else json.dumps(ra, ensure_ascii=False)[:120])

    # T2 couple1 = A+B
    r = api("app-api", "POST", "/love/couple/create-invite", token_a)
    invite = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", token_b, {"inviteCode": invite})
    check("T2 绑定 couple1", r.get("code") == 0 and bool(invite),
          "code=%s" % r.get("code"))

    # T3 create ×3,回包全量断言
    r1 = create(token_a, "M5 恋爱纪念日", "2020-02-14", 1, "❤")   # 每年·过去
    r2 = create(token_a, "M5 旅行约定", "2027-01-01", 2, "✈")     # 一次性·未来
    r3 = create(token_b, "M5 生日", "2026-12-24", 1, "")               # 每年·未来·空 emoji
    d1, d2, d3 = (r.get("data") or {} for r in (r1, r2, r3))
    ok = (r1.get("code") == 0 and r2.get("code") == 0 and r3.get("code") == 0
          and all(d.get("id") and d.get("ownerId") and d.get("createTime") for d in (d1, d2, d3))
          and d1.get("annivDate") == "2020-02-14" and isinstance(d1.get("annivDate"), str)
          and d2.get("annivDate") == "2027-01-01"
          and d1.get("emoji") == "❤" and d3.get("emoji") == "")
    check("T3 create ×3 回包全量(annivDate 字符串/emoji 归一)", ok,
          json.dumps([r1.get("code"), r2.get("code"), r3.get("code")], ensure_ascii=False))

    # T4 非法入参
    r = create(token_a, "M5 非法日历", "2026-02-30", 1, "")
    check("T4a 2026-02-30 → 1_003_006_002", r.get("code") == 1003006002,
          "code=%s msg=%s" % (r.get("code"), r.get("msg")))
    r = create(token_a, "M5 非法重复", "2026-06-01", 3, "")
    check("T4b repeatType=3 → 400", r.get("code") == 400,
          "code=%s" % r.get("code"))
    r = create(token_a, "X" * 33, "2026-06-01", 1, "")
    check("T4c title 超 32 → 400", r.get("code") == 400,
          "code=%s" % r.get("code"))
    r = api("app-api", "POST", "/love/anniversary/delete", token_a, {"id": 999999999})
    check("T4d 删不存在 → 1_003_006_000", r.get("code") == 1003006000,
          "code=%s" % r.get("code"))

    # T5 A list:3 条、annivDate asc、字段完整
    conn.commit()  # 结束 REPEATABLE READ 旧快照再读
    r = api("app-api", "GET", "/love/anniversary/list", token_a)
    rows = r.get("data") or []
    dates = [x.get("annivDate") for x in rows]
    ok = (r.get("code") == 0 and len(rows) == 3
          and dates == ["2020-02-14", "2026-12-24", "2027-01-01"]
          and all(x.get("title") and x.get("repeatType") in (1, 2)
                  and x.get("emoji") is not None and x.get("ownerId") for x in rows))
    check("T5 A list 3 条 annivDate asc", ok, json.dumps(dates, ensure_ascii=False))

    # T6 B 同视图 + B 删 A 的行(couple 共享)
    r = api("app-api", "GET", "/love/anniversary/list", token_b)
    rows_b = r.get("data") or []
    del_id = rows_b[0].get("id") if rows_b else None
    r = api("app-api", "POST", "/love/anniversary/delete", token_b, {"id": del_id})
    check("T6a B 删 A 的行", r.get("code") == 0, "code=%s" % r.get("code"))
    conn.commit()
    r = api("app-api", "GET", "/love/anniversary/list", token_a)
    rows_a = r.get("data") or []
    check("T6b A list 剩 2", r.get("code") == 0 and len(rows_a) == 2,
          "len=%s" % len(rows_a))

    # T7 C 未绑定
    r = api("app-api", "GET", "/love/anniversary/list", token_c)
    check("T7a C list 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))
    r = create(token_c, "M5 未绑定", "2026-06-01", 1, "")
    check("T7b C create 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))

    # T8 第二对 C+D;A 跨 couple 删 D 的行 → IDOR
    r = api("app-api", "POST", "/love/couple/create-invite", token_c)
    invite2 = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", token_d, {"inviteCode": invite2})
    check("T8a 绑定 couple2", r.get("code") == 0 and bool(invite2),
          "code=%s" % r.get("code"))
    r = create(token_d, "M5 D 的纪念日", "2026-08-08", 1, "\U0001F382")
    d_row = r.get("data") or {}
    check("T8b D create", r.get("code") == 0 and d_row.get("id"),
          "code=%s" % r.get("code"))
    r = api("app-api", "POST", "/love/anniversary/delete", token_a,
            {"id": d_row.get("id")})
    check("T8c A 跨 couple 删 → 1_003_006_001", r.get("code") == 1003006001,
          "code=%s msg=%s" % (r.get("code"), r.get("msg")))

    # T9 admin 通道
    r = api("admin-api", "POST", "/system/auth/login",
            body={"username": "admin", "password": "admin123"})
    admin_token = (r.get("data") or {}).get("accessToken", "")
    check("T9a admin 登录", r.get("code") == 0 and admin_token, "")
    r = api("admin-api", "GET", "/love/anniversary/page?pageNo=1&pageSize=50&title=M5",
            admin_token)
    page = r.get("data") or {}
    items = page.get("list") or []
    ok = (r.get("code") == 0 and page.get("total") == 3
          and all(isinstance(i.get("annivDate"), str) and i.get("coupleId")
                  and i.get("repeatType") in (1, 2) for i in items))
    check("T9b admin page title 模糊 3 条", ok,
          "total=%s" % page.get("total"))
    r = api("admin-api", "DELETE", "/love/anniversary/delete?id=%s" % d_row.get("id"),
            admin_token)
    check("T9c admin delete", r.get("code") == 0, "code=%s" % r.get("code"))
    conn.commit()
    r = api("admin-api", "GET", "/love/anniversary/page?pageNo=1&pageSize=50&title=M5",
            admin_token)
    check("T9d admin 回读剩 2", r.get("code") == 0
          and (r.get("data") or {}).get("total") == 2,
          "total=%s" % (r.get("data") or {}).get("total"))
    r = api("admin-api", "DELETE", "/love/anniversary/delete?id=%s" % d_row.get("id"),
            admin_token)
    check("T9e admin 删不存在 → 1_003_006_000", r.get("code") == 1003006000,
          "code=%s" % r.get("code"))

    # T10 清理
    cleanup(cur)
    conn.commit()
    check("T10 清理还原", True)
    conn.close()

    print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
