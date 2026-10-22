# -*- coding: utf-8 -*-
"""
m15_moment_smoke.py — 相册二期(M15)全链路冒烟:多图 image_urls + love_moment_like 点赞 + 兼容归一 + admin 回归

前置:
  1. fat jar 含 M15 代码(mvn -pl yudao-server -am package 重打)
  2. 库已执行 love.sql M15 段(image_urls 列 + 回填 + love_moment_like 表, m15_ddl.py)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && python -I ../Temp/m15_moment_smoke.py 48081

断言:
  T1 五号 sms-login(9999;E 从不绑定,充当未绑定用例)
  T2 couple1=A+B, couple2=C+D
  T3 A create 多图{imageUrl:u1, imageUrls:[u1,u2,u3]} → 回包 imageUrls 长3/likeCount=0/liked=False;
     落库 image_urls=["u1","u2","u3"]、image_url=u1
  T4 存量兼容 create 仅 imageUrl:u9 → 回包 imageUrls=[u9];落库 image_urls=["u9"]
  T5 list 组装:每条含非空 imageUrls/likeCount/liked;多图条 liked=False
  T6 回填幂等:直插 image_urls=NULL 存量态行 → 跑回填 UPDATE → 变 [image_url];再跑无变化
  T7 B like(w1,true) ×2 → 两次均 {likeCount:1, liked:true};DB 恰 1 物理行(INSERT IGNORE 幂等)
  T8 B like(w1,false) ×2 → {likeCount:0, liked:false};DB 0 行(物理删锤实,无墓碑)
  T9 互赞视角:A like(w1,true) → B list 视角 liked=False/likeCount=1;A list 视角 liked=True
  T10 uk 锤实:对 (w1,B) 直插重复行 → 1062 duplicate(uk 生效)
  T11 防探测:C(跨 couple) like couple1 瞬间 / id=999999 → 均 1_003_008_000 同文案
  T12 删后再赞:B like A 的 m3 → code=0;A delete m3 → code=0;B 再 like m3 → 1_003_008_000;
     m3 赞行残留 DB(D-20h 接受);list 不含 m3
  T13 超限 imageUrls 10 个 → 400(@Size max=9)
  T14 未绑定 E list/like → 1_003_004_003;无 token list/like → 401
  T15 admin page 回归:多图瞬间 imageUrl=u1(首图冗余,admin/web 零改动根基);coupleId 过滤正常
  T16 cleanup 后零残留(love_moment_like/love_moment/sms code)
"""
import json
import sys
import urllib.request
import urllib.error

import pymysql
import pymysql.err

# -I 隔离模式忽略 PYTHONIOENCODING,GBK 控制台打不出 emoji → 显式重配置
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILES = ["13900010031", "13900010032", "13900010033", "13900010034", "13900010035"]
BACKFILL_SQL = ("UPDATE love_moment SET image_urls = JSON_ARRAY(image_url) "
                "WHERE image_urls IS NULL OR image_urls = ''")

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
        "DELETE FROM love_moment_like WHERE couple_id IN (SELECT id FROM (SELECT id FROM love_couple "
        "WHERE user_a_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t) "
        "OR user_b_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t)) x)"
        % (ph, ph), MOBILES + MOBILES)
    cur.execute(
        "DELETE FROM love_moment WHERE couple_id IN (SELECT id FROM (SELECT id FROM love_couple "
        "WHERE user_a_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t) "
        "OR user_b_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t)) x)"
        % (ph, ph), MOBILES + MOBILES)
    cur.execute(
        "DELETE FROM love_couple WHERE user_a_id IN "
        "(SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t) "
        "OR user_b_id IN (SELECT id FROM (SELECT id FROM love_user WHERE mobile IN (%s)) t)" % (ph, ph),
        MOBILES + MOBILES)
    cur.execute("DELETE FROM love_user WHERE mobile IN (%s)" % ph, MOBILES)
    cur.execute("DELETE FROM love_moment_like WHERE couple_id NOT IN (SELECT id FROM love_couple)")


