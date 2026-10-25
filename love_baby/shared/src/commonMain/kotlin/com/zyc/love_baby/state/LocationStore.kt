package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.GeoKit
import com.zyc.love_baby.base.LocationFix
import com.zyc.love_baby.base.LocationModule
import com.zyc.love_baby.data.model.LocationLatestRespInfo
import com.zyc.love_baby.data.model.LocationPlaceInfo
import com.zyc.love_baby.data.model.LocationPlaceListRespInfo
import com.zyc.love_baby.data.model.LocationPointInfo
import com.zyc.love_baby.data.model.LocationTrailRespInfo
import com.zyc.love_baby.data.model.ShareSettingInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.AMapApi
import com.zyc.love_baby.network.AMapConfig
import com.zyc.love_baby.network.AMapRegeo
import com.zyc.love_baby.network.LocationApi
import com.zyc.love_baby.network.ShareApi
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * LocationStore — 位置共享/行动轨迹快照单例(M26,BatteryStore 同范式)
 * 用法:读 myPoint/partnerPoint/partnerShareEnabled 渲染位置页;doReport(位置页 5s 会话
 * 循环每拍直调,M27 起公开)/refresh(回前台/回页补拉)驱动上报;closeShareSession(MainPager
 * 前台钩子,会话收口)关闸;applyPartnerPoint(WS 实时帧直落,M27);loadPlaceList 地点记录
 * 查询(缓存,M28;点级轨迹 loadTrail 已随 M28.1 半层纯列表化移除);resolveAddress 逆地理
 * (>100m 才重查);locationShareEnabled 会话闸由位置页 updateShare(true)/closeShareSession
 * 读写;reset 随登出/解绑清场
 * 依赖:androidx.compose.runtime 快照状态;LocationApi/ShareApi/AMapApi(裸 GET);
 *      base/LocationModule(原生定位,Android 注册/其余端 null 降级)+ GeoKit(WGS-84→GCJ-02
 *      纠偏唯一入口);kotlinx Mutex(上报单飞)
 * 事件:轮询/上报失败静默(只读降级);开关更新回 ApiResult 由调用方按需处置
 * 功能:
 *  - 会话共享(M29 去开关化,对标微信共享实时位置):locationShareEnabled=会话闸——进
 *    位置页权限就绪即开(updateShare(true),失败随 5s 拍重试),回主页关(closeShareSession,
 *    MainPager 前台/重连钩子);强隐私 §12.4 由「会话」承载,主页 5min ticker 与 4min 节流
 *    已随会话化退役(在主页恒 no-op,删除)
 *  - 上报 = 原生单次定位(前台闸在原生侧)→ GeoKit 纠偏 → regeo 明细(100m 缓存,M28 起
 *    省市区+POI 随点上报地点表)→ location/report(3s 服务端限频静默吞点仍回最新点);
 *    会话闸关不上报(服务端同闸拒收,双保险)
 *  - 单飞:Mutex.tryLock 在飞直接放弃(下一拍补位,不排队)
 *  - 地点缓存 per( target|startTs|endTs )(loadPlaceList,M28 地点记录),手动刷新 force
 *    旁路;地址缓存按位移 100m 失效
 *  - 无后台采集(产品定案):原生前台闸(退后台定位回 err=background)+会话闸双保险,
 *    后台不出点
 */
internal object LocationStore {

    // ---- 双方最新点(latest 轮询/report 回包落值;null=从未上报) ----
    var myPoint by mutableStateOf<LocationPointInfo?>(null)
        private set
    var partnerShareEnabled by mutableStateOf(false)
        private set
    var partnerPoint by mutableStateOf<LocationPointInfo?>(null)
        private set

    /** latest 是否成功拉过(位置页区分「加载中」与「TA 未开启共享」空态) */
    var latestLoaded by mutableStateOf(false)
        private set

    /** 我方位置共享会话闸(M29 会话语义:进位置页开/回主页关;懒创建默认关,强隐私 §12.4) */
    var locationShareEnabled by mutableStateOf(false)
        private set

