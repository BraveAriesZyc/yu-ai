# -*- coding: utf-8 -*-
"""
m9_moment_smoke.py — 相册瞬间(M9)全链路冒烟:app 双通道 + admin 通道

前置:
  1. fat jar 含 moment 代码(mvn -pl yudao-server -am package 重打)
  2. dev 库已执行 love.sql M9 段(表 + 菜单)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m9_moment_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 A create-invite → B bind(couple1)
  T3 A create(图+文):回包全量(id/userId/imageUrl/text/createTime)
  T4 list 双方共享:A/B 均 1 条;text 原样回传
  T5 B create → couple1 list 2 条且 id 倒序(B 新在前);text 空串落库
  T6 游标分页:limit=1 → 最新 1 条;beforeId=该 id → 上一条;不传 limit 默认 30
  T7 text 65 字 → 400(@Length)
  T8 C 未绑定:create/list → 1_003_004_003
  T9 第二对 C+D:D create 后 couple1 list 仍 2 条;couple2 list 只见 D 自己 1 条
  T10 B 删 A 的 → 1_003_008_001(仅上传者);B 删自己的 → code=0;A list 剩 1;删不存在 → 1_003_008_000
  T11 admin:page(coupleId/userId 精确+createTime 段 %5B%5D)→ delete → app list 剩 1 → 删不存在 1_003_008_000
  T12 清理还原(物理删;孤儿清理放删 couple/user 之后)
"""
import json
import sys
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900008031"
MOBILE_B = "13900008032"
MOBILE_C = "13900008033"
MOBILE_D = "13900008034"
FAKE_URL = ("http://192.168.1.4:48080/admin-api/infra/file/4/get/"
            "smoke_m9_%s.jpg" % ("1600000000000"))

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
    """物理解删冒烟数据。⚠ 顺序:业务行 → couple/user → 孤儿兜底(在世时 NOT IN 恒空转)"""
    cur.execute(
        "DELETE FROM love_moment WHERE user_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s,%s)) t)",
        (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s,%s)) t) "
        "OR user_b_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s,%s)) t)",
        (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D) * 2)
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s,%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    cur.execute("DELETE FROM love_moment WHERE couple_id NOT IN "
                "(SELECT id FROM love_couple)")


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code",
        body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login",
            body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", ""), r


def moment_create(token, text=None, image_url=FAKE_URL):
    body = {"imageUrl": image_url}
    if text is not None:
        body["text"] = text
    return api("app-api", "POST", "/love/moment/create", token, body)


