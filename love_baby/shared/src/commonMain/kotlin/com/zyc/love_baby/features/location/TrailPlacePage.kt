package com.zyc.love_baby.features.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.AMapNativeMap
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.statusBarPaddingDp

/** trailPlacePage 路由名(openTrailPlacePage 与 @Page 注册名共用,勿散落字面量) */
internal const val TRAIL_PLACE_PAGE_NAME = "trailPlacePage"

/**
 * 打开地点回看页(M28.1 行动轨迹地点条目点击落点):坐标(GCJ-02)+ 名牌文案随开页参数
 * 传入,页内原生地图 focusPoint 落品牌圆点标注 + 相机飞 16 级
 */
internal fun openTrailPlacePage(pager: Pager, lat: Double, lng: Double, title: String, subtitle: String) {
    pager.openLovePage(
        TRAIL_PLACE_PAGE_NAME,
        JSONObject()
            .put("lat", lat)
            .put("lng", lng)
            .put("title", title)
            .put("subtitle", subtitle),
    )
}

/**
 * TrailPlacePage — 地点回看页(M28.1):行动轨迹地点条目的独立地图详情页
 * 背景:M28 原设计「半层静态轨迹图 + 点条目关半层回主地图回看」真机两处不适——静态图
 * trailPoints==null 无失败分支恒卡「加载中」;主地图回看被半层遮挡观感割裂。定案半层
 * 纯列表化,回看独立成页直达。
 * 用法:TrailSheet 地点条目点击 openTrailPlacePage 打开;左上返回钮 closePage
 * 依赖:base/AMapNativeMap(focusPoint 回看标注,Android 壳 KRAMapView 消费;其余端未注册
 *      viewName 地图静默空白,与 locationPage 同口径)+ LocationPage 同包的
 *      BackFloatingButton/toHex(本页复用)
 * 事件:返回钮 closePage;无其他交互(单点回看,不做实时/上报)
 * 功能:全屏地图 focusPoint 标注该地点(focusSeq=1,attach 首帧 viewUpdate 必跑一次即
 *      落点+飞 16 级);顶部返回钮 + 白胶囊地点名牌(主行 POI/地址/坐标,副行 省市区·时间);
 *      坐标 0,0(参数缺失/未桥接端)显缺失态不打标注
 */
@Page("trailPlacePage", supportInLocal = true)
internal class TrailPlacePager : BasePager() {

    /** 目的地与名牌文案(willInit 从开页参数定,组合体只读);lat/lng 全 0 = 参数缺失 */
    internal var lat = 0.0
    internal var lng = 0.0
    internal var title = ""
    internal var subtitle = ""

    override fun willInit() {
        super.willInit()
        // 开页参数:Number 判型取数(值可能被序列化成 Int/Double);String 才 optString
        try {
            val params = pageData.params
            lat = (params.opt("lat") as? Number)?.toDouble() ?: 0.0
            lng = (params.opt("lng") as? Number)?.toDouble() ?: 0.0
            title = params.optString("title")
            subtitle = params.optString("subtitle")
        } catch (_: Exception) {
        }
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            TrailPlacePage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun TrailPlacePage(pager: TrailPlacePager) {
    val theme = ThemeStore.current
    val hasTarget = pager.lat != 0.0 || pager.lng != 0.0
    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        if (hasTarget) {
            // 空点位表:本页唯一 marker 由 focusPoint 落(不进 updatePoints 增量通道);
            // 空表下发 = 原生无 marker,与回看标注互不干扰
            val emptyPoints = remember { JSONObject().put("points", JSONArray()).toString() }
            val focusJson = remember(pager.lat, pager.lng) {
                JSONObject()
                    .put("lat", pager.lat)
                    .put("lng", pager.lng)
                    .put("color", toHex(theme.brandStart))
                    .toString()
            }
            AMapNativeMap(
                pointsJson = emptyPoints,
                fitTrigger = 0,
                modifier = Modifier.fillMaxSize(),
                focusSeq = 1, // 常量序号:attach 首帧 viewUpdate 必跑(1 > 0),之后去重不重放
                focusJson = focusJson,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("位置信息缺失", color = AppTheme.TextTertiary, fontSize = 13.sp)
            }
        }
        // 顶部:返回钮 + 地点名牌(白胶囊;地图无页头,LocationPage 顶栏同款布局)
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = statusBarPaddingDp() + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackFloatingButton {
                pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            }
            if (pager.title.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        pager.title,
                        color = AppTheme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (pager.subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            pager.subtitle,
                            color = AppTheme.TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
