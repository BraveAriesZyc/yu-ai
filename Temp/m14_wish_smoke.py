# -*- coding: utf-8 -*-
"""
m14_wish_smoke.py — 恋爱清单(M14)全链路冒烟:love_wish 四端点 + 完成自动发消息 CAS + admin 通道 + 落库

前置:
  1. fat jar 含 wish 代码(mvn -pl yudao-server -am package 重打)
  2. 库已执行 love.sql M14 段(love_wish 表 + system_menu 三行, m14_apply_sql.py)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m14_wish_smoke.py 48081

断言:
  T1 五号 sms-login(9999;E 从不绑定,充当未绑定用例)
  T2 couple1=A+B, couple2=C+D
  T3 A create(标题含中文引号+emoji) → 对象回包 id/userId/done=False;落库 title 原样
  T4/T5 B list 见 A 的;A list 见 B 的;id 升序
  T6 B done(w1,true) → done=True/doneBy=B/doneTime>0;love_message +1 kind=1
     content="我们一起完成了「title」🎉" sender_id=B;A unread +1
  T7 A 重复 done(w1,true) → doneBy=B doneTime 不变;消息数不变(CAS rows=0 幂等)
  T8 A done(w1,false) → done=False;落库 done_by/done_time IS NULL;消息数不变
  T9 B re-done → 消息再 +1(undo→re-done 重发为预期,D-19b)
  T10 A delete B 创建的 → 1_003_009_001
  T11 各删自己创建的(含已完成 w1) → code=0;list 不含;落库 deleted=1
  T12 跨 couple done/delete(A 对 couple2 的 w3) → 均 1_003_009_000
  T13 不存在 id done/delete → 均 1_003_009_000
  T14 未绑定 E 四端点 → 均 1_003_004_003
  T15 无 token 四端点 → 均 401
  T16 title 64字 ok / 65字 400 / 空串 400 / 纯空白 400
  T17 admin page 筛选(coupleId/userId/createTime 段)+ delete 他人心愿 + admin delete 不存在 →
      过滤正确/不受 001 限制/app list 同步消失/000
  T18 done 回包形态:data 为 dict 且含 doneTime 键(toOk 对象形态守护)
  T19 cleanup 后零残留(love_wish/庆祝消息/sms code);system_menu 三行保留
"""
import json
import sys
import urllib.request
import urllib.error

import pymysql

# -I 隔离模式忽略 PYTHONIOENCODING,GBK 控制台打不出 emoji → 显式重配置
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILES = ["13900010031", "13900010032", "13900010033", "13900010034", "13900010035"]

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


conn = db()
cur = conn.cursor()


def cleanup():
    ph = ",".join(["%s"] * len(MOBILES))
    cur.execute("DELETE FROM system_sms_code WHERE mobile IN (%s)" % ph, MOBILES)
    cur.execute(
        "DELETE FROM love_message WHERE sender_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t)" % ph, MOBILES)
    cur.execute("DELETE FROM love_wish WHERE couple_id NOT IN (SELECT id FROM love_couple) "
                "OR couple_id IN (SELECT id FROM (SELECT id FROM love_couple WHERE user_a_id IN "
                "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t) "
                "OR user_b_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t)) x)"
                % (ph, ph), MOBILES + MOBILES)
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t) "
        "OR user_b_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t)" % (ph, ph),
        MOBILES + MOBILES)
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s)" % ph, MOBILES)
    cur.execute("DELETE FROM love_message WHERE couple_id NOT IN (SELECT id FROM love_couple)")


cleanup()
conn.commit()


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code", body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login", body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", "")


def unread_count(token):
    r = api("app-api", "GET", "/love/message/unread", token)
    assert r.get("code") == 0, "unread 失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return (r.get("data") or {}).get("unread", 0)


def msg_count(couple_id):
    conn.commit()
    cur.execute("SELECT COUNT(*) FROM love_message WHERE couple_id = %s", (couple_id,))
    return cur.fetchone()[0]


def wish_list(token):
    r = api("app-api", "GET", "/love/wish/list", token)
    assert r.get("code") == 0, "wish list 失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return r.get("data") or []


# T1 五号登录(E 从不绑定)
tok = {}
for i, m in enumerate(MOBILES):
    tok[m] = member_login(m)
tok_a, tok_b, tok_c, tok_d, tok_e = (tok[m] for m in MOBILES)
check("T1 五号 sms-login", all(tok.values()), "")

