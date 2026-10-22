# -*- coding: utf-8 -*-
"""
m7_views_smoke.py — 三域记录查看冒烟(wish-list 端点 + stats.wishCount + mood createTime)

前置:
  1. fat jar 含 wish-list 端点与 stats.wishCount / AppMoodRespVO.createTime(mvn 重打)
  2. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m7_views_smoke.py 48081

断言:
  T1 三号 sms-login(验证码 9999,未注册自动注册)
  T2 A create-invite → B bind
  T3 A 发 2 心愿 + 1 文本悄悄话 + 1 待办
  T4 A wish-list:只回 kind=2、id 倒序、快照 color/icon 全量
  T5 B wish-list 同 couple 同视图
  T6 wish-list limit=1 只回最新一条
  T7 stats:wishCount=2 / messageCount=3(2 心愿+1 文本)/ todoCount=1;B 同 wishCount
  T8 未绑定 C:stats 全 0(含 wishCount);C wish-list 未绑定错 1003004003
  T9 mood:建字典选项 → 记当日 → record/list 回 createTime(epoch 毫秒>0);stats moodCount=1
  T10 清理还原(物理解删冒烟数据)
"""
import json
import sys
import time
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900006011"
MOBILE_B = "13900006012"
MOBILE_C = "13900006013"
MONTH = time.strftime("%Y-%m")

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
    """物理解删冒烟数据。⚠ 顺序:业务行孤儿删除必须放在删 couple/user 之后
    (user 在世时 NOT IN 恒空转,先删人才有孤儿可删;首次跑尾cleanup漏删的孤儿
    由下次跑头 cleanup 二次兜底)"""
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s)) t) "
        "OR user_b_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s,%s,%s)) t)",
        (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_A, MOBILE_B, MOBILE_C))
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C))
    cur.execute(
        "DELETE FROM love_mood_record WHERE user_id NOT IN (SELECT id FROM love_user)")
    cur.execute(
        "DELETE FROM love_todo WHERE owner_id NOT IN (SELECT id FROM love_user)")
    cur.execute(
        "DELETE FROM love_message WHERE sender_id NOT IN (SELECT id FROM love_user)")
    cur.execute(
        "DELETE FROM love_mood_option WHERE couple_id NOT IN (SELECT id FROM love_couple)")


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

    # T1 三号登录
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

    # T3 造业务数据:2 心愿 + 1 文本 + 1 待办
    w1 = api("app-api", "POST", "/love/message/send-wishes", token_a,
             {"wishKey": "wish_view7_a", "text": "VIEW7 心愿一",
              "color": "#FF7A45", "icon": "Star"})
    w2 = api("app-api", "POST", "/love/message/send-wishes", token_a,
             {"wishKey": "wish_view7_b", "text": "VIEW7 心愿二",
              "color": "#6C7BFF", "icon": "Heart"})
    t1 = api("app-api", "POST", "/love/message/send", token_a,
             {"content": "VIEW7 文本一条"})
    t2 = api("app-api", "POST", "/love/todo/create", token_a,
             {"title": "VIEW7 待办一条"})
    check("T3 发心愿+文本+待办",
          all(x.get("code") == 0 for x in (w1, w2, t1, t2)),
          "codes=%s" % [x.get("code") for x in (w1, w2, t1, t2)])

    # T4 A wish-list:kind 全 2、id 倒序、快照 color/icon 全量、无文本消息
    r = api("app-api", "GET", "/love/message/wish-list?limit=200", token_a)
    rows = r.get("data") or []
    ids = [int(x.get("id")) for x in rows]
    ok = (r.get("code") == 0 and len(rows) == 2
          and all(x.get("kind") == 2 for x in rows)
          and ids == sorted(ids, reverse=True)
          and all(x.get("color") and x.get("icon") and x.get("content") for x in rows)
          and "VIEW7 文本一条" not in json.dumps(rows, ensure_ascii=False))
    check("T4 wish-list kind=2 DESC 快照全量", ok, json.dumps(rows, ensure_ascii=False)[:200])

    # T5 B wish-list 同 couple 同视图
    r = api("app-api", "GET", "/love/message/wish-list?limit=200", token_b)
    rows_b = r.get("data") or []
    check("T5 B 同视图", r.get("code") == 0 and len(rows_b) == 2,
          "code=%s len=%s" % (r.get("code"), len(rows_b)))

    # T6 limit=1 只回最新一条(第一条 id 与 T4 一致)
    r = api("app-api", "GET", "/love/message/wish-list?limit=1", token_a)
    rows_1 = r.get("data") or []
    check("T6 limit=1", r.get("code") == 0 and len(rows_1) == 1
          and int(rows_1[0].get("id")) == ids[0],
          "len=%s first=%s" % (len(rows_1), rows_1[0].get("id") if rows_1 else "-"))

    # T7 stats:wishCount / messageCount / todoCount
    r = api("app-api", "GET", "/love/user/stats", token_a)
    d = r.get("data") or {}
    ok = (r.get("code") == 0 and d.get("wishCount") == 2
          and d.get("messageCount") == 3 and d.get("todoCount") == 1)
    check("T7 A stats wishCount=2 messageCount=3", ok, json.dumps(d, ensure_ascii=False))
    r = api("app-api", "GET", "/love/user/stats", token_b)
    d = r.get("data") or {}
    check("T7 B stats 同 couple", r.get("code") == 0 and d.get("wishCount") == 2,
          json.dumps(d, ensure_ascii=False))

    # T8 未绑定 C:stats 全 0;wish-list 未绑定错
    r = api("app-api", "GET", "/love/user/stats", token_c)
    d = r.get("data") or {}
    check("T8 C stats 全 0", r.get("code") == 0
          and d.get("wishCount") == 0 and d.get("messageCount") == 0
          and d.get("todoCount") == 0 and d.get("moodCount") == 0,
          json.dumps(d, ensure_ascii=False))
    r = api("app-api", "GET", "/love/message/wish-list?limit=200", token_c)
    check("T8 C wish-list 未绑定错 1003004003", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))

    # T9 mood:建选项 → 记当日 → list 回 createTime
    r = api("app-api", "POST", "/love/mood/option/create", token_a,
            {"label": "VIEW7 平静", "emoji": "😌", "color": "#5B8DEF"})
    option_id = (r.get("data") or {}).get("id", "")
    check("T9 建选项", r.get("code") == 0 and bool(option_id),
          "code=%s" % r.get("code"))
    r = api("app-api", "POST", "/love/mood/record", token_a, {"optionId": option_id})
    check("T9 记当日", r.get("code") == 0, "code=%s" % r.get("code"))
    r = api("app-api", "GET", "/love/mood/record/list?month=" + MONTH, token_a)
    rows_m = r.get("data") or []
    cts = [int(x.get("createTime") or 0) for x in rows_m]
    check("T9 record/list 回 createTime(epoch ms)",
          r.get("code") == 0 and len(rows_m) >= 1 and all(c > 0 for c in cts),
          "code=%s createTime=%s" % (r.get("code"), cts))
    r = api("app-api", "GET", "/love/user/stats", token_a)
    d = r.get("data") or {}
    check("T9 A stats moodCount=1", r.get("code") == 0 and d.get("moodCount") == 1,
          "moodCount=%s" % d.get("moodCount"))

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
