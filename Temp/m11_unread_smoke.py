# -*- coding: utf-8 -*-
"""
m11_unread_smoke.py — 聊天未读红点(M11)全链路冒烟:unread/read 双端点 + admin 通道 + 落库

前置:
  1. fat jar 含 unread/read 代码(mvn -pl yudao-server -am package 重打)
  2. dev 库已执行 love.sql M11 段(love_user.chat_read_cursor bigint NOT NULL DEFAULT 0)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m11_unread_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 A create-invite → B bind(couple1)
  T3 S1 A 发文本 → B unread 差值 +1,回包 data 为对象 {"unread":n}(形态断言,禁标量)
  T4 S2 B read(m1) → data=true;unread 差值归 0
  T5 S3 落库 love_user.chat_read_cursor = m1
  T6 S4 GREATEST 防回退:read(m2) 后 read(m1) 游标仍 m2、unread 不回弹
  T7 S5 自己发不计未读:A 连发 3 条 → A 差值 0 / B 差值 3
  T8 S6 未绑定 C:unread code=0 且 unread=0;read → 1_003_004_003
  T9 S7 kind=2/3 全计:A send-wishes + send-image → B 差值 2
  T10 S8 admin 逻辑删一条 B 未读消息 → B unread 差值 -1
  T11 S9 couple 隔离:C+D 绑 couple2,D 发 → C 差值 1;B read 不改 C 游标
  T12 S10 read {} / 0 / -1 → 400;未登录 unread → 401
  T13 S11 解绑重绑:A 解绑 → 与 B 重绑新 couple → B 发 → A 计新 couple 消息不误报
  T14 清理还原
"""
import json
import sys
import urllib.request
import urllib.error

import pymysql

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE_A = "13900010031"
MOBILE_B = "13900010032"
MOBILE_C = "13900010033"
MOBILE_D = "13900010034"

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
    return (r.get("data") or {}).get("accessToken", "")


def unread(token):
    return api("app-api", "GET", "/love/message/unread", token)


def unread_count(token):
    r = unread(token)
    assert r.get("code") == 0, "unread 调用失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return (r.get("data") or {}).get("unread", 0)


def mark_read(token, mid):
    return api("app-api", "POST", "/love/message/read", token,
               {"lastMessageId": mid})


def send_text(token, content):
    return api("app-api", "POST", "/love/message/send", token, {"content": content})


def cursor_of(cur, mobile):
    conn.commit()
    cur.execute(
        "SELECT chat_read_cursor FROM love_user WHERE mobile = %s", (mobile,))
    row = cur.fetchone()
    return row[0] if row else None


def user_id_of(cur, mobile):
    cur.execute("SELECT id FROM love_user WHERE mobile = %s", (mobile,))
    row = cur.fetchone()
    return row[0] if row else None


conn = db()
cur = conn.cursor()
cleanup(cur)
conn.commit()

# T1 四号登录
tok_a = member_login(MOBILE_A)
tok_b = member_login(MOBILE_B)
tok_c = member_login(MOBILE_C)
tok_d = member_login(MOBILE_D)
check("T1 四号 sms-login", all([tok_a, tok_b, tok_c, tok_d]), "")

# T2 couple1 = A+B
r = api("app-api", "POST", "/love/couple/create-invite", tok_a)
invite = (r.get("data") or {}).get("inviteCode", "")
r = api("app-api", "POST", "/love/couple/bind", tok_b, {"inviteCode": invite})
check("T2 绑定 couple1", r.get("code") == 0 and bool(invite),
      "code=%s" % r.get("code"))

# T3 S1:A 发文本 → B unread +1(data 对象形态)
base_b = unread_count(tok_b)
r = send_text(tok_a, "红点冒烟-1")
m1 = (r.get("data") or {}).get("id")
check("T3a A send 文本", r.get("code") == 0 and m1, "code=%s" % r.get("code"))
conn.commit()
r = unread(tok_b)
d = r.get("data")
ok = (r.get("code") == 0 and isinstance(d, dict) and "unread" in d
      and d.get("unread") == base_b + 1)