# T2 两 couple 绑定
r = api("app-api", "POST", "/love/couple/create-invite", tok_a)
invite1 = (r.get("data") or {}).get("inviteCode", "")
r = api("app-api", "POST", "/love/couple/bind", tok_b, {"inviteCode": invite1})
check("T2a 绑定 couple1", r.get("code") == 0 and bool(invite1), "code=%s" % r.get("code"))
r = api("app-api", "POST", "/love/couple/create-invite", tok_c)
invite2 = (r.get("data") or {}).get("inviteCode", "")
r = api("app-api", "POST", "/love/couple/bind", tok_d, {"inviteCode": invite2})
check("T2b 绑定 couple2", r.get("code") == 0, "code=%s" % r.get("code"))
cur.execute("SELECT id FROM love_couple WHERE user_a_id = (SELECT id FROM love_user WHERE mobile = %s)",
            (MOBILES[0],))
couple1 = cur.fetchone()[0]

# T3 A create(中文引号+emoji)
title1 = "一起去看「大海」🌊"
r = api("app-api", "POST", "/love/wish/create", tok_a, {"title": title1})
d = r.get("data") or {}
w1 = d.get("id")
check("T3a A create", r.get("code") == 0 and w1 and d.get("done") is False
      and d.get("userId") and d.get("createTime"), "code=%s id=%s" % (r.get("code"), w1))
conn.commit()
cur.execute("SELECT title, done = b'1', done_by, done_time, user_id FROM love_wish WHERE id = %s", (w1,))
row = cur.fetchone()
check("T3b 落库 title 原样 + 初始态", row and row[0] == title1 and row[1] == 0
      and row[2] is None and row[3] is None, "title=%s" % (row[0] if row else None))

# T4/T5 couple 互见 + id 升序
lst = wish_list(tok_b)
check("T4 B list 含 A 的 w1", any(str(x.get("id")) == str(w1) for x in lst),
      "n=%d" % len(lst))
r = api("app-api", "POST", "/love/wish/create", tok_b, {"title": "B 的心愿"})
w2 = (r.get("data") or {}).get("id")
check("T5a B create w2", r.get("code") == 0 and w2, "code=%s" % r.get("code"))
lst = wish_list(tok_a)
ids = [str(x.get("id")) for x in lst]
check("T5b A list 见 B 的 + id 升序", str(w1) in ids and str(w2) in ids
      and ids.index(str(w1)) < ids.index(str(w2)), "ids=%s" % ids)
conn.commit()  # RR 快照:HTTP 写后 DB 读前必须 commit 结束旧快照
cur.execute("SELECT user_id FROM love_wish WHERE id = %s", (w2,))
uid_b = cur.fetchone()[0]

# T6 B done(w1,true):庆祝消息 + A unread +1
before_unread_a = unread_count(tok_a)
before_msgs = msg_count(couple1)
r = api("app-api", "POST", "/love/wish/done", tok_b, {"id": w1, "done": True})
d = r.get("data") or {}
done_time_1 = d.get("doneTime")
check("T6a B done → 回包真值", r.get("code") == 0 and d.get("done") is True
      and str(d.get("doneBy")) == str(uid_b) and done_time_1, "code=%s doneBy=%s doneTime=%s"
      % (r.get("code"), d.get("doneBy"), done_time_1))
plus_msgs = msg_count(couple1)
check("T6b 庆祝消息 +1", plus_msgs == before_msgs + 1, "msg:%s→%s" % (before_msgs, plus_msgs))
cur.execute("SELECT kind, content, sender_id FROM love_message WHERE couple_id = %s "
            "ORDER BY id DESC LIMIT 1", (couple1,))
mrow = cur.fetchone()
expected = "我们一起完成了「" + title1 + "」🎉"
check("T6c 消息 kind=1 content sender=B", mrow and mrow[0] == 1 and mrow[1] == expected
      and str(mrow[2]) == str(uid_b), "content=%s" % (mrow[1] if mrow else None))
plus_unread_a = unread_count(tok_a)
check("T6d A unread +1", plus_unread_a == before_unread_a + 1,
      "%s→%s" % (before_unread_a, plus_unread_a))

# T7 A 重复 done(幂等,真值不变,消息不重发)
before_msgs = msg_count(couple1)
r = api("app-api", "POST", "/love/wish/done", tok_a, {"id": w1, "done": True})
d = r.get("data") or {}
check("T7 重复 done 幂等", r.get("code") == 0 and str(d.get("doneBy")) == str(uid_b)
      and d.get("doneTime") == done_time_1 and msg_count(couple1) == before_msgs,
      "doneBy=%s doneTime=%s" % (d.get("doneBy"), d.get("doneTime")))

