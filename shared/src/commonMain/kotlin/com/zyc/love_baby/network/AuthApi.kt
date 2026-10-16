package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.LoginReq
import com.zyc.love_baby.data.model.RegisterReq
import com.zyc.love_baby.data.model.ResetPasswordReq
import com.zyc.love_baby.data.model.SendSmsCodeReq
import com.zyc.love_baby.data.model.SmsLoginReq

/**
 * AuthApi — 鉴权域接口(§5.2 auth 组:6 免登 + 1 需登录,全部 POST)
 * 用法:协程内 val r = AuthApi.smsLogin(pager, mobile, code);按 ApiResult 分支(Ok 携带 CommonResult.data)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);data/model 各请求体
 * 事件:无(登录态回写由调用方经 SessionStore.update 完成;刷新通道回写在 HttpClient 内)
 * 功能:
 *  - register 密码注册(短信码校验,错码 1_003_005_000)
 *  - login 密码登录 / smsLogin 短信登录(未注册自动注册,首期主路径)
 *  - sendSmsCode 发验证码(scene 固定 1 = MEMBER_LOGIN,D-3)
 *  - resetPassword 重置密码(复用 SmsCodeApi scene=1)
 *  - logout 需登录无 body;端上先调、容忍失败、再清本地(§5.2)
 * 不设 refreshToken:刷新通道由 HttpClient.doRefresh 直连实现(经 request 会走 401 通道,
 * 刷新端点自身 401 时递归触发单飞),路径唯一来源 ApiConfig.PATH_REFRESH_TOKEN
 */
internal object AuthApi {

    private const val PATH_REGISTER = "/love/auth/register"
    private const val PATH_LOGIN = "/love/auth/login"
    private const val PATH_SMS_LOGIN = "/love/auth/sms-login"
    private const val PATH_SEND_SMS_CODE = "/love/auth/send-sms-code"
    private const val PATH_RESET_PASSWORD = "/love/auth/reset-password"
    private const val PATH_LOGOUT = "/love/auth/logout"

    /** 密码注册(短信码校验);响应 data 同登录契约(若有)由调用方落 SessionStore */
    suspend fun register(pager: Pager, mobile: String, password: String, code: String): ApiResult =
        HttpClient.request(pager, PATH_REGISTER, isPost = true, body = RegisterReq(mobile, password, code).toJson())

    /** 密码登录(错码 1_003_005_001/002) */
    suspend fun login(pager: Pager, mobile: String, password: String): ApiResult =
        HttpClient.request(pager, PATH_LOGIN, isPost = true, body = LoginReq(mobile, password).toJson())

    /** 短信登录,未注册自动注册(首期主路径) */
    suspend fun smsLogin(pager: Pager, mobile: String, code: String): ApiResult =
        HttpClient.request(pager, PATH_SMS_LOGIN, isPost = true, body = SmsLoginReq(mobile, code).toJson())

    /** 发送短信验证码,scene 固定 1(MEMBER_LOGIN,§5.2 D-3) */
    suspend fun sendSmsCode(pager: Pager, mobile: String): ApiResult =
        HttpClient.request(pager, PATH_SEND_SMS_CODE, isPost = true, body = SendSmsCodeReq(mobile).toJson())

    /** 重置密码(复用 SmsCodeApi scene=1);成功后需重新登录 */
    suspend fun resetPassword(pager: Pager, mobile: String, code: String, password: String): ApiResult =
        HttpClient.request(
            pager,
            PATH_RESET_PASSWORD,
            isPost = true,
            body = ResetPasswordReq(mobile, code, password).toJson(),
        )

    /** 退出登录:需登录、无 body;服务端移除 access+refresh token,端上先调、容忍失败、再清本地(§5.2) */
    suspend fun logout(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LOGOUT, isPost = true, body = null)
}
