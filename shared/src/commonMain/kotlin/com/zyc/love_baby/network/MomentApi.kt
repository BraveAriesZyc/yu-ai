package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.MomentCreateReq
import com.zyc.love_baby.data.model.MomentDeleteReq

/**
 * MomentApi — 相册瞬间域接口(INTEGRATION-DESIGN §5.2 moment 组,需登录,M9)
 * 用法:协程内 val r = MomentApi.list(pager);list 端点 data 为数组,经 ApiResult.Ok.dataArray
 *      携带(HttpClient M3 增量),由调用方以 MomentInfo.listFrom 解析;create 回包单个 VO 用
 *      MomentInfo.from(供发布后头插落态,不重拉)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);MomentModels
 * 事件:无(相册页数据页内自持不进 AppState,M7 时间线页先例——由调用方驱动,无 Store)
 * 功能:
 *  - list 游标分页:id 倒序新→旧;beforeId=0 取最新一页,非 0 取 id 小于它的下一页(端上「加载更多」
 *    传当前列表最小 id);limit 端上显式传(30 进页/加载更多同值,回包条数 < limit 判无更多)
 *  - create 发布瞬间(imageUrl 为 pickAndUpload 回包 data;text 空串 = 无配文):回包 RespVO 含
 *    服务端 id/createTime,调用方头插落态(todo create 先例)
 *  - delete 仅上传者本人可删(1_003_008_001):传 {"id"};无 update 端点(删了重发,D-17 口径)
 *  - ⚠ 查询参数直接拼 URL 而非走 core param 通道(MoodApi 同款:core 各宿主对 GET param 转 query
 *    行为不一,beforeId/limit 为纯数字 URL 安全字符,显式拼接五端一致)
 *  - 未绑定回 1_003_004_003 走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 *    (1_003_004_003 的状态信号放行是 couple/get 专用,§5.2)
 */
internal object MomentApi {

    private const val PATH_LIST = "/love/moment/list"
    private const val PATH_CREATE = "/love/moment/create"
    private const val PATH_DELETE = "/love/moment/delete"

    /**
     * 游标分页列表:data 为数组(ApiResult.Ok.dataArray)
     * @param beforeId 游标:取 id 小于该值的下一页;0 = 首页(最新一页)
     * @param limit 每页条数(服务端默认 30 上限 100,端上显式传 30)
     */
    suspend fun list(pager: Pager, beforeId: String, limit: Int): ApiResult =
        HttpClient.request(pager, "$PATH_LIST?beforeId=$beforeId&limit=$limit", isPost = false)

    /** 发布瞬间:回包 data 为瞬间 VO(含服务端 id/createTime) */
    suspend fun create(pager: Pager, imageUrl: String, text: String): ApiResult =
        HttpClient.request(pager, PATH_CREATE, isPost = true, body = MomentCreateReq(imageUrl, text).toJson())

    /** 删除瞬间(仅上传者本人):传 {"id"} */
    suspend fun delete(pager: Pager, id: String): ApiResult =
        HttpClient.request(pager, PATH_DELETE, isPost = true, body = MomentDeleteReq(id).toJson())
}