# T8 A done(w1,false):清空三态,无新消息
before_msgs = msg_count(couple1)
r = api("app-api", "POST", "/love/wish/done", tok_a, {"id": w1, "done": False})
d = r.get("data") or {}
conn.commit()
cur.execute("SELECT done = b'1', done_by, done_time FROM love_wish WHERE id = %s", (w1,))
row = cur.fetchone()
check("T8 undo 清空 + 无新消息", r.get("code") == 0 and d.get("done") is False
      and row[0] == 0 and row[1] is None and row[2] is None
      and msg_count(couple1) == before_msgs, "db=%s" % (row,))

# T9 B re-done → 消息再 +1
before_msgs = msg_count(couple1)
r = api("app-api", "POST", "/love/wish/done", tok_b, {"id": w1, "done": True})
check("T9 re-done 消息重发", r.get("code") == 0 and (r.get("data") or {}).get("done") is True
      and msg_count(couple1) == before_msgs + 1, "code=%s" % r.get("code"))

# T10 A delete B 创建的 w2 → 1_003_009_001
r = api("app-api", "POST", "/love/wish/delete", tok_a, {"id": w2})
check("T10 删他人心愿 → 1_003_009_001", r.get("code") == 1003009001,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))

# T12 跨 couple(couple2 先造 w3)
r = api("app-api", "POST", "/love/wish/create", tok_d, {"title": "couple2 专属"})
w3 = (r.get("data") or {}).get("id")
check("T12a D create w3", r.get("code") == 0 and w3, "code=%s" % r.get("code"))
r = api("app-api", "POST", "/love/wish/done", tok_a, {"id": w3, "done": True})
c_done = r.get("code")
r2 = api("app-api", "POST", "/love/wish/delete", tok_a, {"id": w3})
c_del = r2.get("code")
check("T12b 跨 couple done/delete → 1_003_009_000", c_done == 1003009000 and c_del == 1003009000,
      "done=%s delete=%s" % (c_done, c_del))

# T13 不存在 id
r = api("app-api", "POST", "/love/wish/done", tok_a, {"id": 999999999, "done": True})
c1 = r.get("code")
r2 = api("app-api", "POST", "/love/wish/delete", tok_a, {"id": 999999999})
c2 = r2.get("code")
check("T13 不存在 → 1_003_009_000", c1 == 1003009000 and c2 == 1003009000,
      "done=%s delete=%s" % (c1, c2))

# T11 各删自己创建的(w1 已完成态也可删)
r = api("app-api", "POST", "/love/wish/delete", tok_a, {"id": w1})
c_a = r.get("code") if False else r.get("code")
r = api("app-api", "POST", "/love/wish/delete", tok_b, {"id": w2})
c_b = r.get("code")
check("T11a 各删自己的 → code=0", c_a == 0 and c_b == 0, "a=%s b=%s" % (c_a, c_b))
lst = wish_list(tok_a)
gone = not any(str(x.get("id")) in (str(w1), str(w2)) for x in lst)
conn.commit()
cur.execute("SELECT COUNT(*) FROM love_wish WHERE id IN (%s,%s) AND deleted = b'1'", (w1, w2))
check("T11b list 不含 + 落库 deleted=1", gone and cur.fetchone()[0] == 2, "n=%d" % len(lst))

# T14 未绑定 E 四端点 → 1_003_004_003
c1 = api("app-api", "GET", "/love/wish/list", tok_e).get("code")
c2 = api("app-api", "POST", "/love/wish/create", tok_e, {"title": "x"}).get("code")
c3 = api("app-api", "POST", "/love/wish/done", tok_e, {"id": 1, "done": True}).get("code")
c4 = api("app-api", "POST", "/love/wish/delete", tok_e, {"id": 1}).get("code")
check("T14 未绑定四端点 → 1_003_004_003",
      c1 == 1003004003 and c2 == 1003004003 and c3 == 1003004003 and c4 == 1003004003,
      "%s/%s/%s/%s" % (c1, c2, c3, c4))

# T15 无 token 四端点 → 401
c1 = api("app-api", "GET", "/love/wish/list").get("code")
c2 = api("app-api", "POST", "/love/wish/create", None, {"title": "x"}).get("code")
c3 = api("app-api", "POST", "/love/wish/done", None, {"id": 1, "done": True}).get("code")
c4 = api("app-api", "POST", "/love/wish/delete", None, {"id": 1}).get("code")
check("T15 无 token 四端点 → 401", c1 == 401 and c2 == 401 and c3 == 401 and c4 == 401,
      "%s/%s/%s/%s" % (c1, c2, c3, c4))

