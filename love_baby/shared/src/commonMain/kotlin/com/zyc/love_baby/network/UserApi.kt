package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.UserUpdateReq

/**
 * UserApi — 用户资料域接口(INTEGRATION-DESIGN §5.2 user 组,需登录)
 * 用法:协程内 val r = UserApi.getUser(pager) / UserApi.updateUser(pager, nickname = "新昵称")
 * 依赖:HttpClient;CoupleModels(UserUpdateReq/UserProfile,解析在调用方)
 * 事件:无
 * 功能:
 *  - getUser 个人信息(§5.2 M-6:MEMBER 上下文 buildUserInfo 返回空 Map,昵称/头像一律由此接口回查 love_user)
 *  - updateUser 改昵称/头像。⚠ 用 POST(§5.2 原写 PUT):core 2.29 NetworkModule 只支持 GET/POST
 *    (反编译 AAR 实锤,M2 定案),app-api 写接口一律 POST;admin-api 不受限(axios)仍 RESTful;
 *    M3 的 todo update/delete 同理届时按此修正
 */
internal object UserApi {

    private const val PATH_GET = "/love/user/get"
    private const val PATH_UPDATE = "/love/user/update"
    private const val PATH_STATS = "/love/user/stats"
    private const val PATH_CANCEL = "/love/user/cancel"

    suspend fun getUser(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_GET, isPost = false)

    /** 恋爱统计(在一起天数+悄悄话/待办/心情计数,未绑定全 0;解析落 StatsStore 由调用方收口) */
    suspend fun getStats(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_STATS, isPost = false)

    /**
     * 改个人资料:仅传非空字段(未传字段不进 body,防服务端把未编辑字段误清空;
     * 例外见 UserUpdateReq——sex 非 null 即传、signature 非 null 即传含空串=清空)。
     * 三老调用方(昵称浮层/头像上传)走默认参零改动
     */
    suspend fun updateUser(
        pager: Pager,
        nickname: String? = null,
        avatar: String? = null,
        sex: Int? = null,
        birthday: String? = null,
        signature: String? = null,
    ): ApiResult = HttpClient.request(
        pager,
        PATH_UPDATE,
        isPost = true,
        body = UserUpdateReq(nickname, avatar, sex, birthday, signature).toJson(),
    )

    /**
     * 注销账号(M33 上架合规,商店硬性要求):无请求体,确认浮层二次确认后调用。
     * 服务端口径 = 解绑(物理删 couple)+ 个人敏感数据物理清除(位置轨迹/地点/电量/共享开关)
     * + mobile 改写释放 uk + 逻辑删用户 + 撤当前 token;共同业务数据保留(对方一侧历史不抹)。
     * 不可逆:成功后调用方 SessionStore.clear 回 Gate(与登出同路)
     */
    suspend fun cancel(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_CANCEL, isPost = true)
}