check("T3b B unread 差值+1 且 data 为对象", ok,
      "raw=%s base=%s" % (json.dumps(d), base_b))

# T4 S2:B read(m1) → true;unread 归零(差值 0)
r = mark_read(tok_b, m1)
check("T4a B read → data=true", r.get("code") == 0 and r.get("data") is True,
      "code=%s data=%s" % (r.get("code"), r.get("data")))
conn.commit()
now_b = unread_count(tok_b)
check("T4b B unread 差值 0", now_b == base_b, "base=%s now=%s" % (base_b, now_b))

# T5 S3:落库游标 = m1
conn.commit()
cur.execute("SELECT chat_read_cursor FROM love_user WHERE mobile = %s", (MOBILE_B,))
row = cur.fetchone()
check("T5 B 落库 chat_read_cursor = m1",
      row is not None and str(row[0]) == str(m1),
      "cursor=%s m1=%s" % (row[0] if row else None, m1))

# T6 S4:GREATEST 防回退(read 旧 id 不回退)
r = send_text(tok_a, "红点冒烟-2")
m2 = (r.get("data") or {}).get("id")
check("T6a A send 第二条", r.get("code") == 0 and m2, "code=%s" % r.get("code"))
r = mark_read(tok_b, m2)
check("T6b B read(m2)", r.get("code") == 0, "code=%s" % r.get("code"))
r = mark_read(tok_b, m1)  # 迟到/重放旧游标
check("T6c B read(m1) 旧 id 也 200", r.get("code") == 0, "code=%s" % r.get("code"))
conn.commit()
cur.execute("SELECT chat_read_cursor FROM love_user WHERE mobile = %s", (MOBILE_B,))
row = cur.fetchone()
now_b = unread_count(tok_b)
ok = (row is not None and str(row[0]) == str(m2) and now_b == base_b)
check("T6d 游标仍 m2 且 unread 不回弹", ok,
      "cursor=%s m2=%s unread=%s base=%s" % (row[0] if row else None, m2, now_b, base_b))

# T7 S5:自己发不计未读
base_a = unread_count(tok_a)
ids = []
for i in range(3):
    r = send_text(tok_a, "自发自测-%d" % i)
    ids.append((r.get("data") or {}).get("id"))
conn.commit()
now_a = unread_count(tok_a)
now_b2 = unread_count(tok_b)
ok = (now_a == base_a and now_b2 == base_b + 3
      and all(ids))
check("T7 A 自发 3 条:A 差值 0 / B 差值 3", ok,
      "a:%s→%s b:%s→%s" % (base_a, now_a, base_b, now_b2))

# T8 S6:未绑定 C(unread 0 / read 报错)
r = unread(tok_c)
d = r.get("data")
check("T8a C 未绑定 unread → code=0 unread=0",
      r.get("code") == 0 and isinstance(d, dict) and d.get("unread") == 0,
      "raw=%s" % json.dumps(d))
r = mark_read(tok_c, 999999999)
check("T8b C 未绑定 read → 1_003_004_003", r.get("code") == 1003004003,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))

# T9 S7:kind=2/3 全计
r = api("app-api", "POST", "/love/message/send-wishes", tok_a,
        {"wishKey": "smoke_w1", "text": "红点心愿", "color": "#FF5A8C", "icon": "Star"})
check("T9a A send-wishes", r.get("code") == 0, "code=%s" % r.get("code"))
r = api("app-api", "POST", "/love/message/send-image", tok_a,
        {"imageUrl": "http://192.168.1.4:48080/admin-api/infra/file/4/get/smoke_m11.jpg"})
check("T9b A send-image", r.get("code") == 0, "code=%s" % r.get("code"))
conn.commit()
now_b3 = unread_count(tok_b)
# B 在 T6 后已读至 m2,T7 后有 3 条文本未读;T9 加 wish+image 后应累计 base_b+3+2
ok = now_b3 == base_b + 3 + 2
check("T9c B unread 累计校验(+3 文本 +2 wish/image 全计)", ok,
      "b:%s→%s(期望 %s)" % (base_b, now_b3, base_b + 5))

