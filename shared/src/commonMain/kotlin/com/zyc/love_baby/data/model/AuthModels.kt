package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * AuthModels — 鉴权域请求/响应模型(INTEGRATION-DESIGN §5.1/§5.2 契约)
 * 用法:请求体 XxxReq(...).toJson() 传 AuthApi;响应 data 经 AuthSession.from(data) 落成 SessionStore
 * 依赖:仅 core JSONObject 手写映射(不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - 六免登接口请求体:Register/Login/SmsLogin/SendSmsCode(scene=1)/ResetPassword
 *  - 登录/刷新统一响应 session 五字段(accessToken/refreshToken/userId/expiresTime/clientId),
 *    持久化 JSON 字段与响应一致(§7.2)
 *  - auth 组错误码(1_003_005_00x):000 手机号已注册/001 账号或密码错误(无端上分支,见错误码段注释);
 *    002 账号已停用(AUTH_ERR_USER_DISABLED,HttpClient 特判清会话回 Gate)
 */

// ---------- auth 组错误码(§5.2;短信验证码自身错误由 system 的 SmsCodeApi 抛其自有码) ----------
// 1_003_005_000 手机号已注册 / 1_003_005_001 账号或密码错误:客户端无差异化分支,统一走
// HttpClient 的 BizError toast 服务端 msg,故不设常量(避免死代码假覆盖);
// 1_003_005_002 账号已停用有专属收口(清会话回 Gate,§5.1 对照表/§6.5),设常量供 HttpClient 特判

/** 1_003_005_002 账号已停用 */
internal const val AUTH_ERR_USER_DISABLED = 1003005002L

// ---------- 请求体(§5.2,全部 POST + application/json) ----------

/** 密码注册(短信码校验) */
internal data class RegisterReq(val mobile: String, val password: String, val code: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("mobile", mobile)
        .put("password", password)
        .put("code", code)
}

/** 密码登录 */
internal data class LoginReq(val mobile: String, val password: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("mobile", mobile)
        .put("password", password)
}

/** 短信登录(未注册自动注册,首期主路径) */
internal data class SmsLoginReq(val mobile: String, val code: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("mobile", mobile)
        .put("code", code)
}

/** 发送短信验证码,scene 固定 1(MEMBER_LOGIN,D-3) */
internal data class SendSmsCodeReq(val mobile: String, val scene: Int = 1) {
    fun toJson(): JSONObject = JSONObject()
        .put("mobile", mobile)
        .put("scene", scene)
}

/** 重置密码(复用 SmsCodeApi scene=1) */
internal data class ResetPasswordReq(val mobile: String, val code: String, val password: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("mobile", mobile)
        .put("code", code)
        .put("password", password)
}

// ---------- 登录/刷新统一响应 ----------

/**
 * 登录/刷新响应统一契约(§5.2):{accessToken, refreshToken, userId, expiresTime, clientId}。
 * userId 服务端为 Long(NumberSerializer 超 2^53-1 时序列化为字符串,范围内仍是数字,§5.1)——
 * 一律按 String 解析,禁止按数字解析 id(兼防 H5 double 精度丢失)
 */
internal data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresTime: Long,
    val clientId: String,
) {
    /** 持久化 JSON 字段与响应一致(含 clientId,§7.2) */
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_ACCESS_TOKEN, accessToken)
        .put(KEY_REFRESH_TOKEN, refreshToken)
        .put(KEY_USER_ID, userId)
        .put(KEY_EXPIRES_TIME, expiresTime)
        .put(KEY_CLIENT_ID, clientId)

    companion object {
        const val KEY_ACCESS_TOKEN = "accessToken"
        const val KEY_REFRESH_TOKEN = "refreshToken"
        const val KEY_USER_ID = "userId"
        const val KEY_EXPIRES_TIME = "expiresTime"
        const val KEY_CLIENT_ID = "clientId"

        /**
         * 从 CommonResult.data 解析;accessToken 为空判定无效回包返回 null。
         * clientId 缺省时用 [fallbackClientId](传 ApiConfig.CLIENT_ID)
         */
        fun from(data: JSONObject?, fallbackClientId: String = ""): AuthSession? {
            if (data == null) return null
            val accessToken = data.optString(KEY_ACCESS_TOKEN)
            if (accessToken.isEmpty()) return null
            return AuthSession(
                accessToken = accessToken,
                refreshToken = data.optString(KEY_REFRESH_TOKEN),
                userId = data.optString(KEY_USER_ID),
                expiresTime = data.optLong(KEY_EXPIRES_TIME),
                clientId = data.optString(KEY_CLIENT_ID).ifEmpty { fallbackClientId },
            )
        }

        /** 从持久化 JSON 恢复(与 toJson 互逆);accessToken 为空返回 null */
        fun fromJson(json: JSONObject): AuthSession? {
            val accessToken = json.optString(KEY_ACCESS_TOKEN)
            if (accessToken.isEmpty()) return null
            return AuthSession(
                accessToken = accessToken,
                refreshToken = json.optString(KEY_REFRESH_TOKEN),
                userId = json.optString(KEY_USER_ID),
                expiresTime = json.optLong(KEY_EXPIRES_TIME),
                clientId = json.optString(KEY_CLIENT_ID),
            )
        }
    }
}
