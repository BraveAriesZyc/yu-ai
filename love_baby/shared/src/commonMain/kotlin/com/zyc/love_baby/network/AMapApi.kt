package com.zyc.love_baby.network

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * regeo 明细(M28 行动轨迹地点记录:省市区 + POI 随点上报;空串 = 没解析到,落库/展示降级)
 */
internal class AMapRegeo(
    val text: String,
    val province: String,
    val city: String,
    val district: String,
    val poi: String,
)

/**
 * AMapApi — 高德 Web 服务直连桥(M26,不经 HttpClient:yudao CommonResult 拆包语义不适用于
 * 高德回包,core NetworkModule 对 JSON 回包直接给 JSONObject,免剥壳)
 * 用法:
 *  - val addr = AMapApi.regeoDetail(pager, lat, lng) —— 逆地理明细(GCJ-02 → 文字地址+省市区+POI,
 *    M28 地点记录用);失败/未配 key 回 null
 *  - val text = AMapApi.regeo(pager, lat, lng) —— 逆地理薄壳(regeoDetail().text),地址胶囊用
 * 依赖:core NetworkModule 裸 GET(铁律⑦ 可空 getModule);AMapConfig
 * 事件:无(配额缓解:regeo 位移 >100m 才重查,缓存锚点在 LocationStore)
 * 坐标口径:全部 GCJ-02(AMapConfig/LocationApi 同源纠偏);⚠ 高德参数顺序恒 lng,lat(经度在前)
 * 注:staticMapUrl/trailMapUrl/fitZoom(M26 静态图族)已随 M28.1 轨迹半层纯列表化移除,
 * 需要时回 git 历史找
 */
internal object AMapApi {

    private const val TIMEOUT_SECONDS = 15
    private const val KEY_STATUS = "status"
    private const val KEY_REGEOCODE = "regeocode"
    private const val KEY_FORMATTED_ADDRESS = "formatted_address"
    private const val KEY_ADDRESS_COMPONENT = "addressComponent"
    private const val KEY_POIS = "pois"

    /**
     * 逆地理薄壳:只要 formatted_address(地址胶囊用);regeoDetail 的降级包装
     */
    suspend fun regeo(pager: Pager, lat: Double, lng: Double): String? =
        regeoDetail(pager, lat, lng)?.text

    /**
     * 逆地理明细(M28):extensions=all 拉全量,解析 formatted_address + addressComponent
     * (province/city/district)+ pois[0].name;失败/未配 key/网络错误回 null
     * ⚠ Kuikly optString 对非 String 值回其 toString 字面串(空数组 → "[]" 字面串,不是
     * fallback/空串,2026-10-25 核 core AbstractJSONObject 源实锤)——省市区/POI 一律
     * opt() + is String 判型;直辖市 city 可能是空数组(回 ""),pois 取不到回 ""
     */
    suspend fun regeoDetail(pager: Pager, lat: Double, lng: Double): AMapRegeo? {
        if (!AMapConfig.isConfigured()) return null
        val url = AMapConfig.REST_BASE + AMapConfig.PATH_REGEO +
                "?key=" + AMapConfig.KEY + "&location=" + lng + "," + lat + "&extensions=all"
        val root = rawGet(pager, url) ?: return null
        if (root.optString(KEY_STATUS) != "1") return null
        val regeocode = root.optJSONObject(KEY_REGEOCODE) ?: return null
        val address = regeocode.optString(KEY_FORMATTED_ADDRESS)
        if (address.isEmpty()) return null
        val component = regeocode.optJSONObject(KEY_ADDRESS_COMPONENT)
        fun fieldText(key: String): String {
            if (component == null) return ""
            val value = component.opt(key)
            return if (value is String) value else ""
        }
        var poi = ""
        val pois: JSONArray? = regeocode.optJSONArray(KEY_POIS)
        if (pois != null && pois.length() > 0) {
            val name = pois.optJSONObject(0)?.opt("name")
            if (name is String) poi = name
        }
        return AMapRegeo(
            text = address,
            province = fieldText("province"),
            city = fieldText("city"),
            district = fieldText("district"),
            poi = poi,
        )
    }

    /** 两点球面距离 km(LocationStore 地址缓存 100m 失效判定共用;纯函数无副作用) */
    internal fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) / 180.0 * PI
        val dLng = (lng2 - lng1) / 180.0 * PI
        val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 / 180.0 * PI) * cos(lat2 / 180.0 * PI) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * 裸 GET(HttpClient.rawRequest 同款线程桥,无 token/无拆包):高德 JSON 回包由 core 直接
     * 解析成 JSONObject;失败/超时/非 JSON 回 null(调用方一律降级,不打错)
     */
    private suspend fun rawGet(pager: Pager, url: String): JSONObject? {
        val network = try {
            pager.getModule<NetworkModule>(NetworkModule.MODULE_NAME)
        } catch (_: Exception) {
            null
        } ?: return null
        return suspendCoroutine { continuation ->
            network.httpRequest(url, false, JSONObject(), JSONObject(), null, TIMEOUT_SECONDS) { data, success, _, _ ->
                continuation.resume(if (success) data else null)
            }
        }
    }
}
