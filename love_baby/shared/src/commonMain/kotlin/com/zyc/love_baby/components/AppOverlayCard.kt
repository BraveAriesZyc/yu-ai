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
import com.tencent.kuikly.compose.ui.graphics.Shape
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AppOverlayCard — 全屏灰幕 + 白卡浮层骨架(表单/详情/选择/底部贴边类浮层通用底座)
 * 用法:AppOverlayCard(onDismiss = { show = false }, busy = submitting) {
 *          Text("标题..."); ...  // 白卡内容,ColumnScope
 *       }
 *      挂调用方根 Box 最后(后组合者在上);⚠ Tab 页浮层必须状态提升挂 MainPager 根级——
 *      页内挂 z 序低于浮岛底栏盖不住(铁律 14);独立 @Page 页页内挂即可
 * 依赖:AppTheme(CardBg);无动画(铁律④纯条件渲染)
 * 事件:灰幕点击 onDismiss(busy=true 拦截,防提交中误关);白卡空 clickable{} 吞点击防透传
 * 功能:
 *  - 灰幕 fillMaxSize 黑 scrimAlpha(默认 0.45)+ 白卡 CardBg(形状 cardShape,默认
 *    RoundedCornerShape(cardCorner)=20dp;底部贴边形态传 top-only 四参圆角,Kuikly 不裁屏幕
 *    边缘——单参全圆角底部缺口会透灰幕);卡外边距 cardOuterPadding 默认横向 36dp(页边内聚
 *    在本组件),键盘顶起/贴底形态传自定义 PaddingValues(bottom=…,与 contentAlignment 动态
 *    切换配合);卡内容 padding 默认 20/22,cardPadding 可调;卡内内容水平对齐
 *    contentHorizontalAlignment 默认 Start(居中卡传 CenterHorizontally)
 *  - 命中铁律:灰幕与白卡均 background+clickable 同节点(铁律⑤);白卡空 clickable {} 吞透传
 *  - ⚠️ 新浮层一律走本壳;busy 语义分叉保真(2026-10-26 七处收编定案):提交在飞需拦灰幕的
 *    传 busy(WishDetail=submitting/SexPicker=saving/FieldInput=saving);灰幕本就无闸的不传
 *    (MoodOptionManage/MineAbout/Anniversary/Moment——后两者关闭动作自带 clearFocus 前置)
 *  - 2026-10-26 收编七处:MoodOptionManageOverlay/MineAboutOverlay/WishDetailOverlay/
 *    SexPickerOverlay/FieldInputOverlay/AnniversaryAddOverlay/MomentAddOverlay(此前页面
 *    手写同构骨架而不用本件;键盘顶起形态即原「键盘避让变体待二期」随本批落地)
 */
@Composable
internal fun AppOverlayCard(
    onDismiss: (() -> Unit)? = null,
    busy: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    scrimAlpha: Float = 0.45f,
    cardOuterPadding: PaddingValues = PaddingValues(horizontal = 36.dp),
    cardCorner: Dp = 20.dp,
    cardShape: Shape = RoundedCornerShape(cardCorner),
    cardPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
    contentHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable { if (!busy) onDismiss?.invoke() },
        contentAlignment = contentAlignment,
    ) {
        Column(
            modifier = Modifier.padding(cardOuterPadding)
                .fillMaxWidth()
                .background(AppTheme.CardBg, cardShape)
                .clickable { } // 吞点击防透传到遮罩(铁律⑤:background 与 clickable 同节点)
                .padding(cardPadding),
            horizontalAlignment = contentHorizontalAlignment,
        ) {
            content()
        }
    }
}
