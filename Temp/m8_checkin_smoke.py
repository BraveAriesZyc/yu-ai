# -*- coding: utf-8 -*-
"""
m8_checkin_smoke.py — 每日打卡(M8)全链路冒烟:app 双通道 + admin 通道

前置:
  1. fat jar 含 checkin 代码(mvn -pl yudao-server -am package 重打)
  2. dev 库已执行 love.sql M8 段(表 + 菜单)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m8_checkin_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 A create-invite → B bind(couple1)
  T3 A check:回包全量(id/userId/checkinDate=今天字符串/createTime)
  T4 A 重复 check 幂等(同 id 同 checkinDate,不打错)
  T5 B check(userId=B)
  T6 summary:commonStreak=1 / myTotal=1 / partnerTotal=1
  T7 list:当月 2 条(checkinDate 字符串) / 上月 0 条 / 非法 month → 400
  T8 C 未绑定:check/list/summary → 1_003_004_003
  T9 第二对 C+D:D check 后 couple1 list 不受影响(按 couple 过滤,天然隔离)
  T10 跨日 streak(DB 直插历史行):插昨日 A+B → streak=2;删昨日 B 行 → streak=1
  T11 admin:page(userId 精确+日期段)→ delete A 今日行 → A list 剩 1 →
      A 重打 → 复活(同 id,1_003_007_000 不出现)→ 删不存在 → 1_003_007_000
  T12 清理还原(物理删;孤儿清理放删 couple/user 之后)
"""
import datetime
import json
import sys
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900007031"
MOBILE_B = "13900007032"
MOBILE_C = "13900007033"
MOBILE_D = "13900007034"

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