cleanup()
conn.commit()


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code", body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login", body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", "")


def moment_list(token, limit=30):
    r = api("app-api", "GET", "/love/moment/list?beforeId=0&limit=%d" % limit, token)
    assert r.get("code") == 0, "moment list 失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return r.get("data") or []


# T1 五号登录(E 从不绑定)
tok = {}
for m in MOBILES:
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

U1 = "http://127.0.0.1:48081/app-api/infra/file/4/get/m15_t3_a.jpg"
U2 = "http://127.0.0.1:48081/app-api/infra/file/4/get/m15_t3_b.jpg"
U3 = "http://127.0.0.1:48081/app-api/infra/file/4/get/m15_t3_c.jpg"
U9 = "http://127.0.0.1:48081/app-api/infra/file/4/get/m15_t4_single.jpg"

# T3 A create 多图
r = api("app-api", "POST", "/love/moment/create", tok_a,
        {"imageUrl": U1, "imageUrls": [U1, U2, U3], "text": "三连拍"})
d = r.get("data") or {}
w1 = d.get("id")
check("T3a 多图 create 回包", r.get("code") == 0 and w1 and d.get("imageUrls") == [U1, U2, U3]
      and d.get("likeCount") == 0 and d.get("liked") is False and d.get("imageUrl") == U1,
      "code=%s id=%s" % (r.get("code"), w1))
conn.commit()
cur.execute("SELECT image_url, image_urls FROM love_moment WHERE id = %s", (w1,))
row = cur.fetchone()
check("T3b 落库 image_urls + 首图冗余", row and row[0] == U1
      and json.loads(row[1]) == [U1, U2, U3], "db=%s" % (row,))

# T4 存量兼容:仅 imageUrl(模拟老版 App)
r = api("app-api", "POST", "/love/moment/create", tok_a, {"imageUrl": U9, "text": ""})
d = r.get("data") or {}
w2 = d.get("id")
check("T4a 单图 create 归一", r.get("code") == 0 and w2 and d.get("imageUrls") == [U9],
      "code=%s imageUrls=%s" % (r.get("code"), d.get("imageUrls")))
conn.commit()
cur.execute("SELECT image_url, image_urls FROM love_moment WHERE id = %s", (w2,))
row = cur.fetchone()
check("T4b 落库归一 [image_url]", row and row[0] == U9 and json.loads(row[1]) == [U9],
      "db=%s" % (row,))

# T5 list 组装
lst = moment_list(tok_a)
multi = next((x for x in lst if str(x.get("id")) == str(w1)), None)
ok_shape = bool(lst) and all(
    isinstance(x.get("imageUrls"), list) and len(x.get("imageUrls")) >= 1
    and isinstance(x.get("likeCount"), int) and "liked" in x for x in lst)
check("T5 list 组装 shape", ok_shape and multi and multi.get("likeCount") == 0
      and multi.get("liked") is False, "n=%d" % len(lst))

# T6 回填幂等(直插存量态行 → 回填 → 再跑无变化)
cur.execute("INSERT INTO love_moment (couple_id, user_id, image_url, text) "
            "VALUES (%s, (SELECT id FROM (SELECT id FROM love_user WHERE mobile = %s) t), "
            "'http://x/m15_bf.jpg', '')", (couple1, MOBILES[0]))
conn.commit()
cur.execute(BACKFILL_SQL)
cur.execute("SELECT image_urls FROM love_moment WHERE image_url = 'http://x/m15_bf.jpg' "
            "AND deleted = b'0' ORDER BY id DESC LIMIT 1")
backfilled = json.loads(cur.fetchone()[0])
cur.execute(BACKFILL_SQL)
check("T6 回填幂等", backfilled == ["http://x/m15_bf.jpg"] and cur.rowcount == 0,
      "backfill=%s rerun_affected=%s" % (backfilled, cur.rowcount))