# T10 S8:admin 逻辑删一条 B 未读 → 差值 -1
r = api("admin-api", "POST", "/system/auth/login",
        body={"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken", "")
check("T10a admin 登录", r.get("code") == 0 and admin_token, "")
victim = ids[0] if ids and ids[0] else m2
r = api("admin-api", "DELETE", "/love/message/delete?id=%s" % victim, admin_token)
check("T10b admin delete 未读消息", r.get("code") == 0, "code=%s" % r.get("code"))
conn.commit()
now_b4 = unread_count(tok_b)
check("T10c B unread 差值 -1(admin 删不计)", now_b4 == now_b3 - 1,
      "b:%s→%s" % (now_b3, now_b4))

# T11 S9:couple2 = C+D 隔离
r = api("app-api", "POST", "/love/couple/create-invite", tok_c)
invite2 = (r.get("data") or {}).get("inviteCode", "")
r = api("app-api", "POST", "/love/couple/bind", tok_d, {"inviteCode": invite2})
check("T11a 绑定 couple2", r.get("code") == 0, "code=%s" % r.get("code"))
base_c = unread_count(tok_c)
r = send_text(tok_d, "couple2 专属")
check("T11b D send", r.get("code") == 0, "code=%s" % r.get("code"))
conn.commit()
now_c = unread_count(tok_c)
check("T11c C unread 差值 +1(couple 隔离)", now_c == base_c + 1,
      "c:%s→%s" % (base_c, now_c))
mark_read(tok_b, m2)  # B 的 read 不应影响 C 的游标
conn.commit()
cur.execute("SELECT chat_read_cursor FROM love_user WHERE mobile = %s", (MOBILE_C,))
row_c = cur.fetchone()
cur.execute("SELECT chat_read_cursor FROM love_user WHERE mobile = %s", (MOBILE_B,))
row_b = cur.fetchone()
check("T11d B read 不改 C 游标(双查)",
      row_c is not None and row_b is not None
      and str(row_c[0]) == "0" and str(row_b[0]) == str(m2),
      "c_cursor=%s b_cursor=%s" % (row_c[0] if row_c else None,
                                   row_b[0] if row_b else None))

# T12 S10:参数校验 + 未登录
r = mark_read(tok_b, None)
check("T12a read 缺字段 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
r = mark_read(tok_b, 0)
check("T12b read 0 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
r = mark_read(tok_b, -1)
check("T12c read -1 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
r = api("app-api", "GET", "/love/message/unread")
check("T12d 未登录 unread → 401", r.get("code") == 401,
      "code=%s" % r.get("code"))

# T13 S11:解绑重绑(A 解绑 B → 重新绑定 → 新 couple 消息计入未读)
r = api("app-api", "POST", "/love/couple/unbind", tok_a)
check("T13a A unbind", r.get("code") == 0, "code=%s" % r.get("code"))
r = api("app-api", "POST", "/love/couple/create-invite", tok_a)
invite3 = (r.get("data") or {}).get("inviteCode", "")
r = api("app-api", "POST", "/love/couple/bind", tok_b, {"inviteCode": invite3})
check("T13b A+B 重绑新 couple", r.get("code") == 0, "code=%s" % r.get("code"))
r = send_text(tok_b, "新 couple 的第一条")
check("T13c B send 新 couple", r.get("code") == 0, "code=%s" % r.get("code"))
conn.commit()
now_a = unread_count(tok_a)
ok = now_a == base_a + 1  # 新 couple 消息 id 必大于旧游标(m2 前的 base_a 段),自然衔接
check("T13d A unread 计新 couple 消息(旧游标不干扰)", ok,
      "a:%s→%s" % (base_a, now_a))

# T14 清理
cleanup(cur)
conn.commit()
check("T14 清理还原", True)
conn.close()

print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
if failed:
    print("失败项:", failed)
    sys.exit(1)
