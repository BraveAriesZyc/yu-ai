package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.TodoCreateReq
import com.zyc.love_baby.data.model.TodoDeleteReq
import com.zyc.love_baby.data.model.TodoUpdateReq

/**
 * TodoApi — 待办域接口(INTEGRATION-DESIGN §5.2 todo 组,需登录)
 * 用法:协程内 val r = TodoApi.list(pager);列表端点 data 为数组,经 ApiResult.Ok.dataArray 携带
 *      (HttpClient M3 增量),由调用方以 TodoInfo.listFrom 解析;create/update 回包单个 VO 用 TodoInfo.from
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);TodoModels
 * 事件:无(待办状态统一落 state/TodoStore,由调用方驱动)
 * 功能:
 *  - list 全量列表(服务端按 create_time asc,id asc 排序;端上整表覆盖 TodoStore,§5.2/§7.4)
 *  - create/update/delete 写穿透入口(§7.4:乐观更新 + 失败回滚在 Store 层做,本层不加业务 toast):
 *    ⚠ 写端点一律 POST(§5.2 user 组注记:core 2.29 NetworkModule 仅 GET/POST;原 PUT/DELETE 已于
 *    M3 改 POST body,§5.2 todo 组表)——update 五字段全量上送含 toggle done,delete 传 {"id"}
 *  - 归属与未绑定:按 id 写操作服务端强制 couple 归属校验(1_003_001_001 防 IDOR),未绑定回
 *    1_003_004_003——均走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 *    (1_003_004_003 的状态信号放行是 couple/get 专用,§5.2)
 */
internal object TodoApi {

    private const val PATH_LIST = "/love/todo/list"
    private const val PATH_CREATE = "/love/todo/create"
    private const val PATH_UPDATE = "/love/todo/update"
    private const val PATH_DELETE = "/love/todo/delete"

    /** 全量列表:data 为数组(ApiResult.Ok.dataArray),GET 无查询参数 */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)

    /** 新建待办:color 为标签胶囊 hex 色值(String),tag 为标签文本(家庭/恋爱/健康/生活/随手记,§4) */
    suspend fun create(pager: Pager, title: String, tag: String, color: String): ApiResult =
        HttpClient.request(pager, PATH_CREATE, isPost = true, body = TodoCreateReq(title, tag, color).toJson())

    /** 整条更新(last-write 覆盖,§7.4 D-6):编辑语义 = 端上当前整项五字段全量上送;done 翻转亦走此端点 */
    suspend fun update(
        pager: Pager,
        id: String,
        title: String,
        done: Boolean,
        tag: String,
        color: String,
    ): ApiResult =
        HttpClient.request(pager, PATH_UPDATE, isPost = true, body = TodoUpdateReq(id, title, done, tag, color).toJson())

    suspend fun delete(pager: Pager, id: String): ApiResult =
        HttpClient.request(pager, PATH_DELETE, isPost = true, body = TodoDeleteReq(id).toJson())
}
