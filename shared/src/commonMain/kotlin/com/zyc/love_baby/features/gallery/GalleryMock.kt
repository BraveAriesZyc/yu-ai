package com.zyc.love_baby.features.gallery

import com.tencent.kuikly.compose.ui.graphics.Color
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppStatItem

/**
 * GalleryMenuItem — 组件列表页菜单分组 mock 项
 */
data class GalleryMenuItem(
    val icon: AppIconType,
    val tint: Color,
    val bg: Color,
    val title: String,
)

/**
 * GalleryMock — 组件列表页的 mock 数据(纯展示用,无持久化)
 * 用法:GalleryMock.menuItems / stats / tags / featureColors / segmentedOptions /
 *      segmentedOptionsOnBrand / monthLabels
 * 依赖:AppStatItem/AppIconType
 * 事件:无
 * 功能:预留结构化 mock,后续接真数据时按同结构替换即可
 */
object GalleryMock {

    /** 菜单分组 mock(AppMenuGroup/AppMenuRow 演示) */
    val menuItems = listOf(
        GalleryMenuItem(AppIconType.Star, Color(0xFFFF9A56), Color(0xFFFFF1E8), "纪念日提醒"),
        GalleryMenuItem(AppIconType.Heart, Color(0xFFFF5A8C), Color(0xFFFFEAF1), "我们的相册"),
        GalleryMenuItem(AppIconType.Service, Color(0xFF34C77B), Color(0xFFE6F8EF), "帮助与客服"),
    )

    /** 统计条 mock(AppStatBar 演示) */
    val stats = listOf(
        AppStatItem("999+", "收到拥抱"),
        AppStatItem("36", "心愿完成"),
        AppStatItem("128", "悄悄话"),
    )

    /** 标签 mock,Pair(文案, 主色)(AppTag 演示,底色取主色 12% 透明度) */
    val tags = listOf(
        "日常" to Color(0xFF6C7BFF),
        "约会" to Color(0xFFFF5A8C),
        "小确幸" to Color(0xFF34C77B),
        "想吃" to Color(0xFFFF9A56),
    )

    /** 灵感卡渐变 mock(AppFeatureCard 演示) */
    val featureColors = listOf(Color(0xFFFFB27A), Color(0xFFFF7A8C))

    /** 分段选择 mock 灰底变体(AppSegmentedChips 演示,与生产调用方 BindingPanel 双模式同文案) */
    val segmentedOptions = listOf("我的邀请码", "输码绑定")

    /** 分段选择 mock 白透底变体(AppSegmentedChips 演示,品牌渐变条上展示,与 LoginPanel 登录方式同文案) */
    val segmentedOptionsOnBrand = listOf("密码登录", "验证码登录")

    /** 月份导航 mock(AppMonthNav 演示):‹ › 在三个月标签间翻动 */
    val monthLabels = listOf("2026年8月", "2026年9月", "2026年10月")
}
