package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.WishCreateReq
import com.zyc.love_baby.data.model.WishDeleteReq
import com.zyc.love_baby.data.model.WishDoneReq

/**
 * WishApi — 恋爱清单域接口(INTEGRATION-DESIGN §5.2 wish 组,M14,需登录)
 * 用法:协程内 val r = WishApi.list(pager);列表端点 data 为数组,经 ApiResult.Ok.dataArray 携带
 *      (HttpClient M3 增量),由调用方以 WishInfo.listFrom 解析;create/done 回包单个 VO 用 WishInfo.from
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);WishModels
 * 事件:无(页内自持不进 AppState,M14 D-19g——页销毁即弃,登出/解绑零 reset 接线)
 * 功能:
 *  - list 全量列表(服务端按 create_time asc,id asc 排序;端上分层重排 D-19f,页内整体覆盖)
 *  - create/done/delete 写入口(失败 toast 在页面层做,本层不加业务):
 *    ⚠ 写端点一律 POST(§5.2:core 2.29 NetworkModule 仅 GET/POST,send-wishes 先例)
 *  - done 单口双向(D-19b):done=true CAS 置位恰发一条庆祝消息(重复勾选幂等,回包真值
 *    doneBy/doneTime 不变),done=false 清空三态不发消息;回包恒为操作后库真值对象
 *    (D-19h:端上原位替换本地行)
 *  - 归属与未绑定:按 id 写操作服务端强制 couple 归属校验(1_003_009_000 防 IDOR/防探测),
 *    未绑定回 1_003_004_003——均走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 */
internal object WishApi {

    private const val PATH_LIST = "/love/wish/list"
    private const val PATH_CREATE = "/love/wish/create"
    private const val PATH_DONE = "/love/wish/done"
    private const val PATH_DELETE = "/love/wish/delete"

    /** 全量列表:data 为数组(ApiResult.Ok.dataArray),GET 无查询参数 */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)

    /** 新建心愿:服务端固定初始未完成态,回包含回填的 id/createTime(todo create 先例) */
    suspend fun create(pager: Pager, title: String): ApiResult =
        HttpClient.request(pager, PATH_CREATE, isPost = true, body = WishCreateReq(title).toJson())

    /** 勾选完成/取消勾选(单口双向):回包 = 操作后服务端真值(D-19h 原位替换源) */
    suspend fun done(pager: Pager, id: String, done: Boolean): ApiResult =
        HttpClient.request(pager, PATH_DONE, isPost = true, body = WishDoneReq(id, done).toJson())

    /** 删除心愿:仅创建者可删(1_003_009_001 服务端兜底);逻辑删除,传播靠全量覆盖 */
    suspend fun delete(pager: Pager, id: String): ApiResult =
        HttpClient.request(pager, PATH_DELETE, isPost = true, body = WishDeleteReq(id).toJson())
}
