# -*- coding: utf-8 -*-
"""
m10_message_image_smoke.py — 聊天图片消息(M10, kind=3)全链路冒烟:app 双通道 + admin 通道

前置:
  1. fat jar 含 send-image 代码(mvn -pl yudao-server -am package 重打)
  2. dev 库已执行 love.sql M10 段(content varchar(512) + kind 注释 3图片消息)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m10_message_image_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 A create-invite → B bind(couple1)
  T3 A send-image:回包全量(id/coupleId/senderId/receiverId/kind=3/content=URL/
     wishKey/color/icon 空/auditStatus=1/createTime 毫秒)
  T4 A 再发 1 条文本 → 双方 list 混序正常(最新文本在前、图片在后),content 原样
  T5 落库 kind=3(content=URL,REPEATABLE READ 写后 conn.commit() 再 SELECT)
  T6 imageUrl 空串/缺字段/纯空白 → 400
  T7 imageUrl 513 字 → 400(@Length max=512)
  T8 C 未绑定 send-image → 1_003_004_003
  T9 第二对 C+D 隔离:D send-image 后 couple1 list 条数不变;couple2 list 只见 D 那条
  T10 admin:page content LIKE 命中 URL 片段 + kind=3 精确筛选命中
  T11 心愿时间线不被污染:B wish-list 无 kind=3 行
  T12 清理还原(物理删;孤儿清理放删 couple/user 之后)
"""
import json
import sys
import time
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900009031"
MOBILE_B = "13900009032"
MOBILE_C = "13900009033"
MOBILE_D = "13900009034"
FAKE_URL = ("http://192.168.1.4:48080/admin-api/infra/file/4/get/"
            "smoke_m10_%d.jpg" % int(time.time() * 1000))

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
    # 短信验证码记录一并清:频控(60s SEND_TOO_FAST)与 used 状态全存 system_sms_code 表,
    # 不删则脚本短间隔重跑必报「验证码已使用」(T1 首跑踩坑)
    cur.execute("DELETE FROM system_sms_code WHERE mobile IN (%s,%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    cur.execute(
        "DELETE FROM love_message WHERE sender_id IN "
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
    cur.execute("DELETE FROM love_message WHERE couple_id NOT IN "
                "(SELECT id FROM love_couple)")


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code",
        body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login",
            body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", ""), r


def send_image(token, image_url=FAKE_URL, omit_url=False):
    body = {} if omit_url else {"imageUrl": image_url}
    return api("app-api", "POST", "/love/message/send-image", token, body)


def message_list(token, last_id=None, limit=None):
    qs = []
    if last_id:
        qs.append("lastId=%s" % last_id)
    if limit:
        qs.append("limit=%s" % limit)
    path = "/love/message/list" + ("?" + "&".join(qs) if qs else "")
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

    # T3 A send-image(回包全量)
    r = send_image(token_a)
    d = r.get("data") or {}
    row_img = d.get("id")
    ok = (r.get("code") == 0 and row_img
          and d.get("coupleId") is not None
          and d.get("senderId") is not None and d.get("receiverId") is not None
          and d.get("kind") == 3 and d.get("content") == FAKE_URL
          and d.get("wishKey") in (None, "") and d.get("color") in (None, "")
          and d.get("icon") in (None, "")
          and d.get("auditStatus") == 1 and bool(d.get("createTime")))
    check("T3 A send-image 回包全量(kind=3)", ok,
          json.dumps(d, ensure_ascii=False)[:240])

    # T4 A 再发文本 → 双方 list 混序(message list 恒升序:旧→新,图片在前文本在后)
    r = api("app-api", "POST", "/love/message/send", token_a,
            {"content": "看到图了吗"})
    row_text = (r.get("data") or {}).get("id")
    check("T4a A send 文本", r.get("code") == 0 and row_text,
          "code=%s" % r.get("code"))
    conn.commit()
    r = message_list(token_a)
    rows = r.get("data") or []
    ok = (r.get("code") == 0 and len(rows) == 2
          and str(rows[0].get("id")) == str(row_img)
          and rows[0].get("kind") == 3 and rows[0].get("content") == FAKE_URL
          and str(rows[1].get("id")) == str(row_text)
          and rows[1].get("kind") == 1)
    check("T4b A list 混序(图片→文本,升序)", ok,
          "kinds=%s" % [(x.get("kind"), x.get("content", "")[:20]) for x in rows])
    r = message_list(token_b)
    rows = r.get("data") or []
    ok = (r.get("code") == 0 and len(rows) == 2
          and str(rows[0].get("id")) == str(row_img)
          and rows[0].get("content") == FAKE_URL)
    check("T4c B list 同见图片消息", ok, "len=%s" % len(rows))

    # T5 落库 kind=3
    conn.commit()  # 结束 REPEATABLE READ 旧快照再读
    cur.execute("SELECT kind, content, audit_status FROM love_message WHERE id = %s",
                (row_img,))
    row = cur.fetchone()
    check("T5 落库 kind=3/content=URL", row is not None and row[0] == 3
          and row[1] == FAKE_URL and row[2] == 1,
          "row=%s" % (repr(row)[:120],))

    # T6 imageUrl 空 → 400
    r = send_image(token_a, "")
    check("T6a 空串 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
    r = send_image(token_a, omit_url=True)
    check("T6b 缺字段 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
    r = send_image(token_a, "   ")
    check("T6c 纯空白 → 400", r.get("code") == 400, "code=%s" % r.get("code"))

    # T7 513 字 → 400("http://x/" 9 字符 + 504 = 513)
    r = send_image(token_a, "http://x/" + "a" * 504)
    check("T7 513 字 → 400", r.get("code") == 400, "code=%s" % r.get("code"))

    # T8 C 未绑定
    r = send_image(token_c)
    check("T8 C send-image 未绑定 → 1_003_004_003", r.get("code") == 1003004003,
          "code=%s msg=%s" % (r.get("code"), r.get("msg")))

    # T9 第二对 C+D:隔离
    r = api("app-api", "POST", "/love/couple/create-invite", token_c)
    invite2 = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", token_d, {"inviteCode": invite2})
    check("T9a 绑定 couple2", r.get("code") == 0 and bool(invite2),
          "code=%s" % r.get("code"))
    r = send_image(token_d)
    row_d = (r.get("data") or {}).get("id")
    check("T9b D send-image", r.get("code") == 0 and row_d,
          "code=%s" % r.get("code"))
    conn.commit()
    r = message_list(token_a)
    check("T9c couple1 list 仍 2 条(couple 隔离)",
          r.get("code") == 0 and len(r.get("data") or []) == 2,
          "len=%s" % len(r.get("data") or []))
    r = message_list(token_d)
    rows = r.get("data") or []
    ok = (r.get("code") == 0 and len(rows) == 1
          and str(rows[0].get("id")) == str(row_d) and rows[0].get("kind") == 3)
    check("T9d couple2 list 只见 D 那条", ok, "len=%s" % len(rows))

    # T10 admin 通道:content LIKE + kind=3 筛选
    r = api("admin-api", "POST", "/system/auth/login",
            body={"username": "admin", "password": "admin123"})
    admin_token = (r.get("data") or {}).get("accessToken", "")
    check("T10a admin 登录", r.get("code") == 0 and admin_token, "")
    r = api("admin-api", "GET",
            "/love/message/page?pageNo=1&pageSize=50&content=%s" % "smoke_m10_",
            admin_token)
    page = r.get("data") or {}
    items = page.get("list") or []
    ok = (r.get("code") == 0 and page.get("total") == 2 and items
          and all(x.get("kind") == 3 for x in items))
    check("T10b admin content LIKE 命中 2 条(kind=3)", ok,
          "total=%s" % page.get("total"))
    r = api("admin-api", "GET",
            "/love/message/page?pageNo=1&pageSize=50&kind=3", admin_token)
    page = r.get("data") or {}
    items = page.get("list") or []
    ok = (r.get("code") == 0 and page.get("total") == 2
          and all(str(x.get("content")).startswith("http") for x in items)
          and any(str(x.get("id")) == str(row_img) for x in items))
    check("T10c admin kind=3 筛选含 T3 行", ok, "total=%s" % page.get("total"))

    # T11 心愿时间线不被污染
    r = api("app-api", "GET", "/love/message/wish-list", token_b)
    rows = r.get("data") or []
    check("T11 B wish-list 无 kind=3", r.get("code") == 0
          and all(x.get("kind") == 2 for x in rows),
          "len=%s kinds=%s" % (len(rows), sorted({x.get("kind") for x in rows})))

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
