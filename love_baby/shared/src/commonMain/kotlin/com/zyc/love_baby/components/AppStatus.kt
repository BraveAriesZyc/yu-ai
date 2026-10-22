package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.CircularProgressIndicator
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppStatus — 列表三态通用件(加载中 / 加载失败重试 / 空态提示)
 * 用法:LazyColumn item { AppLoading() } / item { AppLoadFailed { reload() } } /
 *       item { AppEmptyHint(AppIconType.Heart, "还没有瞬间", hint = "点右下角 + 晒一张今天的照片吧") }
 * 依赖:ThemeStore(brandStart 品牌色)/AppIcons/AppTheme(TextSecondary/TextTertiary)
 * 事件:AppLoadFailed 的 onRetry(重试胶囊点击)
 * 功能:
 *  - AppLoading/AppLoadFailed 原样迁自 features/mood/WishTimelinePage 的 TimelineLoading/
 *    TimelineLoadFailed(M17 两时间线共用件公开化;原件已删,全仓调用方已直替)
 *  - AppEmptyHint 两形态:circled=true「56dp 圆底(brandStart 10%)+ 品牌色 icon + 标题 +
 *    可选副文案」(MomentPage.MomentEmptyHint / AnniversaryPage.AnniversaryEmptyHint 同构);
 *    circled=false「裸 icon + 标题」(WishListPage.WishEmptyHint 同构,icon 用 TextTertiary 灰)
 *  - 圆底 icon 形态的背景是纯色低透明圆(color 重载,非 brush)——brandStart 10% 圆是仓内
 *    空态同构视觉,不涉 color/brush 重载混用问题
 */
@Composable
internal fun AppLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = ThemeStore.current.brandStart,
            strokeWidth = 3.dp,
        )
    }
}

/** 加载失败:一句文案 + 重试胶囊(胶囊背景常驻与 clickable 同节点,铁律⑤) */
@Composable
internal fun AppLoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载失败了,网络开小差了", color = AppTheme.TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier.background(
                    ThemeStore.current.brandStart.copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp),
                )
                .clickable { onRetry() }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                "重试",
                color = ThemeStore.current.brandStart,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 空态提示:icon + 标题(+ 可选副文案)
 * @param circled true=圆底 icon 形态(默认);false=裸 icon 形态
 * @param containerColor 圆底色(circled=true 生效;默认 brandStart 10% 透明)
 * @param iconSize icon 边长(circled=true 时落在 56dp 圆内;false 时即裸 icon 尺寸,
 *        裸形态惯例给大号如 44.dp——WishEmptyHint 同款)
 */
@Composable
internal fun AppEmptyHint(
    icon: AppIconType,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    circled: Boolean = true,
    containerColor: Color = ThemeStore.current.brandStart.copy(alpha = 0.10f),
    iconSize: Dp = 24.dp,
) {
    val theme = ThemeStore.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (circled) {
            Box(
                modifier = Modifier.size(56.dp)
                    .background(containerColor, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(icon, theme.brandStart, Modifier.size(iconSize))
            }
        } else {
            AppIcon(icon, AppTheme.TextTertiary, Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            title,
            color = AppTheme.TextSecondary,
            // circled 形态 14sp / 裸 icon 形态 13sp(与两处源实现逐字一致)
            fontSize = if (circled) 14.sp else 13.sp,
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(hint, color = AppTheme.TextTertiary, fontSize = 11.sp)
        }
    }
}
