# -*- coding: utf-8 -*-
"""
m6_stats_smoke.py — 恋爱统计端点冒烟(GET /app-api/love/user/stats,统计真数据化里程碑)

前置:
  1. fat jar 含 stats 端点(mvn -pl yudao-server -am package 重打)
  2. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m6_stats_smoke.py 48081

断言:
  T1 双号 sms-login(local 验证码 9999,未注册自动注册)
  T2 A create-invite → B bind 绑定成功
  T3 A 发 2 条悄悄话 + 建 1 条待办
  T4 A stats:bindDays=1(含当天) / messageCount=2 / todoCount=1 / moodCount=0
  T5 B stats 同 couple 同口径(messageCount=2)
  T6 未绑定号 C stats:四项全 0
  T7 清理还原(物理解删冒烟数据)
"""
import json
import sys
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900006001"
MOBILE_B = "13900006002"
MOBILE_C = "13900006003"

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
    """物理解删冒烟数据(先 couple 后 user 再业务行,按专用手机号段)"""
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s)) t) "
        "OR user_b_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s)) t)",
        (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_A, MOBILE_B, MOBILE_C))
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C))
    cur.execute(
        "DELETE FROM love_todo WHERE owner_id NOT IN (SELECT id FROM love_user) "
        "AND title LIKE %s", ("STATS6%",))
    cur.execute(
        "DELETE FROM love_message WHERE sender_id NOT IN (SELECT id FROM love_user) "
        "AND content LIKE %s", ("STATS6%",))
    cur.execute(
        "DELETE FROM love_todo WHERE owner_id NOT IN (SELECT id FROM love_user)")
    cur.execute(
        "DELETE FROM love_message WHERE sender_id NOT IN (SELECT id FROM love_user)")


def member_login(mobile):
    """send-sms-code → sms-login(未注册自动注册),返回 accessToken"""
    api("app-api", "POST", "/love/auth/send-sms-code",
        body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login",
            body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", ""), r


def main():
    conn = db()
    cur = conn.cursor()
    cleanup(cur)
    conn.commit()

    # T1 双号登录
    token_a, ra = member_login(MOBILE_A)
    token_b, rb = member_login(MOBILE_B)
    token_c, rc = member_login(MOBILE_C)
    check("T1 三号 sms-login", all([token_a, token_b, token_c]),
          "" if all([token_a, token_b, token_c])
          else json.dumps(ra, ensure_ascii=False)[:120])

    # T2 绑定
    r = api("app-api", "POST", "/love/couple/create-invite", token_a)
    invite = (r.get("data") or {}).get("inviteCode", "") or (r.get("data") or {}).get("invite_code", "")
    r = api("app-api", "POST", "/love/couple/bind", token_b, {"inviteCode": invite})
    check("T2 绑定", r.get("code") == 0 and bool(invite),
          "code=%s" % r.get("code"))

    # T3 造业务数据
    r1 = api("app-api", "POST", "/love/message/send", token_a, {"content": "STATS6 消息一"})
    r2 = api("app-api", "POST", "/love/message/send", token_a, {"content": "STATS6 消息二"})
    r3 = api("app-api", "POST", "/love/todo/create", token_a, {"title": "STATS6 待办一"})
    check("T3 发消息+建待办",
          all(x.get("code") == 0 for x in (r1, r2, r3)),
          "codes=%s" % [x.get("code") for x in (r1, r2, r3)])

    # T4 A stats
    r = api("app-api", "GET", "/love/user/stats", token_a)
    d = r.get("data") or {}
    ok = (r.get("code") == 0 and d.get("bindDays") == 1
          and d.get("messageCount") == 2 and d.get("todoCount") == 1
          and d.get("moodCount") == 0)
    check("T4 A stats 四项", ok, json.dumps(d, ensure_ascii=False))

    # T5 B stats 同口径
    r = api("app-api", "GET", "/love/user/stats", token_b)
    d = r.get("data") or {}
    check("T5 B stats 同 couple", r.get("code") == 0
          and d.get("bindDays") == 1 and d.get("messageCount") == 2,
          json.dumps(d, ensure_ascii=False))

    # T6 未绑定 C 全 0
    r = api("app-api", "GET", "/love/user/stats", token_c)
    d = r.get("data") or {}
    check("T6 未绑定全 0", r.get("code") == 0
          and d.get("bindDays") == 0 and d.get("messageCount") == 0
          and d.get("todoCount") == 0 and d.get("moodCount") == 0,
          json.dumps(d, ensure_ascii=False))

    # T7 清理
    cleanup(cur)
    conn.commit()
    check("T7 清理还原", True)
    conn.close()

    print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
    if failed:
        print("失败项:", failed)
        sys.exit(1)


if __name__ == "__main__":
    main()