# T7 B like w1 ×2(INSERT IGNORE 幂等,真值收敛)
r1 = api("app-api", "POST", "/love/moment/like", tok_b, {"id": w1, "liked": True})
d1 = r1.get("data") or {}
r2 = api("app-api", "POST", "/love/moment/like", tok_b, {"id": w1, "liked": True})
d2 = r2.get("data") or {}
conn.commit()
cur.execute("SELECT COUNT(*) FROM love_moment_like WHERE moment_id = %s", (w1,))
n_like = cur.fetchone()[0]
check("T7 like ×2 幂等 + DB 1 行", r1.get("code") == 0 and r2.get("code") == 0
      and d1 == {"likeCount": 1, "liked": True} and d2 == {"likeCount": 1, "liked": True}
      and n_like == 1, "d1=%s d2=%s rows=%s" % (d1, d2, n_like))

# T8 unlike ×2(物理 DELETE,无墓碑)
r1 = api("app-api", "POST", "/love/moment/like", tok_b, {"id": w1, "liked": False})
d1 = r1.get("data") or {}
r2 = api("app-api", "POST", "/love/moment/like", tok_b, {"id": w1, "liked": False})
d2 = r2.get("data") or {}
conn.commit()
cur.execute("SELECT COUNT(*) FROM love_moment_like WHERE moment_id = %s", (w1,))
n_like = cur.fetchone()[0]
check("T8 unlike ×2 幂等 + DB 0 行(物理删)", r1.get("code") == 0 and r2.get("code") == 0
      and d1 == {"likeCount": 0, "liked": False} and d2 == {"likeCount": 0, "liked": False}
      and n_like == 0, "d1=%s rows=%s" % (d1, n_like))

# T9 互赞视角:liked 按人算
r = api("app-api", "POST", "/love/moment/like", tok_a, {"id": w1, "liked": True})
check("T9a A like → 回包 {1,true}", (r.get("data") or {}) == {"likeCount": 1, "liked": True},
      "d=%s" % (r.get("data"),))
lst_a = moment_list(tok_a)
lst_b = moment_list(tok_b)
ma = next((x for x in lst_a if str(x.get("id")) == str(w1)), {})
mb = next((x for x in lst_b if str(x.get("id")) == str(w1)), {})
check("T9b 双方视角分叉", ma.get("liked") is True and ma.get("likeCount") == 1
      and mb.get("liked") is False and mb.get("likeCount") == 1,
      "a=%s/%s b=%s/%s" % (ma.get("liked"), ma.get("likeCount"), mb.get("liked"), mb.get("likeCount")))

# T10 uk 锤实:直插与 T9 已存在赞行 (w1,A) 重复的 (moment_id,user_id) → 1062
cur.execute("SELECT id FROM love_user WHERE mobile = %s", (MOBILES[0],))
uid_a2 = cur.fetchone()[0]
dup_ok = False
try:
    cur.execute("INSERT INTO love_moment_like (couple_id, moment_id, user_id) VALUES (%s, %s, %s)",
                (couple1, w1, uid_a2))
except pymysql.err.IntegrityError as e:
    dup_ok = e.args[0] == 1062
conn.rollback()  # RR 事务内 1062 后回滚,避免残留半程状态
check("T10 uk duplicate 1062", dup_ok, "")

# T11 防探测:跨 couple + 不存在 → 统一 1_003_008_000
r1 = api("app-api", "POST", "/love/moment/like", tok_c, {"id": w1, "liked": True})
r2 = api("app-api", "POST", "/love/moment/like", tok_a, {"id": 999999999, "liked": True})
check("T11 跨 couple/不存在 → 1_003_008_000",
      r1.get("code") == 1003008000 and r2.get("code") == 1003008000
      and r1.get("msg") == r2.get("msg"),
      "c1=%s c2=%s msg=%s" % (r1.get("code"), r2.get("code"), r1.get("msg")))

