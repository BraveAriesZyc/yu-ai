package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppFilterChip / AppFilterChipRow — 横向滚动筛选胶囊(分类 chips 通用件,M17 抽离)
 * 用法:AppFilterChipRow(labels = listOf("全部") + categories, selectedIndex = selected,
 *      onSelect = { i -> selected = i }, modifier = Modifier.padding(horizontal = 16.dp))
 * 依赖:ThemeStore(选中色 brandStart);AppTheme(未选底色用 0xFFF5F6FA 硬编码——与 MoodOptionChip/
 *      BindInputField 输入行同源的「浅灰底」视觉令牌,AppTheme 无对应令牌故不引)
 * 事件:onSelect(下标);AppFilterChip 单枚可独立用 onClick
 * 功能:
 *  - 分类筛选行通用形态(灵感页分类/MoodPage 心情档/chips 需求同源):首枚惯例「全部」由调用方
 *    放进 labels,本组件不感知语义;选中 brandStart 实底白字 Bold,未选浅灰底灰字(照
 *    MoodPage.MoodOptionChip 生产视觉,M17 灵感页复用后抽离)
 *  - LazyRow spacedBy(8.dp) 横向滚动,超宽不换行;单枚 chip 文字自适应宽
 */
@Composable
fun AppFilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels.size) { i ->
            AppFilterChip(labels[i], selected = i == selectedIndex) { onSelect(i) }
        }
    }
}

/** 单枚筛选胶囊:选中 brandStart 实底白字 Bold / 未选浅灰底灰字(MoodOptionChip 同源视觉) */
@Composable
fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) Color.White else AppTheme.TextSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                if (selected) ThemeStore.current.brandStart else Color(0xFFF5F6FA),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
