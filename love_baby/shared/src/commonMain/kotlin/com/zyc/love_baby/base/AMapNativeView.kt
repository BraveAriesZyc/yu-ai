package com.zyc.love_baby.base

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.event.Event

/**
 * AMapNativeView — 高德原生动态地图视图声明(M27,Compose DSL 嵌原生视图首个组件):
 * 老 DSL 四件套(DeclarativeBaseView/Attr/Event/viewName)声明 → Android 壳
 * KRAMapView(高德 3dmap MapView,viewName "HRAMapView")消费;其余端(iOS/ohos/H5)
 * 未注册该 viewName → renderView 永不创建,performTask 队列静默滞留,页面侧以
 * LocationModule 探测先行整页降级,不依赖本类报错。
 * 依赖:core DeclarativeBaseView(callRenderViewMethod 收口 renderView?.callMethod);
 *      compose 扩展 MakeKuiklyComposeNode(2.29 官方互操作正路,AndroidView 互操作是空 stub)
 * 用法:LocationPage 经 AMapNativeMap(pointsJson, fitTrigger, focusSeq, focusJson, modifier)
 * 组合进全屏地图
 * 方法(与 androidApp KRAMapView.call 分发名同串双源,勿单改一边):
 *  - updatePoints(json):{"points":[{role,lat,lng,avatarUrl,initial,colorStart,colorEnd,
 *    textColor}]} 增量落 marker;lat/lng GCJ-02 直用(高德系原生坐标系)
 *  - fitMarkers():两点包络回中(相机不随 marker 移动自动拉,由本方法/手动触发)
 *  - focusPoint(json):{"lat":..,"lng":..,"color":"#RRGGBB"} 回看标注(M28 地点列表点击:
 *    品牌色圆点落位 + 相机飞 16 级;fitMarkers 清标注回实时)
 */
class AMapNativeView : DeclarativeBaseView<AMapNativeAttr, AMapNativeEvent>() {

    /** fitTrigger 已应用的序号(去重:points 每拍变化会反复触发 viewUpdate,fit 只准发一次) */
    internal var appliedFitSeq = 0

    /** focusPoint 已应用的序号(同 appliedFitSeq 去重思路:递增才下发,防 viewUpdate 重放) */
    internal var appliedFocusSeq = 0

    override fun createAttr(): AMapNativeAttr = AMapNativeAttr()

    override fun createEvent(): AMapNativeEvent = AMapNativeEvent()

    override fun viewName(): String = VIEW_NAME

    /** 增量下发双方点位+头像数据(JSON 串,原生解析) */
    fun updatePoints(json: String) {
        callRenderViewMethod(METHOD_UPDATE_POINTS, json)
    }

    /** 相机回中(两点包络) */
    fun fitMarkers() {
        callRenderViewMethod(METHOD_FIT_MARKERS)
    }

    /** 回看标注(M28:地点列表点击飞过去打点;json {"lat","lng","color"}) */
    fun focusPoint(json: String) {
        callRenderViewMethod(METHOD_FOCUS_POINT, json)
    }

    companion object {
        const val VIEW_NAME = "HRAMapView"
        const val METHOD_UPDATE_POINTS = "updatePoints"
        const val METHOD_FIT_MARKERS = "fitMarkers"
        const val METHOD_FOCUS_POINT = "focusPoint"
    }
}

/** 空属性集(地图全部走 callMethod 指令通道,无声明式属性) */
class AMapNativeAttr : Attr()

/** 空事件集(M27 无地图点击交互;二期 polyline/点 marker 弹卡再扩) */
class AMapNativeEvent : Event()

/**
 * AMapNativeMap — 原生地图 Compose 包装
 * 用法:modifier 必须给显式尺寸(默认 measurePolicy 取 constraints 最大值,fillMaxSize 定全屏);
 *      pointsJson 变化 → viewUpdate 重跑 → 原生增量落点;fitTrigger 递增触发一次 fitMarkers
 *      (int 序号驱动,boolean 会粘滞——true 恒真每次 update 重 fit 拉相机);
 *      focusSeq 递增触发一次 focusPoint 回看标注(M28,同序号去重)
 */
@Composable
fun AMapNativeMap(
    pointsJson: String,
    fitTrigger: Int,
    modifier: Modifier,
    focusSeq: Int = 0,
    focusJson: String = "",
) {
    MakeKuiklyComposeNode(
        factory = { AMapNativeView() },
        modifier = modifier,
        viewInit = {
            // 无声明式属性;首帧点位由 viewUpdate 统一下发(attach 时必跑一次)
        },
        // ⚠ viewUpdate 是 (T)->Unit 参数 lambda 非 receiver lambda,视图引用必须经 it
        viewUpdate = { view ->
            view.updatePoints(pointsJson)
            if (fitTrigger > view.appliedFitSeq) {
                view.appliedFitSeq = fitTrigger
                view.fitMarkers()
            }
            if (focusSeq > view.appliedFocusSeq) {
                view.appliedFocusSeq = focusSeq
                view.focusPoint(focusJson)
            }
        },
    )
}