    /** 我方最新点文字地址(逆地理缓存;空=未解析/未配 key,UI 降级显坐标) */
    var myAddress by mutableStateOf("")
        private set

    /** 我方最新点逆地理明细(M28:省市区+POI 随点上报地点表用;null=未解析/未配 key) */
    private var myAddrDetail: AMapRegeo? = null

    /** 上报单飞(位置页 5s 会话拍与回页补拉并发去重) */
    private val reportMutex = Mutex()

    /** 地点记录缓存(M28):键 target|startTs|endTs → (升序地点列, truncated) */
    private val placeCache = HashMap<String, Pair<List<LocationPlaceInfo>, Boolean>>()

    /** 逆地理缓存锚点(位移 <100m 沿用 myAddress) */
    private var addressLat = 0.0
    private var addressLng = 0.0

    /**
     * 位置页补拉入口(回前台/回页 refreshTick 用):先上报自己(拿新鲜点)再拉双方最新;
     * 上报失败不影响 latest 照拉(两段独立降级)。M27 起页面会话循环走 doReport 直调
     * (5s 拍),本入口只服务低频路径
     */
    suspend fun refresh(pager: Pager) {
        doReport(pager)
        refreshLatest(pager)
    }

    /** 拉双方最新点(只读;位置页 5s 拍的 30s 兜底与回页补拉用) */
    suspend fun refreshLatest(pager: Pager) {
        val result = LocationApi.latest(pager)
        if (result is ApiResult.Ok) {
            LocationLatestRespInfo.from(result.data)?.let { resp ->
                myPoint = resp.mine
                partnerShareEnabled = resp.partner.shareEnabled
                partnerPoint = resp.partner.point
                latestLoaded = true
            }
        }
    }

    /**
     * WS 实时帧落点(M27 love-location-new,ChatPushStore 分发):TA 上报落库即推,此处
     * 直换 partnerPoint + partnerShareEnabled 乐观置开(能推点来=共享开着),地图 marker
     * 秒级跟随;不做校验兜底——latest 30s 兜底轮询负责纠偏回真值(服务端只在落库成功
     * 分支推,非法点进不来);与轮询并发写无序性问题可接受(点带 reportTime,后写覆盖即可)
     */
    fun applyPartnerPoint(point: LocationPointInfo) {
        partnerShareEnabled = true
        partnerPoint = point
    }

    /**
     * 共享会话收口(MainPager 前台/重连钩子,M29):先 loadSetting 同步服务端开关——杀进程
     * 残态下 store 默认 false 而服务端闸仍开,TA 侧会看到陈旧「共享中」,此处归位;仍开才
     * updateShare(false) 关闸(正常回主页路径 store 已关,零写网络)
     */
    suspend fun closeShareSession(pager: Pager) {
        loadSetting(pager)
        if (locationShareEnabled) {
            updateShare(pager, false)
        }
    }

    /**
     * 上报一次:会话闸关/桥缺失/定位失败(含前台闸 background/native 超时)/接口失败都静默
     * 放弃回 false——采集链路不 toast(会话闸+服务端限频双闸兜底)。
     * 位置页 5s 会话循环每拍直调(单飞 Mutex 在飞即弃,密集调用安全)
     */
    suspend fun doReport(pager: Pager): Boolean {
        if (!locationShareEnabled) return false
        if (!reportMutex.tryLock()) return false // 在飞直接放弃,下一轮补位(不排队)
        try {
            val fix = nativeFix(pager) ?: return false
            if (!fix.ok) return false
            val (lat, lng) = GeoKit.wgs84ToGcj02(fix.lat, fix.lng)
            // M28:regeo 明细随点上报(100m 缓存锚点内零网络;解析失败/未配 key 传 null,
            // 地点降级不影响上报本体)
            val addr = resolveAddrDetail(pager, lat, lng)
            val result = LocationApi.report(pager, lat, lng, fix.accuracy.toInt(), nowEpochMs(), addr)
            if (result is ApiResult.Ok) {
                LocationPointInfo.from(result.data)?.let { myPoint = it }
            }
            return true
        } finally {
            reportMutex.unlock()
        }
    }