def user_ids(cur):
    cur.execute("SELECT id FROM love_user WHERE mobile IN (%s,%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    return [r[0] for r in cur.fetchall()]


def cleanup(cur):
    """物理解删冒烟数据。⚠ 顺序:业务行 → couple/user → 孤儿兜底(在世时 NOT IN 恒空转)"""
    cur.execute(
        "DELETE FROM love_checkin WHERE user_id IN "
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
    cur.execute("DELETE FROM love_checkin WHERE couple_id NOT IN "
                "(SELECT id FROM love_couple)")


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code",
        body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login",
            body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", ""), r


def checkin(token):
    return api("app-api", "POST", "/love/checkin/check", token, {})


def summary(token):
    return api("app-api", "GET", "/love/checkin/summary", token)


def checkin_list(token, month=None):
    path = "/love/checkin/list" + ("?month=" + month if month else "")
    return api("app-api", "GET", path, token)


def main():
    today = datetime.date.today().isoformat()
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

    # T3 A check(回包全量)
    r = checkin(token_a)
    d = r.get("data") or {}
    row_a = d.get("id")
    ok = (r.get("code") == 0 and row_a and d.get("userId") is not None
          and d.get("checkinDate") == today and bool(d.get("createTime")))
    check("T3 A check 回包全量(checkinDate=今天字符串)", ok,
          json.dumps(d, ensure_ascii=False)[:160])

    # T4 A 重复 check 幂等(同 id)
    r = checkin(token_a)
    d4 = r.get("data") or {}
    check("T4 A 重复 check 幂等", r.get("code") == 0 and d4.get("id") == row_a,
          "id=%s vs %s" % (d4.get("id"), row_a))

    # T5 B check
    r = checkin(token_b)
    d5 = r.get("data") or {}
    row_b = d5.get("id")
    check("T5 B check", r.get("code") == 0 and row_b and row_b != row_a,
          "id=%s" % row_b)

    # T6 summary:共同连续=1
    r = summary(token_a)
    s = r.get("data") or {}
    check("T6 summary streak=1/1/1",
          r.get("code") == 0 and s.get("commonStreak") == 1
          and s.get("myTotal") == 1 and s.get("partnerTotal") == 1,
          json.dumps(s))

    # T7 list 月过滤
    conn.commit()  # 结束 REPEATABLE READ 旧快照再读
    r = checkin_list(token_a)
    rows = r.get("data") or []
    ok = (r.get("code") == 0 and len(rows) == 2
          and all(x.get("checkinDate") == today for x in rows)
          and all(x.get("userId") is not None and x.get("id") for x in rows))
    check("T7a list 当月 2 条(双方)", ok, "len=%s" % len(rows))
    r = checkin_list(token_a, "2026-01")
    check("T7b list 上月 0 条", r.get("code") == 0
          and len(r.get("data") or []) == 0,
          "len=%s" % len(r.get("data") or []))
    r = checkin_list(token_a, "2026/10")
    check("T7c list 非法 month → 400", r.get("code") == 400,
          "code=%s" % r.get("code"))

    # T8 C 未绑定
    r = checkin(token_c)
    check("T8a C check 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))
    r = checkin_list(token_c)
    check("T8b C list 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))
    r = summary(token_c)
    check("T8c C summary 未绑定错", r.get("code") == 1003004003,
          "code=%s" % r.get("code"))

    # T9 第二对 C+D:D check 后 couple1 视图不受影响
    r = api("app-api", "POST", "/love/couple/create-invite", token_c)
    invite2 = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", token_d, {"inviteCode": invite2})
    check("T9a 绑定 couple2", r.get("code") == 0 and bool(invite2),
          "code=%s" % r.get("code"))
    r = checkin(token_d)
    check("T9b D check", r.get("code") == 0 and (r.get("data") or {}).get("id"),
          "code=%s" % r.get("code"))
    conn.commit()
    r = checkin_list(token_a)
    check("T9c couple1 list 仍 2 条(couple 隔离)",
          r.get("code") == 0 and len(r.get("data") or []) == 2,
          "len=%s" % len(r.get("data") or []))
    r = summary(token_d)
    s = r.get("data") or {}
    check("T9d couple2 summary D 单方(未共同点亮)",
          r.get("code") == 0 and s.get("commonStreak") == 0
          and s.get("myTotal") == 1 and s.get("partnerTotal") == 0,
          json.dumps(s))

    # T10 跨日 streak(DB 直插历史行;uk 与业务口径同源)
    uids = {}
    cur.execute("SELECT mobile, id FROM love_user WHERE mobile IN (%s,%s,%s,%s)",
                (MOBILE_A, MOBILE_B, MOBILE_C, MOBILE_D))
    for mobile, uid in cur.fetchall():
        uids[mobile] = uid
    cur.execute(
        "SELECT id FROM love_couple WHERE user_a_id=%s AND user_b_id=%s",
        (uids[MOBILE_A], uids[MOBILE_B]))
    couple1 = cur.fetchone()[0]
    cur.execute(
        "INSERT INTO love_checkin (couple_id, user_id, checkin_date, creator, updater) "
        "VALUES (%s,%s, CURDATE() - INTERVAL 1 DAY, 'smoke', 'smoke'), "
        "(%s,%s, CURDATE() - INTERVAL 1 DAY, 'smoke', 'smoke')",
        (couple1, uids[MOBILE_A], couple1, uids[MOBILE_B]))
    conn.commit()
    r = summary(token_a)
    s = r.get("data") or {}
    check("T10a 插昨日双方 → streak=2",
          r.get("code") == 0 and s.get("commonStreak") == 2
          and s.get("myTotal") == 2 and s.get("partnerTotal") == 2,
          json.dumps(s))
    cur.execute(
        "DELETE FROM love_checkin WHERE user_id=%s "
        "AND checkin_date = CURDATE() - INTERVAL 1 DAY", (uids[MOBILE_B],))
    conn.commit()
    r = summary(token_a)
    s = r.get("data") or {}
    check("T10b 删昨日 B → streak=1(昨日断档)",
          r.get("code") == 0 and s.get("commonStreak") == 1
          and s.get("myTotal") == 2 and s.get("partnerTotal") == 1,
          json.dumps(s))
    # 清掉直插的昨日 A 行,后续 T11 的 list 口径回到「今天 2 条」
    cur.execute(
        "DELETE FROM love_checkin WHERE user_id=%s "
        "AND checkin_date = CURDATE() - INTERVAL 1 DAY", (uids[MOBILE_A],))
    conn.commit()

    # T11 admin 通道
    r = api("admin-api", "POST", "/system/auth/login",
            body={"username": "admin", "password": "admin123"})
    admin_token = (r.get("data") or {}).get("accessToken", "")
    check("T11a admin 登录", r.get("code") == 0 and admin_token, "")
    r = api("admin-api", "GET",
            "/love/checkin/page?pageNo=1&pageSize=50&userId=%s&checkinDate%%5B0%%5D=%s&checkinDate%%5B1%%5D=%s"
            % (uids[MOBILE_A], today, today), admin_token)
    page = r.get("data") or {}
    items = page.get("list") or []
    ok = (r.get("code") == 0 and page.get("total") == 1 and items
          and str(items[0].get("id")) == str(row_a)
          and items[0].get("checkinDate") == today
          and items[0].get("coupleId") is not None)
    check("T11b admin page userId 精确+日期段 1 条", ok,
          "total=%s" % page.get("total"))
    r = api("admin-api", "DELETE", "/love/checkin/delete?id=%s" % row_a, admin_token)
    check("T11c admin delete", r.get("code") == 0, "code=%s" % r.get("code"))
    conn.commit()
    r = checkin_list(token_a)
    check("T11d admin 删除后 A list 剩 1(仅 B)", r.get("code") == 0
          and len(r.get("data") or []) == 1,
          "len=%s" % len(r.get("data") or []))
    r = checkin(token_a)
    d11 = r.get("data") or {}
    check("T11e admin 删除后 A 重打 → 复活(同 id)",
          r.get("code") == 0 and d11.get("id") == row_a,
          "id=%s vs %s" % (d11.get("id"), row_a))
    r = api("admin-api", "DELETE", "/love/checkin/delete?id=%s" % row_a, admin_token)
    check("T11f admin 删复活行 code=0", r.get("code") == 0,
          "code=%s" % r.get("code"))
    r = api("admin-api", "DELETE", "/love/checkin/delete?id=999999999", admin_token)
    check("T11g admin 删不存在 → 1_003_007_000", r.get("code") == 1003007000,
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
