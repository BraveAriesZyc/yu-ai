package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppMenuGroup — 设置/功能菜单分组容器(白底 20dp 圆角卡)
 * 用法:AppMenuGroup { AppMenuRow(...); AppMenuDivider(); AppMenuRow(...) }
 * 依赖:AppTheme(CardBg)
 * 事件:无(行点击在各 AppMenuRow 上)
 * 功能:调用方自行套 horizontal = AppTheme.PageHPadding 的外边距
 */
@Composable
fun AppMenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp)),
    ) {
        content()
    }
}

/**
 * AppMenuRow — 菜单行(圆角色块图标 + 标题 + 尾部插槽,默认右侧箭头)
 * 用法:AppMenuRow(AppIconType.Star, tint, bg, "纪念日提醒") { ... }
 * 依赖:AppIcons/AppTheme
 * 事件:onClick(默认空实现);trailing 可自定义(默认 ChevronRight 箭头)
 * 功能:图标底座 32dp 圆角 10,图标 17dp;标题 14sp 占余宽
 */
@Composable
fun AppMenuRow(
    icon: AppIconType,
    iconTint: Color,
    iconBg: Color,
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 14.sp,
    onClick: () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {
        AppIcon(AppIconType.ChevronRight, Color(0xFFC8CCD8), Modifier.size(15.dp))
    },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp)
                .background(iconBg, RoundedCornerShape(10.dp))
                // 图标底座是背景视图会吞点击,挂同一回调(见 AppCheckbox 注释)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, iconTint, Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = AppTheme.TextPrimary, fontSize = titleSize, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * AppMenuDivider — 菜单行分割线(左侧缩进 60dp 对齐标题)
 * 用法:AppMenuRow(...) 之后调用
 * 依赖:AppTheme(Divider)
 * 事件:无
 * 功能:与 AppMenuGroup 搭配使用
 */
@Composable
fun AppMenuDivider() {
    Box(
        modifier = Modifier
            .padding(start = 60.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.Divider),
    )
}
