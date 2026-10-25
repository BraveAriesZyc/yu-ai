package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.tencent.kuikly.compose.animation.animateColorAsState
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AppSwitch — 开关组件(轨道双色 + 白圆滑块滑动,隐私/功能开关通用件,M26)
 * 用法:AppSwitch(checked = shareOn, onCheckedChange = { on = it })
 * 依赖:ThemeStore(开启态轨道 brandStart)/compose 动画
 * 事件:onCheckedChange(!checked)
 * 功能:
 *  - 轨道两态背景走 background **color 重载**常驻切值(铁律 7:同节点背景分支必须同参类型,
 *    color/brush 混用会在 Lazy 复用槽位冻结实色层;开关无渐变诉求,纯色即可)
 *  - 滑块位移 = graphicsLayer.translationX 纯变换(铁律 10③:offset 每帧原生 re-layout)+
 *    tween Compose 插帧(铁律 10①:spring 帧数多/preferNative 真机异常,禁用);轨道颜色
 *    过渡 tween 150ms(颜色动画照 NavItem 先例压帧)
 *  - clickable 与带 background 的轨道同节点(铁律 5:命中测试以背景视图为准,无背景
 *    clickable 节点点击会被背景子视图吞掉);滑块是背景子视图但无独立回调,点击透传轨道
 *  - 滑块结构照 SlidingPill 三层:外层 padding 定位(2dp 内衬)→ 中层 graphicsLayer 位移 →
 *    内层 width/height + background 画白圆(嵌套容器,避开同链 padding+background 的
 *    覆盖语义)
 */
@Composable
internal fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 轨道颜色过渡:纯色两态 tween 短插帧(与滑块位移 180ms 观感同步)
    val trackColor by animateColorAsState(
        targetValue = if (checked) ThemeStore.current.brandStart else Color(0xFFE2E4EC),
        animationSpec = tween<Color>(durationMillis = 150),
        label = "switchTrack",
    )
    // 滑块位移归一化 0→1(checked),graphicsLayer 内换算 px:tween Compose 插帧,禁 spring/preferNative
    val shift by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .width(TRACK_W.dp)
            .height(TRACK_H.dp)
            .background(trackColor, RoundedCornerShape((TRACK_H / 2f).dp))
            .clickable { onCheckedChange(!checked) },
    ) {
        // 滑块:padding 定位左上内衬,内层白圆常驻(同一节点仅改 transform,无残留风险)
        Box(modifier = Modifier.padding(start = THUMB_INSET.dp, top = THUMB_INSET.dp)) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // Dp.times(Float) 类成员调用(SlidingPill 同款,顶层 Float.times(Dp) 扩展勿用)
                        translationX = (THUMB_TRAVEL.dp * shift).toPx()
                    }
                    .width(THUMB_W.dp)
                    .height(THUMB_W.dp)
                    .background(Color.White, RoundedCornerShape((THUMB_W / 2f).dp)),
            )
        }
    }
}

// 几何常数(轨道 44x26 / 滑块 22 / 内衬 2 → 行程 44-22-2*2=18)
private const val TRACK_W = 44f
private const val TRACK_H = 26f
private const val THUMB_W = 22f
private const val THUMB_INSET = 2f
private const val THUMB_TRAVEL = 18f
