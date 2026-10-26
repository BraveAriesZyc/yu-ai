package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.MoodOptionCreateReq
import com.zyc.love_baby.data.model.MoodOptionDeleteReq
import com.zyc.love_baby.data.model.MoodRecordReq

/**
 * MoodApi — 心情域接口(INTEGRATION-DESIGN §5.2 mood 组 + D-14 M3.5 选项字典,需登录)
 * 用法:协程内 val r = MoodApi.list(pager, "2026-10");列表端点 data 为数组,经 ApiResult.Ok.dataArray
 *      携带(HttpClient M3 增量),由调用方以 MoodRecordInfo/MoodOptionInfo.listFrom 解析;
 *      option/create 回包为单对象,经 ApiResult.Ok.data 携带,由 MoodOptionInfo.from 解析
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);MoodModels
 * 事件:无(记录与字典增删统一落 state/MoodStore)
 * 功能:
 *  - record 记当日心情({"optionId":String},M3.5 改传 couple 私有字典选项编号);
 *    同日重复记 = 服务端 uk_user_date 覆盖写(§4),端上按成功处理
 *  - list 按月拉 couple 双方记录(month=yyyy-MM;某日无记录 = 回包无该日条目,端上按空缺渲染,§5.2);
 *    MoodPage 进入/切月由调用方换 month 重拉(§7.4 拉取触发点);记录附快照三列,
 *    月历渲染只吃快照,删除选项后历史不变灰(D-14)
 *  - optionList 查 couple 私有心情选项字典(进入心情页/切回 Tab 整表覆盖 MoodStore.moodOptions);
 *  - optionCreate/optionDelete 字典增删(写恒 POST,core 仅 GET/POST);上限 20/重名等服务端
 *    校验失败走 1_003_002_xxx 普通 BizError 通道由调用方 toast
 *  - ⚠ 查询参数经 network.queryPath 显式拼 URL(不走 core param 通道;「core 各宿主对 GET param
 *    转 query 行为不一」的决策与理由收口至 queryPath 注,month 为 yyyy-MM 纯 URL 安全字符)
 *  - 未绑定回 1_003_004_003 走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 *    (1_003_004_003 的状态信号放行是 couple/get 专用,§5.2)
 */
internal object MoodApi {

    private const val PATH_RECORD = "/love/mood/record"
    private const val PATH_RECORD_LIST = "/love/mood/record/list"
    private const val PATH_OPTION_LIST = "/love/mood/option/list"
    private const val PATH_OPTION_CREATE = "/love/mood/option/create"
    private const val PATH_OPTION_DELETE = "/love/mood/option/delete"

    /**
     * 记当日心情:传 couple 私有字典选项编号(optionId 一律 String,§5.1;
     * 跨 couple 选项 id 服务端按 1_003_002_001 拒绝)
     */
    suspend fun record(pager: Pager, optionId: String): ApiResult =
        HttpClient.request(pager, PATH_RECORD, isPost = true, body = MoodRecordReq(optionId).toJson())

    /**
     * 按月列表:data 为数组(ApiResult.Ok.dataArray)。
     * @param month yyyy-MM(如 2026-10),由调用方按当前展示月传入
     */
    suspend fun list(pager: Pager, month: String): ApiResult =
        HttpClient.request(pager, queryPath(PATH_RECORD_LIST, "month" to month), isPost = false)

    /** 字典列表:data 为数组(sort asc, id asc 归服务端;空数组 = 空字典,端上走空态自愈) */
    suspend fun optionList(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_OPTION_LIST, isPost = false)

    /** 新增心情选项:回包为单对象(data 携带,含服务端 id,Store.addOption 用回包落态) */
    suspend fun optionCreate(pager: Pager, label: String, emoji: String, color: String): ApiResult =
        HttpClient.request(pager, PATH_OPTION_CREATE, isPost = true,
            body = MoodOptionCreateReq(label, emoji, color).toJson())

    /** 删除心情选项:历史月历靠记录快照列渲染,删除后不变灰(D-14) */
    suspend fun optionDelete(pager: Pager, id: String): ApiResult =
        HttpClient.request(pager, PATH_OPTION_DELETE, isPost = true, body = MoodOptionDeleteReq(id).toJson())
}
