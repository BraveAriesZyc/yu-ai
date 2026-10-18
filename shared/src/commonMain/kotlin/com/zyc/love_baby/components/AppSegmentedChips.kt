package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppSegmentedChips — 分段选择胶囊组(等宽 N 段切换,双模式/分段过滤通用件)
 * 用法:AppSegmentedChips(listOf("我的邀请码", "输码绑定"), selectedIndex = mode,
 *      onSelect = { mode = it })
 * 依赖:ThemeStore(选中文字 brandStart)/AppTheme(CardBg 选中底/TextSecondary 未选文字)
 * 事件:onSelect(选中下标)
 * 功能:
 *  - 铁律③(单源化注释):选中白底常驻禁条件增删节点——背景 always 挂、只切颜色
 *    (选中 CardBg / 未选 Transparent),原生属性无 detach 清理,条件增删背景节点会残留
 *  - 灰底容器 RoundedCornerShape(18.dp)+padding(3.dp),chip 圆角 15/竖向 9/字号 14
 *    (源 = features/couple/BindWidgets.BindModeChips 生产视觉);auth 域 AuthModeChips 为
 *    品牌渐变上的半透明白容器变体(容器传 Color.White.copy(alpha = 0.25f)、未选文字传
 *    Color.White),选中/未选结构完全同构
 *  - 每段等宽 weight(1f);选项即文案,无 icon 形态
 */
@Composable
internal fun AppSegmentedChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerBg: Color = Color(0xFFF3F4F8),
    unselectedTextColor: Color = AppTheme.TextSecondary,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .background(containerBg, RoundedCornerShape(18.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier.weight(1f)
                    .background(
                        // 选中白底常驻、未选切透明:禁条件增删背景节点(铁律③,见上)
                        if (selected) AppTheme.CardBg else Color.Transparent,
                        RoundedCornerShape(15.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    color = if (selected) ThemeStore.current.brandStart else unselectedTextColor,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
