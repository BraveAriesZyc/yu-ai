package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * LocationModels — 位置/轨迹域请求/响应模型(M26,§5.1/§7.1 DTO 约定同 TodoModels)
 * 用法:请求体 LocationReportReq(...).toJson() 传 LocationApi;回包 VO 经各 Info.from 解析,
 *      trail 的 points 数组经 LocationPointInfo.listFrom 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(无序列化插件)
 * 功能:
 *  - location 组:report(30s 限频+30 天保留,回操作后我的最新点)/latest(双方最新)/
 *    trail(target=startTs&endTs,升序最近 500 点+truncated)
 *  - 坐标一律 GCJ-02(上传前经 base/GeoKit.wgs84ToGcj02 纠偏;服务端原样存取,高德系直接可用)
 *  - reportTime epoch 毫秒(端上 fix 时间,非入库时间);id String(§5.1)
 *  - 空值口径:mine/point 嵌套对象 null=从未上报(optJSONObject 判空);
 *    trail shareEnabled=false 时 points 恒空(「没共享」与「没数据」靠该字段区分,不打错误码)
 *  - 错误码(1_003_015_xxx):000 位置共享未开启(我方上报被拒)/001 位置参数不合法——正常使用
 *    端上不会触发,统一走 BizError toast,不设常量
 */

// ---------- 请求体(POST + application/json) ----------

/**
 * 上报位置点(location/report):lat/lng 为 GCJ-02(调用方纠偏后传入);accuracy 米,0=未知;
 * reportTime 端上 fix 时间 epoch 毫秒(null=服务端 now 兜底);
 * M28 地址五字段可空(regeo 明细随点上报,仅落地点表 place;全空也落地点行,UI 降级显坐标)
 */
internal data class LocationReportReq(
    val lat: Double,
    val lng: Double,
    val accuracy: Int,
    val reportTime: Long,
    val addrText: String? = null,
    val addrProv: String? = null,
    val addrCity: String? = null,
    val addrDist: String? = null,
    val addrPoi: String? = null,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("lat", lat)
            .put("lng", lng)
            .put("accuracy", accuracy)
            .put("reportTime", reportTime)
        // 地址五字段仅非空才 put(put null 会序列化出 null 字段,报文保持干净)
        if (!addrText.isNullOrEmpty()) json.put("addrText", addrText)
        if (!addrProv.isNullOrEmpty()) json.put("addrProv", addrProv)
        if (!addrCity.isNullOrEmpty()) json.put("addrCity", addrCity)
        if (!addrDist.isNullOrEmpty()) json.put("addrDist", addrDist)
        if (!addrPoi.isNullOrEmpty()) json.put("addrPoi", addrPoi)
        return json
    }
}

// ---------- 响应模型 ----------

/** 位置点(latest/trail 通用结构);坐标 GCJ-02 */
internal data class LocationPointInfo(
    val id: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Int,
    val reportTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(嵌套 mine/point 缺省与 listFrom 逐条跳过共用) */
        fun from(data: JSONObject?): LocationPointInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return LocationPointInfo(
                id = id,
                lat = data.optDouble("lat", 0.0),
                lng = data.optDouble("lng", 0.0),
                accuracy = data.optInt("accuracy", 0),
                reportTime = data.optLong("reportTime", 0),
            )
        }

        /** trail 的 points 数组解析(服务端升序原样保留,端上不重排);空/缺省返回空列表 */
        fun listFrom(dataArray: JSONArray?): List<LocationPointInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<LocationPointInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}

/** TA 的位置(latest 回包 partner 结构):point null = 开关关或从未上报(再用 shareEnabled 细分) */
internal data class LocationPartnerInfo(
    val shareEnabled: Boolean,
    val point: LocationPointInfo?,
)

/** latest 回包:mine 可 null(从未上报),partner 恒有 */
internal data class LocationLatestRespInfo(
    val mine: LocationPointInfo?,
    val partner: LocationPartnerInfo,
) {
    companion object {
        fun from(data: JSONObject?): LocationLatestRespInfo? {
            if (data == null) return null
            val partner = data.optJSONObject("partner") ?: return null
            return LocationLatestRespInfo(
                mine = LocationPointInfo.from(data.optJSONObject("mine")),
                partner = LocationPartnerInfo(
                    shareEnabled = partner.optBoolean("shareEnabled"),
                    point = LocationPointInfo.from(partner.optJSONObject("point")),
                ),
            )
        }
    }
}

/** trail 回包:shareEnabled=false 时 points 恒空;truncated=true = 被 500 点上限截断(端上注记) */
internal data class LocationTrailRespInfo(
    val shareEnabled: Boolean,
    val points: List<LocationPointInfo>,
    val truncated: Boolean,
) {
    companion object {
        fun from(data: JSONObject?): LocationTrailRespInfo? {
            if (data == null) return null
            return LocationTrailRespInfo(
                shareEnabled = data.optBoolean("shareEnabled"),
                points = LocationPointInfo.listFrom(data.optJSONArray("points")),
                truncated = data.optBoolean("truncated"),
            )
        }
    }
}

// ---------- 地点记录(M28 行动轨迹,place/list) ----------

/**
 * 地点记录(place/list 通用结构):坐标 GCJ-02;省市区/地址/POI 空串 = 端上没解析到
 * (UI 降级显坐标);reportTime epoch 毫秒
 */
internal data class LocationPlaceInfo(
    val id: String,
    val lat: Double,
    val lng: Double,
    val province: String,
    val city: String,
    val district: String,
    val address: String,
    val poi: String,
    val reportTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过共用) */
        fun from(data: JSONObject?): LocationPlaceInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return LocationPlaceInfo(
                id = id,
                lat = data.optDouble("lat", 0.0),
                lng = data.optDouble("lng", 0.0),
                // 服务端回的都是 JSON String 或缺省,null 经 optString 归 "",无 optString 陷阱
                province = data.optString("province"),
                city = data.optString("city"),
                district = data.optString("district"),
                address = data.optString("address"),
                poi = data.optString("poi"),
                reportTime = data.optLong("reportTime", 0),
            )
        }

        /** place/list 的 places 数组解析(服务端升序原样保留,端上不重排);空/缺省返回空列表 */
        fun listFrom(dataArray: JSONArray?): List<LocationPlaceInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<LocationPlaceInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}

/** place/list 回包:shareEnabled=false 时 places 恒空;truncated=true = 被 100 条上限截断(端上注记) */
internal data class LocationPlaceListRespInfo(
    val shareEnabled: Boolean,
    val places: List<LocationPlaceInfo>,
    val truncated: Boolean,
) {
    companion object {
        fun from(data: JSONObject?): LocationPlaceListRespInfo? {
            if (data == null) return null
            return LocationPlaceListRespInfo(
                shareEnabled = data.optBoolean("shareEnabled"),
                places = LocationPlaceInfo.listFrom(data.optJSONArray("places")),
                truncated = data.optBoolean("truncated"),
            )
        }
    }
}
