# -*- coding: utf-8 -*-
"""
m13_presence_typing_smoke.py — 正在输入+伴侣在线状态(M13)全链路冒烟:online 端点 + typing 中继 + WS 下行

前置:
  1. fat jar 含 M13 代码(mvn -pl yudao-server -am package 重打)
  2. 无 DDL(部署即生效);pip install websocket-client(本脚本唯一新依赖)
  3. 第二实例已起: java -jar yudao-server/target/yudao-server.jar --server.port=48081
用法: cd love_baby_server && PYTHONIOENCODING=utf-8 python -I ../Temp/m13_presence_typing_smoke.py 48081

断言:
  T1 四号 sms-login(9999,未注册自动注册)
  T2 couple1=A+B、couple2=C+D
  T3 在线基线:A GET /love/couple/online → partnerOnline=False(data 为 dict 含 partnerOnline 键)
  T4 B 连 WS → A 轮询 True;B 开第 2 条连接仍 True(多端并存)
  T5 B 关全部连接 → A 轮询 False(内存会话表即时反映)
  T6 typing 中继:B 重连 → A POST /love/message/typing(空体) → code=0 data=True;
      B 的 WS 收到 type=love-typing 且 content.typing=="1"
  T7 自发不回环:A 自开 WS 再 POST → A 的 WS 无 love-typing 帧(信号只发对方)
  T8 couple 隔离:C POST typing → D 收到;B 的 WS 无
  T9 对方离线:B 断开,A POST → code=0 静默 no-op
  T10 未绑定:C unbind 后 POST typing + GET online → 均 1_003_004_003
  T11 未登录 GET online → 401;坏 token WS 握手拒绝
  T12 回包形态(含 partnerOnline 键非标量)+ typing 垃圾体 {"x":1} 容忍(code=0)
  T13 couple2 解绑不影响 couple1(B GET online 仍 code=0)
  T14 清理还原
"""
import json
import sys
import time
import urllib.request
import urllib.error

import pymysql
import websocket

PORT = sys.argv[1] if len(sys.argv) > 1 else "48081"
BASE = "http://127.0.0.1:%s" % PORT
MOBILE_A = "13900010031"
MOBILE_B = "13900010032"
MOBILE_C = "13900010033"
MOBILE_D = "13900010034"

passed = []
failed = []
ws_pool = []


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


def member_login(mobile):
    api("app-api", "POST", "/love/auth/send-sms-code",
        body={"mobile": mobile, "scene": 1})
    r = api("app-api", "POST", "/love/auth/sms-login",
            body={"mobile": mobile, "code": "9999"})
    return (r.get("data") or {}).get("accessToken", "")


def get_online(token):
    return api("app-api", "GET", "/love/couple/online", token)


def online_flag(token):
    r = get_online(token)
    assert r.get("code") == 0, "online 调用失败: %s" % json.dumps(r, ensure_ascii=False)[:120]
    return (r.get("data") or {}).get("partnerOnline")


def post_typing(token, body=None):
    """无 body = 与端上 HttpClient 缺省空对象同形态;body 传垃圾 dict 用于 T12 容忍断言"""
    return api("app-api", "POST", "/love/message/typing", token, body)


def ws_connect(token):
    """yudao WS 端点握手鉴权走 query param ?token=(LoginUserHandshakeInterceptor)"""
    ws = websocket.create_connection(
        "ws://127.0.0.1:%s/infra/ws?token=%s" % (PORT, token), timeout=5)
    ws_pool.append(ws)
    return ws


def ws_recv_type(ws, want, timeout=3.0):
    """循环收帧命中 type 返回整个消息 dict,超时回 None(ping/pong 与他类帧跳过)"""
    end = time.time() + timeout
    while True:
        remain = end - time.time()
        if remain <= 0:
            return None
        ws.settimeout(remain)
        try:
            frame = ws.recv()
        except websocket.WebSocketTimeoutException:
            return None
        except Exception:
            return None
        try:
            msg = json.loads(frame)
        except Exception:
            continue
        if msg.get("type") == want:
            return msg


def ws_close(ws):
    try:
        ws.close()
    except Exception:
        pass
    if ws in ws_pool:
        ws_pool.remove(ws)


conn = db()
cur = conn.cursor()
cleanup(cur)
conn.commit()

