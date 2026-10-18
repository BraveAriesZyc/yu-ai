package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AppOverlayCard — 全屏灰幕 + 居中白卡浮层骨架(表单/详情/选择类浮层通用底座)
 * 用法:AppOverlayCard(onDismiss = { show = false }, busy = submitting) {
 *          Text("标题..."); ...  // 白卡内容,ColumnScope
 *       }
 *      挂调用方根 Box 最后(后组合者在上);⚠ Tab 页浮层必须状态提升挂 MainPager 根级——
 *      页内挂 z 序低于浮岛底栏盖不住(铁律 14);独立 @Page 页页内挂即可
 * 依赖:AppTheme(CardBg);无动画(铁律④纯条件渲染)
 * 事件:灰幕点击 onDismiss(busy=true 拦截,防提交中误关);白卡空 clickable{} 吞点击防透传
 * 功能:
 *  - 灰幕 fillMaxSize 黑 scrimAlpha(默认 0.45)+ 白卡 CardBg RoundedCornerShape(20.dp),
 *    横向 36dp 页边内聚在本组件(调用方不再写);卡内容 padding 默认 20/22(AppConfirmOverlay/
 *    BindUnbindConfirm/Wish 详情浮层同构常数),cardPadding 可调
 *  - 命中铁律:灰幕与白卡均 background+clickable 同节点(铁律⑤);白卡空 clickable {} 吞透传
 *  - ⚠️ 新浮层一律走本壳;键盘避让变体(浮岛式,AnniversaryAddOverlay/MomentAddOverlay 那套
 *    keyboardHeightChange 顶起)待二期——现接入键盘输入前先确认避让方案
 */
@Composable
internal fun AppOverlayCard(
    onDismiss: (() -> Unit)? = null,
    busy: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    scrimAlpha: Float = 0.45f,
    cardCorner: Dp = 20.dp,
    cardPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable { if (!busy) onDismiss?.invoke() },
        contentAlignment = contentAlignment,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(cardCorner))
                .clickable { } // 吞点击防透传到遮罩(铁律⑤:background 与 clickable 同节点)
                .padding(cardPadding),
        ) {
            content()
        }
    }
}