    /** 原生单次定位(桥缺失回 null;LocationFix.ok=false 含 err 语义,调用方按需分流) */
    private suspend fun nativeFix(pager: Pager): LocationFix? {
        val module = pager.getModule<LocationModule>(LocationModule.MODULE_NAME) ?: return null
        return try {
            suspendCoroutine<LocationFix> { cont ->
                module.getCurrentLocation { fix -> cont.resume(fix) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 拉我的共享开关(closeShareSession 先行同步用,归位杀进程残态;位置页不再读持久开关) */
    suspend fun loadSetting(pager: Pager) {
        val result = ShareApi.getSetting(pager)
        if (result is ApiResult.Ok) {
            ShareSettingInfo.from(result.data)?.let { locationShareEnabled = it.locationShare }
        }
    }

    /** 更新位置共享会话闸:成功落值回 Ok;失败不落值(位置页随 5s 拍重试) */
    suspend fun updateShare(pager: Pager, enabled: Boolean): ApiResult {
        val result = ShareApi.updateSetting(pager, batteryShare = null, locationShare = enabled)
        if (result is ApiResult.Ok) {
            ShareSettingInfo.from(result.data)?.let { locationShareEnabled = it.locationShare }
        }
        return result
    }

    /**
     * 地点记录查询(M28):缓存优先,force=手动刷新旁路;失败回 null(UI 保留旧数据)。
     * 列表服务端升序原样保留;shareEnabled=false 时 places 恒空
     */
    suspend fun loadPlaceList(
        pager: Pager,
        target: String,
        startTs: Long,
        endTs: Long,
        force: Boolean = false,
    ): Pair<List<LocationPlaceInfo>, Boolean>? {
        val key = "$target|$startTs|$endTs"
        if (!force) {
            placeCache[key]?.let { return it }
        }
        val result = LocationApi.placeList(pager, target, startTs, endTs)
        if (result !is ApiResult.Ok) return null
        val resp = LocationPlaceListRespInfo.from(result.data) ?: return null
        val value = Pair(resp.places, resp.truncated)
        placeCache[key] = value
        return value
    }

    /**
     * 我方最新点逆地理(高德 regeo):未配 key 回空串(降级显坐标);缓存锚点位移 <100m
     * 沿用旧地址(配额缓解);查询失败沿用旧值(可能为空)。resolveAddrDetail 的文字薄壳
     * (地址胶囊用;地点上报走明细版)
     */
    suspend fun resolveAddress(pager: Pager, lat: Double, lng: Double): String {
        resolveAddrDetail(pager, lat, lng)
        return myAddress
    }

    /**
     * 逆地理明细内核(M28):regeoDetail 拉全量,myAddress(文字)与 myAddrDetail(省市区+POI)
     * 同源落值;缓存锚点位移 <100m 沿用旧明细(配额缓解);查询失败沿用旧值(可能 null)
     */
    private suspend fun resolveAddrDetail(pager: Pager, lat: Double, lng: Double): AMapRegeo? {
        if (!AMapConfig.isConfigured()) return null
        if (myAddrDetail != null && AMapApi.haversineKm(addressLat, addressLng, lat, lng) < 0.1) {
            return myAddrDetail
        }
        val detail = AMapApi.regeoDetail(pager, lat, lng) ?: return myAddrDetail
        addressLat = lat
        addressLng = lng
        myAddrDetail = detail
        myAddress = detail.text
        return detail
    }

    /** 登出/解绑清场(含地点/地址缓存;会话闸复位默认关) */
    fun reset() {
        myPoint = null
        partnerShareEnabled = false
        partnerPoint = null
        latestLoaded = false
        locationShareEnabled = false
        myAddress = ""
        myAddrDetail = null
        placeCache.clear()
        addressLat = 0.0
        addressLng = 0.0
    }
}
