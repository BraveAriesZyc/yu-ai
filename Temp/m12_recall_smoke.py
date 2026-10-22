# -*- coding: utf-8 -*-
"""
m12_recall_smoke.py — 聊天消息撤回(M12)全链路冒烟:recall 端点 + recalled 墓碑列 + admin 通道 + 落库

前置:
  1. fat jar 含 recall 代码(mvn -pl yudao-server -am package 重打)
  2. dev 库已执行 love.sql M12 段(love_message.recalled bit(1) NOT NULL DEFAULT b'0')
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m12_recall_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 A create-invite → B bind(couple1)
  T3 A send m1 → A recall(m1) → data is True;落库 recalled=1
  T4 A/B list 回包含 m1 且 recalled=True、content 原文仍在(墓碑不删行)
  T5 kind=2/3 可撤:send-wishes→recall、send-image→recall 各 code=0,落库=1
  T6 时间窗:DB 造旧(create_time-130s)→ recall → 1_003_003_001(服务端权威)
  T7 幂等:已撤回 m1 再 recall → code=0 data=True
  T8 未绑定 C:recall → 1_003_004_003
  T9 不存在:recall(999999999) → 1_003_003_000
  T10 couple2=C+D,D 发 → A recall(跨 couple) → 1_003_003_000(防探测)
  T11 非本人:A 发 m3,B recall(m3) → 1_003_003_000
  T12 参数 {} / 0 / -1 → 400;未登录 recall → 401
  T13 红点联动:A 发 m4 → B unread +1 → A recall(m4) → B unread -1(recalled 过滤)
  T14 admin 独立性:admin 删未撤回 m3 → A recall → 1_003_003_000(@TableLogic 已滤);
      admin 删已撤回 m1 → A list 不再含 m1
  T15 wish-list 排除:A send-wishes m6 → recall → B wish-list 不含 m6
  T16 清理还原
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


def send_text(token, content):
    return api("app-api", "POST", "/love/message/send", token, {"content": content})


def recall(token, mid):
    return api("app-api", "POST", "/love/message/recall", token, {"messageId": mid})


def db_recalled(cur, mid):
    """bit(1) 读回按 SQL 层比较(M11 记忆:驱动读回 bytes 于 int 比较恒 False),返回 1/0/None"""
    conn.commit()
    cur.execute("SELECT recalled = b'1' FROM love_message WHERE id = %s", (mid,))
    row = cur.fetchone()
    return row[0] if row else None


def list_ids(token, **params):
    qs = "&".join("%s=%s" % (k, v) for k, v in params.items() if v != "")
    r = api("app-api", "GET", "/love/message/list" + (("?" + qs) if qs else ""), token)
    assert r.get("code") == 0, "list 调用失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return r.get("data") or []


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

# T3 A 发 m1 → 自己撤回
r = send_text(tok_a, "撤回冒烟-1")
m1 = (r.get("data") or {}).get("id")
check("T3a A send 文本", r.get("code") == 0 and m1, "code=%s" % r.get("code"))
r = recall(tok_a, m1)
check("T3b A recall → data=True", r.get("code") == 0 and r.get("data") is True,
      "code=%s data=%s" % (r.get("code"), r.get("data")))
check("T3c 落库 recalled=1", db_recalled(cur, m1) == 1, "m1=%s" % m1)

# T4 list 照常含墓碑行(recalled=True、content 原文仍在)
ok = True
detail = ""
for name, tok in (("A", tok_a), ("B", tok_b)):
    items = [x for x in list_ids(tok) if str(x.get("id")) == str(m1)]
    if len(items) != 1 or items[0].get("recalled") is not True \
            or items[0].get("content") != "撤回冒烟-1":
        ok = False
        detail = "%s list m1=%s" % (name, json.dumps(items, ensure_ascii=False)[:120])
        break
check("T4 A/B list 含 m1 recalled=True 原文在", ok, detail)

# T5 kind=2/3 可撤
r = api("app-api", "POST", "/love/message/send-wishes", tok_a,
        {"wishKey": "smoke_w12", "text": "撤回心愿", "color": "#FF5A8C", "icon": "Star"})
m_w = (r.get("data") or {}).get("id")
check("T5a A send-wishes", r.get("code") == 0 and m_w, "code=%s" % r.get("code"))
r = recall(tok_a, m_w)
check("T5b wish recall", r.get("code") == 0 and db_recalled(cur, m_w) == 1,
      "code=%s" % r.get("code"))
r = api("app-api", "POST", "/love/message/send-image", tok_a,
        {"imageUrl": "http://192.168.1.4:48080/admin-api/infra/file/4/get/smoke_m12.jpg"})
m_i = (r.get("data") or {}).get("id")
check("T5c A send-image", r.get("code") == 0 and m_i, "code=%s" % r.get("code"))
r = recall(tok_a, m_i)
check("T5d image recall", r.get("code") == 0 and db_recalled(cur, m_i) == 1,
      "code=%s" % r.get("code"))

# T6 时间窗:DB 造旧 → 超窗拒绝(m2 保持未撤回)
r = send_text(tok_a, "撤回冒烟-2")
m2 = (r.get("data") or {}).get("id")
check("T6a A send m2", r.get("code") == 0 and m2, "code=%s" % r.get("code"))
cur.execute("UPDATE love_message SET create_time = DATE_SUB(NOW(), INTERVAL 130 SECOND) "
            "WHERE id = %s", (m2,))
conn.commit()
r = recall(tok_a, m2)
check("T6b 超窗 recall → 1_003_003_001", r.get("code") == 1003003001,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))
check("T6c m2 未被置位", db_recalled(cur, m2) == 0, "")

# T7 幂等:已撤回再撤
r = recall(tok_a, m1)
check("T7 已撤回再 recall 幂等成功", r.get("code") == 0 and r.get("data") is True,
      "code=%s data=%s" % (r.get("code"), r.get("data")))

# T8 未绑定 C
r = recall(tok_c, 123)
check("T8 C 未绑定 recall → 1_003_004_003", r.get("code") == 1003004003,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))

# T9 不存在
r = recall(tok_a, 999999999)
check("T9 不存在 → 1_003_003_000", r.get("code") == 1003003000,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))

# T10 couple2 = C+D;跨 couple 防探测
r = api("app-api", "POST", "/love/couple/create-invite", tok_c)
invite2 = (r.get("data") or {}).get("inviteCode", "")
r = api("app-api", "POST", "/love/couple/bind", tok_d, {"inviteCode": invite2})
check("T10a 绑定 couple2", r.get("code") == 0, "code=%s" % r.get("code"))
r = send_text(tok_d, "couple2 专属")
m_d = (r.get("data") or {}).get("id")
check("T10b D send", r.get("code") == 0 and m_d, "code=%s" % r.get("code"))
r = recall(tok_a, m_d)
check("T10c A recall 他 couple 消息 → 1_003_003_000", r.get("code") == 1003003000,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))

# T11 非本人(同 couple):A 发,B 撤
r = send_text(tok_a, "撤回冒烟-3")
m3 = (r.get("data") or {}).get("id")
check("T11a A send m3", r.get("code") == 0 and m3, "code=%s" % r.get("code"))
r = recall(tok_b, m3)
check("T11b B recall 他人消息 → 1_003_003_000", r.get("code") == 1003003000,
      "code=%s msg=%s" % (r.get("code"), r.get("msg")))
check("T11c m3 未被置位", db_recalled(cur, m3) == 0, "")

# T12 参数校验 + 未登录
r = api("app-api", "POST", "/love/message/recall", tok_a, {})
check("T12a recall {} → 400", r.get("code") == 400, "code=%s" % r.get("code"))
r = recall(tok_a, 0)
check("T12b recall 0 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
r = recall(tok_a, -1)
check("T12c recall -1 → 400", r.get("code") == 400, "code=%s" % r.get("code"))
r = api("app-api", "POST", "/love/message/recall", None, {"messageId": 1})
check("T12d 未登录 recall → 401", r.get("code") == 401, "code=%s" % r.get("code"))

# T13 红点联动:B 游标全程未动,unread = couple1 内 A 发的未撤回消息数(用 unread 端点差值断言,与 m11 同款)


def unread_count(token):
    r = api("app-api", "GET", "/love/message/unread", token)
    assert r.get("code") == 0, "unread 失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return (r.get("data") or {}).get("unread", 0)


before = unread_count(tok_b)
r = send_text(tok_a, "撤回冒烟-4(红点联动)")
m4 = (r.get("data") or {}).get("id")
check("T13a A send m4", r.get("code") == 0 and m4, "code=%s" % r.get("code"))
conn.commit()
plus = unread_count(tok_b)
check("T13b B unread 差值 +1", plus == before + 1, "b:%s→%s" % (before, plus))
r = recall(tok_a, m4)
check("T13c A recall m4", r.get("code") == 0, "code=%s" % r.get("code"))
conn.commit()
after = unread_count(tok_b)
check("T13d B unread 差值 -1(recalled 过滤)", after == before,
      "b:%s→%s(base=%s)" % (plus, after, before))

# T14 admin 独立性
r = api("admin-api", "POST", "/system/auth/login",
        body={"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken", "")
check("T14a admin 登录", r.get("code") == 0 and admin_token, "")
r = api("admin-api", "DELETE", "/love/message/delete?id=%s" % m3, admin_token)
check("T14b admin delete 未撤回 m3", r.get("code") == 0, "code=%s" % r.get("code"))
r = recall(tok_a, m3)
check("T14c A recall 已删 m3 → 1_003_003_000(@TableLogic 滤)",
      r.get("code") == 1003003000, "code=%s msg=%s" % (r.get("code"), r.get("msg")))
r = api("admin-api", "DELETE", "/love/message/delete?id=%s" % m1, admin_token)
check("T14d admin delete 已撤回 m1", r.get("code") == 0, "code=%s" % r.get("code"))
items = [x for x in list_ids(tok_a) if str(x.get("id")) == str(m1)]
check("T14e A list 不再含 m1", len(items) == 0,
      "hits=%s" % json.dumps(items, ensure_ascii=False)[:80])

# T15 wish-list 排除已撤回
r = api("app-api", "POST", "/love/message/send-wishes", tok_a,
        {"wishKey": "smoke_w12b", "text": "时间线排除", "color": "#6C7BFF", "icon": "Gift"})
m6 = (r.get("data") or {}).get("id")
check("T15a A send-wishes m6", r.get("code") == 0 and m6, "code=%s" % r.get("code"))
r = recall(tok_a, m6)
check("T15b A recall m6", r.get("code") == 0, "code=%s" % r.get("code"))
r = api("app-api", "GET", "/love/message/wish-list?limit=200", tok_b)
wl = r.get("data") or []
hit = [x for x in wl if str(x.get("id")) in (str(m6), str(m_w))]
check("T15c B wish-list 不含已撤回心愿(m6/m_w)", r.get("code") == 0 and not hit,
      "hits=%s" % json.dumps(hit, ensure_ascii=False)[:120])

# T16 清理
cleanup(cur)
conn.commit()
check("T16 清理还原", True)
conn.close()

print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
if failed:
    print("失败项:", failed)
    sys.exit(1)
