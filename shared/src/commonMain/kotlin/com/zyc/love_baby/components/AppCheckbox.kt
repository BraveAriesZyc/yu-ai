package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AppCheckbox — 圆形勾选框(选中实心勾 / 未选中空心环)
 * 用法:AppCheckbox(checked = todo.done, onCheckedChange = { ... })
 * 依赖:ThemeStore(默认填充色 brandEnd)/AppIcons
 * 事件:onCheckedChange 可空,为 null 时纯展示(点击穿透给父级 clickable)
 * 功能:
 *  - ⚠️ 点击必须挂在带 background 的节点上:Kuikly 2.29 命中测试以背景视图为准,
 *    无背景的 clickable 节点会被盖在表面的背景子视图吞掉点击(真机实测勾选失效根因)。
 *    所以此处背景与 clickable 同节点(背景常驻,选中/未选中只切颜色),空心环内孔
 *    也是背景子视图 → 内孔挂同一回调,保证环心区域可点
 *  - 环/填充分属两个原生视图(同链双 background 互相覆盖;原生 border 描边不跟随圆角)
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    fillColor: Color? = null, // null → 主题 brandEnd
    onCheckedChange: (() -> Unit)? = null,
) {
    val fill = fillColor ?: ThemeStore.current.brandEnd
    val ringCorner = (size.value / 2f).dp
    Box(
        modifier = modifier.size(size)
            .background(if (checked) fill else Color(0xFFD6DAE4), RoundedCornerShape(ringCorner))
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable { onCheckedChange.invoke() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            AppIcon(AppIconType.Check, Color.White, Modifier.size((size.value * 14f / 22f).dp))
        } else {
            // 环内孔:背景视图会吞点击,内孔也挂同一回调(嵌套 clickable 内层消费,不会双触发)
            val innerSize = (size.value * 18f / 22f).dp
            Box(
                modifier = Modifier.size(innerSize)
                    .background(AppTheme.CardBg, RoundedCornerShape((innerSize.value / 2f).dp))
                    .then(
                        if (onCheckedChange != null) {
                            Modifier.clickable { onCheckedChange.invoke() }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/**
 * AppCheckBadge — 选中角标(实心圆勾,未选中完全透明)
 * 用法:AppCheckBadge(visible = selected, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
 *       覆盖在可点击区域上时传 onClick 透传父级回调(背景视图吞点击,见 AppCheckbox 注释)
 * 依赖:ThemeStore(brandStart)/AppIcons
 * 事件:onClick 可空
 * 功能:背景常驻未选中切透明(条件增删背景节点会残留上一次的方色块——原生属性无 detach 清理)
 */
@Composable
fun AppCheckBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.size(size)
            .background(
                if (visible) ThemeStore.current.brandStart else Color.Transparent,
                RoundedCornerShape((size.value / 2f).dp),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick.invoke() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            AppIcon(AppIconType.Check, Color.White, Modifier.size((size.value * 13f / 22f).dp))
        }
    }
}