try:
    # T1 四号登录
    tok_a = member_login(MOBILE_A)
    tok_b = member_login(MOBILE_B)
    tok_c = member_login(MOBILE_C)
    tok_d = member_login(MOBILE_D)
    check("T1 四号 sms-login", all([tok_a, tok_b, tok_c, tok_d]), "")

    # T2 couple1 = A+B,couple2 = C+D
    r = api("app-api", "POST", "/love/couple/create-invite", tok_a)
    invite1 = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", tok_b, {"inviteCode": invite1})
    ok1 = r.get("code") == 0
    r = api("app-api", "POST", "/love/couple/create-invite", tok_c)
    invite2 = (r.get("data") or {}).get("inviteCode", "")
    r = api("app-api", "POST", "/love/couple/bind", tok_d, {"inviteCode": invite2})
    check("T2 绑定 couple1/couple2", ok1 and r.get("code") == 0,
          "couple1=%s couple2=%s" % (ok1, r.get("code")))

    # T3 在线基线:双方均无 WS 连接
    r = get_online(tok_a)
    check("T3 A GET online 基线 False(对象形态)",
          r.get("code") == 0 and isinstance(r.get("data"), dict)
          and r["data"].get("partnerOnline") is False,
          "code=%s data=%s" % (r.get("code"), json.dumps(r.get("data"), ensure_ascii=False)[:80]))

    # T4 B 连 WS → A 轮询 True;多端并存
    ws_b1 = ws_connect(tok_b)
    time.sleep(0.8)
    flag = online_flag(tok_a)
    ws_b2 = ws_connect(tok_b)
    time.sleep(0.8)
    flag2 = online_flag(tok_a)
    check("T4 B 连 WS 后 online=True(双连接仍 True)",
          flag is True and flag2 is True, "单连接=%s 双连接=%s" % (flag, flag2))

    # T5 B 关全部连接 → False
    ws_close(ws_b1)
    ws_close(ws_b2)
    time.sleep(1.2)
    flag = online_flag(tok_a)
    check("T5 B 全断开后 online=False", flag is False, "flag=%s" % flag)

    # T6 typing 中继:B 重连,A 上报,B 收 love-typing
    ws_b1 = ws_connect(tok_b)
    time.sleep(0.8)
    r = post_typing(tok_a)
    frame = ws_recv_type(ws_b1, "love-typing", 5)
    content_typing = None
    if frame is not None:
        try:
            # content 为字符串化 JSON(双层解析,recall 同款信封)
            content_typing = json.loads(frame.get("content") or "{}").get("typing")
        except Exception:
            content_typing = None
    check("T6a A POST typing → code=0 data=True",
          r.get("code") == 0 and r.get("data") is True,
          "code=%s data=%s" % (r.get("code"), r.get("data")))
    check("T6b B 的 WS 收到 love-typing 且 content.typing=='1'",
          content_typing == "1", "frame=%s" % json.dumps(frame, ensure_ascii=False)[:120])

    # T7 自发不回环:A 自开 WS,A 上报 → A 的 WS 无 love-typing(信号只发对方;B 侧会收到,先行消费)
    ws_a1 = ws_connect(tok_a)
    time.sleep(0.8)
    r = post_typing(tok_a)
    ws_recv_type(ws_b1, "love-typing", 3)  # 消费 B 侧该帧,保持后续负断言缓冲干净
    frame_a = ws_recv_type(ws_a1, "love-typing", 2)
    check("T7 A 自发不回环", r.get("code") == 0 and frame_a is None,
          "code=%s frame=%s" % (r.get("code"),
                                json.dumps(frame_a, ensure_ascii=False)[:80] if frame_a else "None"))

    # T8 couple 隔离:C 上报 → D 收到,B 无
    ws_d1 = ws_connect(tok_d)
    time.sleep(0.8)
    r = post_typing(tok_c)
    frame_d = ws_recv_type(ws_d1, "love-typing", 5)
    frame_b = ws_recv_type(ws_b1, "love-typing", 2)
    check("T8 couple 隔离(D 收到,B 无)",
          r.get("code") == 0 and frame_d is not None and frame_b is None,
          "code=%s D帧=%s" % (r.get("code"),
                              json.dumps(frame_d, ensure_ascii=False)[:80] if frame_d else "None"))

    # T9 对方离线:B 断开,A 上报 → 静默 no-op 仍 code=0
    ws_close(ws_b1)
    time.sleep(1.2)
    r = post_typing(tok_a)
    check("T9 对方离线 typing 静默 no-op", r.get("code") == 0 and r.get("data") is True,
          "code=%s data=%s" % (r.get("code"), r.get("data")))

    # T10 未绑定:C unbind 后 typing/online 均 1_003_004_003
    r = api("app-api", "POST", "/love/couple/unbind", tok_c)
    unbind_ok = r.get("code") == 0
    r = post_typing(tok_c)
    code_typing = r.get("code")
    r = get_online(tok_c)
    code_online = r.get("code")
    check("T10 C 未绑定 typing/online → 1_003_004_003",
          unbind_ok and code_typing == 1003004003 and code_online == 1003004003,
          "unbind=%s typing=%s online=%s" % (unbind_ok, code_typing, code_online))

    # T11 未登录 401 + 坏 token WS 握手拒绝
    r = api("app-api", "GET", "/love/couple/online")
    handshook = True
    try:
        bad = websocket.create_connection(
            "ws://127.0.0.1:%s/infra/ws?token=invalid-token-xxx" % PORT, timeout=5)
        bad.close()  # 未被拒绝(意外),标记失败
    except Exception:
        handshook = False
    check("T11 未登录 GET online → 401;坏 token 握手拒绝",
          r.get("code") == 401 and not handshook, "http=%s" % r.get("code"))

    # T12 回包形态 + 垃圾体容忍
    r = get_online(tok_a)
    has_key = isinstance(r.get("data"), dict) and "partnerOnline" in r["data"]
    r2 = post_typing(tok_a, body={"x": 1})
    check("T12 online 对象形态含 partnerOnline 键;typing 垃圾体容忍",
          has_key and r2.get("code") == 0 and r2.get("data") is True,
          "has_key=%s code=%s" % (has_key, r2.get("code")))

    # T13 couple2 解绑不影响 couple1
    r = get_online(tok_b)
    check("T13 B GET online 仍 code=0(couple1 未受影响)", r.get("code") == 0,
          "code=%s" % r.get("code"))
finally:
    for ws in list(ws_pool):
        ws_close(ws)
    cleanup(cur)
    conn.commit()
    conn.close()
    check("T14 清理还原", True)

print("\n===== 冒烟结果: %d 通过 / %d 失败 =====" % (len(passed), len(failed)))
if failed:
    print("失败项:", failed)
    sys.exit(1)
