package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.AnniversaryCreateReq
import com.zyc.love_baby.data.model.AnniversaryDeleteReq

/**
 * AnniversaryApi — 纪念日域接口(INTEGRATION-DESIGN §5.2 anniversary 组,需登录,M5)
 * 用法:协程内 val r = AnniversaryApi.list(pager);列表端点 data 为数组,经 ApiResult.Ok.dataArray 携带
 *      (HttpClient M3 增量),由调用方以 AnniversaryInfo.listFrom 解析;create 回包单个 VO 用
 *      AnniversaryInfo.from
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);AnniversaryModels
 * 事件:无(状态统一落 state/AnniversaryStore,由调用方驱动)
 * 功能:
 *  - list 全量列表(服务端按 anniv_date asc,id asc 排序;端上整表覆盖 AnniversaryStore,§5.2/§7.4)
 *  - create/delete 写穿透入口(§7.4:乐观回滚在 Store 层做,本层不加业务 toast):
 *    ⚠ 写端点一律 POST(§5.2 user 组注记:core 2.29 NetworkModule 仅 GET/POST),delete 传 {"id"};
 *    无 update 端点(D-17:删了重加)
 *  - 归属与未绑定:按 id 写操作服务端强制 couple 归属校验(1_003_006_001 防 IDOR),未绑定回
 *    1_003_004_003——均走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 *    (1_003_004_003 的状态信号放行是 couple/get 专用,§5.2)
 */
internal object AnniversaryApi {

    private const val PATH_LIST = "/love/anniversary/list"
    private const val PATH_CREATE = "/love/anniversary/create"
    private const val PATH_DELETE = "/love/anniversary/delete"

    /** 全量列表:data 为数组(ApiResult.Ok.dataArray),GET 无查询参数 */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)

    /** 新建纪念日:annivDate "yyyy-MM-dd"(服务端 @Pattern + LocalDate.parse 双重校验,非法回 1_003_006_002) */
    suspend fun create(
        pager: Pager,
        title: String,
        annivDate: String,
        repeatType: Int,
        emoji: String,
    ): ApiResult =
        HttpClient.request(
            pager,
            PATH_CREATE,
            isPost = true,
            body = AnniversaryCreateReq(title, annivDate, repeatType, emoji).toJson(),
        )

    suspend fun delete(pager: Pager, id: String): ApiResult =
        HttpClient.request(pager, PATH_DELETE, isPost = true, body = AnniversaryDeleteReq(id).toJson())
}