# T12 删后再赞 + 赞行残留(D-20h)
r = api("app-api", "POST", "/love/moment/create", tok_a,
        {"imageUrl": U2, "imageUrls": [U2], "text": "待删"})
m3 = (r.get("data") or {}).get("id")
r = api("app-api", "POST", "/love/moment/like", tok_b, {"id": m3, "liked": True})
like_ok = r.get("code") == 0
r = api("app-api", "POST", "/love/moment/delete", tok_a, {"id": m3})
del_ok = r.get("code") == 0
r = api("app-api", "POST", "/love/moment/like", tok_b, {"id": m3, "liked": True})
c_like_after = r.get("code")
conn.commit()
cur.execute("SELECT COUNT(*) FROM love_moment_like WHERE moment_id = %s", (m3,))
residue = cur.fetchone()[0]
lst = moment_list(tok_a)
gone = not any(str(x.get("id")) == str(m3) for x in lst)
check("T12 删后 like → 000 + 赞行残留 + list 消失",
      like_ok and del_ok and c_like_after == 1003008000 and residue == 1 and gone,
      "like=%s del=%s after=%s residue=%s" % (like_ok, del_ok, c_like_after, residue))

# T13 超限 imageUrls 10 个 → 400
r = api("app-api", "POST", "/love/moment/create", tok_a,
        {"imageUrl": U1, "imageUrls": ["http://x/%d.jpg" % i for i in range(10)], "text": ""})
check("T13 超限 10 图 → 400", r.get("code") == 400, "code=%s msg=%s" % (r.get("code"), r.get("msg")))

# T14 未绑定 E + 无 token
c1 = api("app-api", "GET", "/love/moment/list?beforeId=0&limit=30", tok_e).get("code")
c2 = api("app-api", "POST", "/love/moment/like", tok_e, {"id": 1, "liked": True}).get("code")
c3 = api("app-api", "GET", "/love/moment/list?beforeId=0&limit=30").get("code")
c4 = api("app-api", "POST", "/love/moment/like", None, {"id": 1, "liked": True}).get("code")
check("T14 未绑定 → 1_003_004_003 / 无 token → 401",
      c1 == 1003004003 and c2 == 1003004003 and c3 == 401 and c4 == 401,
      "%s/%s/%s/%s" % (c1, c2, c3, c4))

# T15 admin page 回归(admin/web 零改动根基:多图瞬间 imageUrl=首图)
r = api("admin-api", "POST", "/system/auth/login", body={"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken", "")
check("T15a admin 登录", r.get("code") == 0 and admin_token, "")
r = api("admin-api", "GET", "/love/moment/page?pageNo=1&pageSize=50&coupleId=%s" % couple1, admin_token)
pg = (r.get("data") or {}).get("list") or []
pm = next((x for x in pg if str(x.get("id")) == str(w1)), None)
check("T15b page coupleId 过滤 + 多图显首图", r.get("code") == 0 and pm is not None
      and pm.get("imageUrl") == U1 and "imageUrls" not in pm,
      "n=%d imageUrl=%s" % (len(pg), pm.get("imageUrl") if pm else None))

# T16 cleanup 零残留
cleanup()
conn.commit()
cur.execute("SELECT COUNT(*) FROM love_moment WHERE couple_id = %s", (couple1,))
n_moment = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM love_moment_like WHERE moment_id NOT IN "
            "(SELECT id FROM love_moment)")
n_orphan_like = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM system_sms_code WHERE mobile IN (%s)"
            % ",".join(["%s"] * len(MOBILES)), MOBILES)
n_sms = cur.fetchone()[0]
check("T16 cleanup 零残留", n_moment == 0 and n_orphan_like == 0 and n_sms == 0,
      "moment=%s orphan_like=%s sms=%s" % (n_moment, n_orphan_like, n_sms))
conn.close()

print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
if failed:
    print("失败项:", failed)
    sys.exit(1)
