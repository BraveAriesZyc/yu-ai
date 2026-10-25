package com.zyc.love_baby.network

/**
 * AMapConfig — 高德 Web 服务 key 与端点(M26 静态地图 + 逆地理,独立于 ApiConfig 的 yudao 通道)
 *
 * ⚠ 使用前申请:高德开放平台(lbs.amap.com)注册个人开发者 → 控制台「应用管理」创建应用 →
 * 添加 key,服务平台选「Web 服务」(静态地图 REST 与 regeo REST 同类型共用)→ 把 key 填到下方 KEY。
 * key 直连客户端(个人应用可接受,quota 缓解在 AMapApi/调用侧记忆化)。
 * 未配置时 isConfigured()=false:地图卡/地址行降级为纯文字,不拦功能。
 */
internal object AMapConfig {

    /** 高德 Web 服务 key(个人开发者免费额度约 5000 次/日、QPS 3) */
    const val KEY = "fbeed51fb9a3d7fd95f0bd14e52e1fe5"

    const val REST_BASE = "https://restapi.amap.com"
    const val PATH_REGEO = "/v3/geocode/regeo"
    const val PATH_STATIC_MAP = "/v3/staticmap"

    /** 静态地图默认出图尺寸(* 分隔;scale=2 高清) */
    const val STATIC_SIZE = "600*360"

    fun isConfigured(): Boolean = KEY.isNotEmpty() && !KEY.startsWith("<")
}
