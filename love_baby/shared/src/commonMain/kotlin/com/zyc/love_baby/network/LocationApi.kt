package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.LocationReportReq

/**
 * LocationApi — 位置/轨迹域接口(M26,需登录+绑定)
 * 用法:协程内 val r = LocationApi.report(pager, ...)/latest(pager)/trail(pager, ...)/
 *      placeList(pager, ...)/pull(pager);report 回单个点(LocationPointInfo)、latest 回
 *      LocationLatestRespInfo、trail 回 LocationTrailRespInfo、placeList 回
 *      LocationPlaceListRespInfo,均经 ApiResult.Ok.data 解析
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);LocationModels;AMapRegeo(地址明细载体)
 * 事件:无(位置状态统一落 state/LocationStore,由调用方驱动)
 * 功能:
 *  - report 上报位置点(坐标调用方纠偏 GCJ-02 后传入;addr 可空 regeo 明细随点上报,仅落
 *    地点表 M28;服务端 3s 限频静默吞点仍回最新点 + 顺带物理清 30 天旧点)——我方位置
 *    共享关闭时拒收(1_003_015_000)
 *  - latest 位置页 60s 轮询用(双方最新点,partner 开关关回 shareEnabled=false)
 *  - trail 轨迹区间查询:target="self"|"partner",startTs/endTs epoch 毫秒闭区间;⚠ GET 查询参数
 *    经 network.queryPath 显式拼进 path(已核 core KRNetworkModule.createRequestUrl 对带 query
 *    的 path 原样直出,本域参数纯 ASCII 无需 encode)
 *  - placeList 地点记录列表(M28,参数口径与 trail 一致,升序最近 100 条 + truncated)
 *  - pull 提醒对方更新位置(M28):WS 信号恒 success,对方前台+开共享才回点
 */
internal object LocationApi {

    private const val PATH_REPORT = "/love/share/location/report"
    private const val PATH_LATEST = "/love/share/location/latest"
    private const val PATH_TRAIL = "/love/share/location/trail"
    private const val PATH_PLACE_LIST = "/love/share/location/place/list"
    private const val PATH_PULL = "/love/share/location/pull"

    /**
     * 上报位置点:lat/lng 为 GCJ-02(纠偏在调用方);reportTime 端上 fix 时间 epoch 毫秒;
     * addr 可空 regeo 明细(M28:省市区+POI 随点上报,仅落地点表,不影响 snapshot/回包)
     */
    suspend fun report(
        pager: Pager,
        lat: Double,
        lng: Double,
        accuracy: Int,
        reportTime: Long,
        addr: AMapRegeo? = null,
    ): ApiResult = HttpClient.request(
        pager, PATH_REPORT, isPost = true,
        body = LocationReportReq(
            lat = lat, lng = lng, accuracy = accuracy, reportTime = reportTime,
            addrText = addr?.text, addrProv = addr?.province, addrCity = addr?.city,
            addrDist = addr?.district, addrPoi = addr?.poi,
        ).toJson(),
    )

    /** 查双方最新位置(只读轮询) */
    suspend fun latest(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LATEST, isPost = false)

    /**
     * 轨迹区间查询:target="self"|"partner",升序最近 500 点 + truncated 标志。
     * M28.1 起暂无调用方(半层纯列表化,服务端端点仍在);点级轨迹线二期复用
     */
    suspend fun trail(pager: Pager, target: String, startTs: Long, endTs: Long): ApiResult =
        HttpClient.request(
            pager, queryPath(PATH_TRAIL, "target" to target, "startTs" to startTs, "endTs" to endTs),
            isPost = false,
        )

    /** 地点记录列表(M28):target="self"|"partner",升序最近 100 条 + truncated 标志 */
    suspend fun placeList(pager: Pager, target: String, startTs: Long, endTs: Long): ApiResult =
        HttpClient.request(
            pager, queryPath(PATH_PLACE_LIST, "target" to target, "startTs" to startTs, "endTs" to endTs),
            isPost = false,
        )

    /** 提醒对方更新位置(M28):WS 信号,恒 success(对方前台+开共享才实际回点) */
    suspend fun pull(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_PULL, isPost = true)
}