# T16 title 边界(64/65/空/纯空白)
t64 = "完" * 64
c1 = api("app-api", "POST", "/love/wish/create", tok_a, {"title": t64}).get("code")
w64 = (api("app-api", "GET", "/love/wish/list", tok_a).get("data") or [])
w64_id = next((str(x.get("id")) for x in w64 if x.get("title") == t64), None)
c2 = api("app-api", "POST", "/love/wish/create", tok_a, {"title": "超" * 65}).get("code")
c3 = api("app-api", "POST", "/love/wish/create", tok_a, {"title": ""}).get("code")
c4 = api("app-api", "POST", "/love/wish/create", tok_a, {"title": "   "}).get("code")
check("T16 title 64ok/65/空/空白 → 0/400/400/400",
      c1 == 0 and c2 == 400 and c3 == 400 and c4 == 400,
      "%s/%s/%s/%s w64=%s" % (c1, c2, c3, c4, w64_id))

# T17 admin page 筛选 + delete(兜底不受 001 限制)+ 不存在 000
r = api("admin-api", "POST", "/system/auth/login", body={"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken", "")
check("T17a admin 登录", r.get("code") == 0 and admin_token, "")
conn.commit()  # RR 快照:同理
cur.execute("SELECT id FROM love_user WHERE mobile = %s", (MOBILES[0],))
uid_a = cur.fetchone()[0]
r = api("admin-api", "GET",
        "/love/wish/page?pageNo=1&pageSize=50&coupleId=%s" % couple1, admin_token)
page = r.get("data") or {}
pg_list = page.get("list") or []
check("T17b page coupleId 过滤", r.get("code") == 0 and page.get("total", 0) >= 1
      and all(str(x.get("coupleId")) == str(couple1) for x in pg_list),
      "total=%s n=%d" % (page.get("total"), len(pg_list)))
r = api("admin-api", "GET",
        "/love/wish/page?pageNo=1&pageSize=50&userId=%s" % uid_a, admin_token)
pg2 = (r.get("data") or {}).get("list") or []
check("T17c page userId 过滤", r.get("code") == 0 and len(pg2) >= 1
      and all(str(x.get("userId")) == str(uid_a) for x in pg2), "n=%d" % len(pg2))
r = api("admin-api", "GET",
        "/love/wish/page?pageNo=1&pageSize=10"
        "&createTime%5B0%5D=2026-01-01%2000%3A00%3A00&createTime%5B1%5D=2026-01-02%2000%3A00%3A00",
        admin_token)
check("T17d createTime 窄段 → 0 条", r.get("code") == 0 and (r.get("data") or {}).get("total") == 0,
      "total=%s" % (r.get("data") or {}).get("total"))
r = api("admin-api", "DELETE", "/love/wish/delete?id=%s" % w64_id, admin_token)
check("T17e admin delete 他人心愿", r.get("code") == 0, "code=%s" % r.get("code"))
lst = wish_list(tok_a)
check("T17f app list 同步消失", not any(str(x.get("id")) == str(w64_id) for x in lst), "")
r = api("admin-api", "DELETE", "/love/wish/delete?id=999999999", admin_token)
check("T17g admin delete 不存在 → 1_003_009_000", r.get("code") == 1003009000,
      "code=%s" % r.get("code"))

# T18 done 回包形态(对象 + doneTime 键)
r = api("app-api", "POST", "/love/wish/create", tok_a, {"title": "形态校验心愿"})
w9 = (r.get("data") or {}).get("id")
r = api("app-api", "POST", "/love/wish/done", tok_b, {"id": w9, "done": True})
d = r.get("data")
check("T18 done 回包为对象含 doneTime", isinstance(d, dict) and "doneTime" in d
      and d.get("done") is True, "type=%s" % type(d).__name__)

# T19 cleanup 零残留 + 菜单三行保留
cleanup()
conn.commit()
cur.execute("SELECT COUNT(*) FROM love_wish WHERE couple_id = %s", (couple1,))
n_wish = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM love_message WHERE couple_id = %s", (couple1,))
n_msg = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM system_sms_code WHERE mobile IN (%s)"
            % ",".join(["%s"] * len(MOBILES)), MOBILES)
n_sms = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM system_menu WHERE component_name = 'LoveWish' "
            "OR permission IN ('love:wish:query', 'love:wish:delete')")
n_menu = cur.fetchone()[0]
check("T19 cleanup 零残留 + 菜单保留", n_wish == 0 and n_msg == 0 and n_sms == 0 and n_menu == 3,
      "wish=%s msg=%s sms=%s menu=%s" % (n_wish, n_msg, n_sms, n_menu))
conn.close()

print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
if failed:
    print("失败项:", failed)
    sys.exit(1)
