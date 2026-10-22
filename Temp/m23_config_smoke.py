# -*- coding: utf-8 -*-
"""
m23_config_smoke.py — 业务配置(M23)全链路冒烟:love_config KV + admin CRUD + app Map 下发 + 兜底语义

前置:
  1. fat jar 含 M23 代码(Love 前缀重命名后重打,ConfigServiceImpl 与 infra 冲突已修)
  2. 库已执行 love.sql M23 段(建表+种子+菜单, m23_apply_sql.py)
  3. 第二实例已起(⚠ fat jar 默认 dev profile 连远程库+开验证码,必须 CLI 覆盖):
     "JBR java" -jar yudao-server/target/yudao-server.jar --server.port=48081 --spring.profiles.active=local
用法: python -I ../Temp/m23_config_smoke.py 48081

断言:
  T1 admin 登录 + page 含种子 customer_service_wechat(lovebaby-helper,启用)
  T2 app sms-login → GET /love/config/list 回 Map 对象形态(含 customer_service_wechat)
  T3 无 token app list → body code=401(HTTP 200 包裹,yudao 鉴权惯例)
  T4 admin create smoke_test_key → code=0 有 id;app list 出现该键
  T5 create 同键重复 → 1_003_012_001(service 层唯一闸,无 DB uk)
  T6 create 非法键(大写/中文/数字开头) → body code=400(@Pattern,HTTP 200 包裹,M16 教训)
  T7 update 改值 → app list 立即可见新值
  T8 update 不存在 id → 1_003_012_000
  T9 page 筛选:configKey 模糊/名称模糊/status 精确 各命中预期
  T10 停用(status=1) → app list 不再下发该键(customer_service_wechat 不受影响)
  T11 delete → DB deleted=1;page 不再出现;app list 恒无
  T12 delete 不存在 id → 1_003_012_000
  T13 改种子值再还原 → app list 跟随(动态配置核心语义)
  T14 cleanup 零残留(smoke 键/种子值还原/sms code/love_user)
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

import pymysql

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:%s" % (sys.argv[1] if len(sys.argv) > 1 else "48081")
MOBILE = "13900010031"
SEED_KEY = "customer_service_wechat"
SEED_VALUE = "lovebaby-helper"
SMOKE_KEY = "smoke_test_key"

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
    cur.execute("DELETE FROM love_config WHERE config_key LIKE 'smoke_test%%'")
    cur.execute("UPDATE love_config SET config_value = %s WHERE config_key = %s AND deleted = b'0'",
                (SEED_VALUE, SEED_KEY))
    cur.execute("DELETE FROM system_sms_code WHERE mobile = %s", (MOBILE,))
    cur.execute("DELETE FROM love_user WHERE mobile = %s", (MOBILE,))
    conn.commit()


cleanup()

# ---------- T1 admin 登录 + 种子在分页 ----------
r = api("admin-api", "POST", "/system/auth/login", body={"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken", "")
check("T1a admin 登录", r.get("code") == 0 and admin_token,
      "" if r.get("code") == 0 else json.dumps(r, ensure_ascii=False)[:120])

r = api("admin-api", "GET", "/love/config/page?pageNo=1&pageSize=50&configKey=%s" % SEED_KEY, admin_token)
rows = (r.get("data") or {}).get("list") or []
seed_row = next((x for x in rows if x.get("configKey") == SEED_KEY), None)
check("T1b page 含种子", r.get("code") == 0 and seed_row is not None
      and seed_row.get("configValue") == SEED_VALUE and seed_row.get("status") == 0,
      json.dumps(seed_row, ensure_ascii=False)[:100] if seed_row else "无")

# ---------- T2 app 登录 + Map 下发 ----------
api("app-api", "POST", "/love/auth/send-sms-code", body={"mobile": MOBILE, "scene": 1})
r = api("app-api", "POST", "/love/auth/sms-login", body={"mobile": MOBILE, "code": "9999"})
app_token = (r.get("data") or {}).get("accessToken", "")
check("T2a app sms-login", r.get("code") == 0 and app_token, "")

r = api("app-api", "GET", "/love/config/list", app_token)
data = r.get("data")
check("T2b app list 回 Map 对象形态", r.get("code") == 0 and isinstance(data, dict)
      and data.get(SEED_KEY) == SEED_VALUE, json.dumps(data, ensure_ascii=False)[:120])

# ---------- T3 无 token ----------
r = api("app-api", "GET", "/love/config/list")
check("T3 无 token body 401", r.get("code") == 401, json.dumps(r, ensure_ascii=False)[:80])

# ---------- T4 create + app 可见 ----------
r = api("admin-api", "POST", "/love/config/create", admin_token,
        body={"name": "冒烟测试项", "configKey": SMOKE_KEY, "configValue": "abc", "remark": "冒烟临时"})
check("T4a create", r.get("code") == 0 and (r.get("data") or {}).get("id"), json.dumps(r, ensure_ascii=False)[:100])
smoke_id = (r.get("data") or {}).get("id")

r = api("app-api", "GET", "/love/config/list", app_token)
check("T4b app list 出现新键", (r.get("data") or {}).get(SMOKE_KEY) == "abc", "")

# ---------- T5 同键重复 ----------
r = api("admin-api", "POST", "/love/config/create", admin_token,
        body={"name": "重复键", "configKey": SEED_KEY, "configValue": "x"})
check("T5 同键 1_003_012_001", r.get("code") == 1003012001, json.dumps(r, ensure_ascii=False)[:80])

# ---------- T6 非法键(@Pattern → body 400) ----------
r = api("admin-api", "POST", "/love/config/create", admin_token,
        body={"name": "非法键", "configKey": "BadKey", "configValue": "x"})
check("T6a 大写开头 body 400", r.get("code") == 400, json.dumps(r, ensure_ascii=False)[:80])
r = api("admin-api", "POST", "/love/config/create", admin_token,
        body={"name": "非法键", "configKey": "含中文键", "configValue": "x"})
check("T6b 中文键 body 400", r.get("code") == 400, "")

# ---------- T7 update 改值即生效 ----------
r = api("admin-api", "PUT", "/love/config/update", admin_token,
        body={"id": smoke_id, "name": "冒烟测试项", "configKey": SMOKE_KEY, "configValue": "xyz"})
check("T7a update", r.get("code") == 0, "")
r = api("app-api", "GET", "/love/config/list", app_token)
check("T7b app list 见新值", (r.get("data") or {}).get(SMOKE_KEY) == "xyz", "")

# ---------- T8/T12 不存在 ----------
r = api("admin-api", "PUT", "/love/config/update", admin_token,
        body={"id": 999999, "name": "幽灵", "configKey": "ghost_key", "configValue": "x"})
check("T8 update 不存在 1_003_012_000", r.get("code") == 1003012000, json.dumps(r, ensure_ascii=False)[:80])
r = api("admin-api", "DELETE", "/love/config/delete?id=999999", admin_token)
check("T12 delete 不存在 1_003_012_000", r.get("code") == 1003012000, "")

# ---------- T9 page 筛选 ----------
r = api("admin-api", "GET", "/love/config/page?pageNo=1&pageSize=50&configKey=smoke", admin_token)
keys = [x.get("configKey") for x in (r.get("data") or {}).get("list") or []]
check("T9a configKey 模糊", keys == [SMOKE_KEY], str(keys))
# 中文查询参数必须 URL 编码(M17 教训),否则 urllib ascii 编码直接崩
r = api("admin-api", "GET", "/love/config/page?pageNo=1&pageSize=50&name=%s" % urllib.parse.quote("客服"), admin_token)
keys = [x.get("configKey") for x in (r.get("data") or {}).get("list") or []]
check("T9b name 模糊", keys == [SEED_KEY], str(keys))
r = api("admin-api", "GET", "/love/config/page?pageNo=1&pageSize=50&status=1", admin_token)
keys = [x.get("configKey") for x in (r.get("data") or {}).get("list") or []]
check("T9c status 过滤停用为空", keys == [], str(keys))

# ---------- T10 停用不下发 ----------
r = api("admin-api", "PUT", "/love/config/update", admin_token,
        body={"id": smoke_id, "name": "冒烟测试项", "configKey": SMOKE_KEY, "configValue": "xyz", "status": 1})
check("T10a 停用 update", r.get("code") == 0, "")
r = api("app-api", "GET", "/love/config/list", app_token)
data = r.get("data") or {}
check("T10b app list 不含停用键(种子仍在)", SMOKE_KEY not in data and data.get(SEED_KEY) == SEED_VALUE, "")

# ---------- T11 delete 落库锤实 ----------
r = api("admin-api", "DELETE", "/love/config/delete?id=%s" % smoke_id, admin_token)
check("T11a delete", r.get("code") == 0, "")
cur.execute("SELECT COUNT(*) FROM love_config WHERE id = %s AND deleted = b'1'", (smoke_id,))
check("T11b DB deleted=1", cur.fetchone()[0] == 1, "")
r = api("admin-api", "GET", "/love/config/page?pageNo=1&pageSize=50&configKey=smoke", admin_token)
keys = [x.get("configKey") for x in (r.get("data") or {}).get("list") or []]
check("T11c page 不再出现", keys == [], str(keys))

# ---------- T13 种子值动态跟随 ----------
r = api("admin-api", "PUT", "/love/config/update", admin_token,
        body={"id": seed_row["id"], "name": seed_row["name"], "configKey": SEED_KEY,
              "configValue": "cs-new-wechat", "status": 0, "remark": seed_row.get("remark")})
check("T13a 改种子值", r.get("code") == 0, "")
r = api("app-api", "GET", "/love/config/list", app_token)
check("T13b app list 跟随新值", (r.get("data") or {}).get(SEED_KEY) == "cs-new-wechat", "")

# ---------- T14 cleanup 零残留 ----------
cleanup()
cur.execute("SELECT COUNT(*) FROM love_config WHERE config_key LIKE 'smoke_test%%'")
check("T14a smoke 键零残留", cur.fetchone()[0] == 0, "")
cur.execute("SELECT config_value FROM love_config WHERE config_key = %s AND deleted = b'0'", (SEED_KEY,))
check("T14b 种子值已还原", cur.fetchone()[0] == SEED_VALUE, "")
cur.execute("SELECT COUNT(*) FROM love_user WHERE mobile = %s", (MOBILE,))
check("T14c 用户清理", cur.fetchone()[0] == 0, "")
conn.commit()

cur.close(); conn.close()
print("\n==== M23 冒烟 %d/%d ====" % (len(passed), len(passed) + len(failed)))
if failed:
    print("失败项:", failed)
    sys.exit(1)
