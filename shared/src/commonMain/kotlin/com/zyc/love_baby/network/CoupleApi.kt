package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.COUPLE_ERR_NOT_BOUND
import com.zyc.love_baby.data.model.CoupleBindReq

/**
 * CoupleApi — 情侣绑定域接口(INTEGRATION-DESIGN §5.2 couple 组,全部需登录)
 * 用法:协程内 val r = CoupleApi.getCouple(pager);按 ApiResult 分支(Ok 携带 CommonResult.data)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新/successBizCodes 状态信号通道);CoupleModels
 * 事件:无(绑定状态统一落 state/CoupleStore,由调用方驱动)
 * 功能:
 *  - createInvite 生成/刷新我的邀请码(存在未过期码则复用,否则重生成 + 续期 7 天)
 *  - bind 凭码绑定(无效/过期/已绑定/自绑被 1_003_004 段拒绝,走 BizError 由调用方 toast)
 *  - getCouple 绑定关系与伴侣信息;⚠ 未绑定时服务端返回 1_003_004_003 —— 这是状态信号不是错误,
 *    经 HttpClient.request 的 successBizCodes 声明按数据放行(空 data 的 Ok),不走「code!=0 一律 toast」路径
 *  - unbind 双方任一可发起,服务端事务内物理删除 couple 行(D-11)
 */
internal object CoupleApi {

    private const val PATH_CREATE_INVITE = "/love/couple/create-invite"
    private const val PATH_BIND = "/love/couple/bind"
    private const val PATH_GET = "/love/couple/get"
    private const val PATH_ONLINE = "/love/couple/online"
    private const val PATH_UNBIND = "/love/couple/unbind"

    suspend fun createInvite(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_CREATE_INVITE, isPost = true)

    suspend fun bind(pager: Pager, inviteCode: String): ApiResult =
        HttpClient.request(pager, PATH_BIND, isPost = true, body = CoupleBindReq(inviteCode).toJson())

    /** ⚠ 未绑定(1_003_004_003)按数据返回:空 data 的 Ok,由 CoupleStore 解析为「未绑定」态 */
    suspend fun getCouple(pager: Pager): ApiResult =
        HttpClient.request(
            pager,
            PATH_GET,
            isPost = false,
            successBizCodes = setOf(COUPLE_ERR_NOT_BOUND),
        )

    suspend fun unbind(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_UNBIND, isPost = true)

    /**
     * 伴侣在线状态(M13,仅聊天页停留期间 5s 轮询):回包 data 为对象 {"partnerOnline":bool}。
     * ⚠ 对象形态契约——服务端禁改回 CommonResult<Boolean> 标量(toOk 只分派对象/数组,标量落空
     * 对象,M11 unread 同款);未绑定 1_003_004_003 走普通 BizError 通道由调用方静默
     * (successBizCodes 放行是 couple/get 的状态信号专用,不外扩)
     */
    suspend fun getOnline(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_ONLINE, isPost = false)
}