def moment_list(token, before_id=None, limit=None):
    qs = []
    if before_id:
        qs.append("beforeId=%s" % before_id)
    if limit:
        qs.append("limit=%s" % limit)
    path = "/love/moment/list" + ("?" + "&".join(qs) if qs else "")
    return api("app-api", "GET", path, token)


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

    # T3 A create(图+文,回包全量)
    r = moment_create(token_a, "今天的晚霞")
    d = r.get("data") or {}
    row_a = d.get("id")
    ok = (r.get("code") == 0 and row_a and d.get("userId") is not None
          and d.get("imageUrl") == FAKE_URL and d.get("text") == "今天的晚霞"
          and bool(d.get("createTime")))
    check("T3 A create 回包全量(图+文)", ok,
          json.dumps(d, ensure_ascii=False)[:200])

    # T4 list 双方共享
    conn.commit()  # 结束 REPEATABLE READ 旧快照再读
    r = moment_list(token_a)
    rows = r.get("data") or []
    check("T4a A list 1 条", r.get("code") == 0 and len(rows) == 1,
          "len=%s" % len(rows))
    r = moment_list(token_b)
    rows = r.get("data") or []
    check("T4b B list 同条(couple 共享)", r.get("code") == 0 and len(rows) == 1
          and rows[0].get("text") == "今天的晚霞",
          "len=%s" % len(rows))

    # T5 B create(无配文)→ 倒序
    r = moment_create(token_b)
    d5 = r.get("data") or {}
    row_b = d5.get("id")
    check("T5a B create(无配文 text='')", r.get("code") == 0 and row_b
          and d5.get("text") == "",
          "text=%r" % d5.get("text"))
    conn.commit()
    r = moment_list(token_a)
    rows = r.get("data") or []
    ok = (r.get("code") == 0 and len(rows) == 2
          and str(rows[0].get("id")) == str(row_b)
          and str(rows[1].get("id")) == str(row_a))
    check("T5b couple1 list 2 条且新→旧", ok,
          "ids=%s" % [x.get("id") for x in rows])

    # T6 游标分页
    r = moment_list(token_a, limit=1)
    rows = r.get("data") or []
    check("T6a limit=1 → 最新 1 条(B)", r.get("code") == 0 and len(rows) == 1
          and str(rows[0].get("id")) == str(row_b),
          "id=%s" % (rows[0].get("id") if rows else None))
    r = moment_list(token_a, before_id=row_b, limit=1)
    rows = r.get("data") or []
    check("T6b beforeId=row_b → 上一条(A)", r.get("code") == 0 and len(rows) == 1
          and str(rows[0].get("id")) == str(row_a),
          "id=%s" % (rows[0].get("id") if rows else None))
    r = moment_list(token_a)
    check("T6c 缺省 limit=30 全回", r.get("code") == 0
          and len(r.get("data") or []) == 2,
          "len=%s" % len(r.get("data") or []))

    # T7 text 超限 400
    r = moment_create(token_a, "字" * 65)
    check("T7 text 65 字 → 400", r.get("code") == 400,
          "code=%s" % r.get("code"))

    # T8 C 未绑定
    r = moment_create(token_c, "未绑定")
    check("T8a C create 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))
    r = moment_list(token_c)
    check("T8b C list 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))

    # T9 第二对 C+D:隔离
    r = api("app-api", "POST", "/love/couple/create-invite", token_c)
    invite2 = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", token_d, {"inviteCode": invite2})
    check("T9a 绑定 couple2", r.get("code") == 0 and bool(invite2),
          "code=%s" % r.get("code"))
    r = moment_create(token_d, "couple2 的瞬间")
    row_d = (r.get("data") or {}).get("id")
    check("T9b D create", r.get("code") == 0 and row_d, "code=%s" % r.get("code"))
    conn.commit()
    r = moment_list(token_a)
    check("T9c couple1 list 仍 2 条(couple 隔离)",
          r.get("code") == 0 and len(r.get("data") or []) == 2,
          "len=%s" % len(r.get("data") or []))
    r = moment_list(token_d)
    rows = r.get("data") or []
    check("T9d couple2 list 只见 D 自己", r.get("code") == 0 and len(rows) == 1
          and str(rows[0].get("id")) == str(row_d),
          "len=%s" % len(rows))

    # T10 删除权限:仅上传者本人
    r = api("app-api", "POST", "/love/moment/delete", token_b, {"id": row_a})
    check("T10a B 删 A 的 → 1_003_008_001", r.get("code") == 1003008001,
          "code=%s msg=%s" % (r.get("code"), r.get("msg")))
    r = api("app-api", "POST", "/love/moment/delete", token_b, {"id": row_b})
    check("T10b B 删自己的 → code=0", r.get("code") == 0,
          "code=%s" % r.get("code"))
    conn.commit()
    r = moment_list(token_a)
    check("T10c A list 剩 1(仅 A 的)", r.get("code") == 0
          and len(r.get("data") or []) == 1,
          "len=%s" % len(r.get("data") or []))
    r = api("app-api", "POST", "/love/moment/delete", token_a, {"id": 999999999})
    check("T10d 删不存在 → 1_003_008_000", r.get("code") == 1003008000,
          "code=%s" % r.get("code"))

    # T11 admin 通道
    r = api("admin-api", "POST", "/system/auth/login",
            body={"username": "admin", "password": "admin123"})
    admin_token = (r.get("data") or {}).get("accessToken", "")
    check("T11a admin 登录", r.get("code") == 0 and admin_token, "")
    uids = {}
    cur.execute("SELECT mobile, id FROM love_user WHERE mobile IN (%s,%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    for mobile, uid in cur.fetchall():
        uids[mobile] = uid
    conn.commit()
    r = api("admin-api", "GET",
            "/love/moment/page?pageNo=1&pageSize=50&userId=%s&createTime%%5B0%%5D=2026-01-01&createTime%%5B1%%5D=2026-12-31"
            % uids[MOBILE_A], admin_token)
    page = r.get("data") or {}
    items = page.get("list") or []
    ok = (r.get("code") == 0 and page.get("total") == 1 and items
          and str(items[0].get("id")) == str(row_a)
          and items[0].get("imageUrl") == FAKE_URL
          and items[0].get("coupleId") is not None)
    check("T11b admin page userId 精确+时间段 1 条", ok,
          "total=%s" % page.get("total"))
    r = api("admin-api", "DELETE", "/love/moment/delete?id=%s" % row_a, admin_token)
    check("T11c admin delete", r.get("code") == 0, "code=%s" % r.get("code"))
    conn.commit()
    r = moment_list(token_a)
    check("T11d admin 删除后 A list 空", r.get("code") == 0
          and len(r.get("data") or []) == 0,
          "len=%s" % len(r.get("data") or []))
    r = api("admin-api", "DELETE", "/love/moment/delete?id=999999999", admin_token)
    check("T11e admin 删不存在 → 1_003_008_000", r.get("code") == 1003008000,
          "code=%s msg=%s" % (r.get("code"), r.get("msg")))

    # T12 清理
    cleanup(cur)
    conn.commit()
    check("T12 清理还原", True)
    conn.close()

    print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
