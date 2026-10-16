package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.pager.Pager

/**
 * KuiklyTheme — 一套可切换的主题(品牌渐变/页面背景)
 * 用法:ThemeStore.current.brandBrush;新增主题在 ThemeStore.all 中追加
 * 依赖:无外部资源
 * 事件:无
 * 功能:品牌渐变双色 + 页面背景色;brandBrush 为计算属性,切换主题后自动全局重组
 */
data class KuiklyTheme(
    val id: String,
    val name: String,
    val brandStart: Color,
    val brandEnd: Color,
    val bg: Color,
) {
    /**
     * 品牌渐变(懒缓存)。不能用 get() 每次新建:Compose 会因实例不等而
     * 判定 background 参数变化,引发多余 diff/重绘(底栏掉帧根因之一)
     */
    val brandBrush: Brush by lazy {
        Brush.horizontalGradient(listOf(brandStart, brandEnd))
    }
}

/**
 * AppTheme — 全局静态设计规范(中性色/间距,不随主题变化)
 * 主题相关的颜色(渐变/背景)请用 ThemeStore.current
 */
object AppTheme {
    val CardBg = Color(0xFFFFFFFF)        // 卡片背景
    val TextPrimary = Color(0xFF2B2E3F)   // 主文字
    val TextSecondary = Color(0xFF8A90A0) // 次文字
    val TextTertiary = Color(0xFFB4B9C6)  // 弱文字/占位
    val AccentOrange = Color(0xFFFF7A45)  // 强调
    val Success = Color(0xFF34C77B)       // 成功
    val Danger = Color(0xFFFF5A6E)        // 危险/角标
    val Divider = Color(0xFFF0F1F5)       // 分割线

    val PageHPadding = 16.dp
}

/**
 * ThemeStore — 全局主题状态与持久化
 * 用法:ThemeStore.current / ThemeStore.switch(theme) / ThemeStore.persist(pager)
 * 依赖:SharedPreferencesModule(Kuikly core)
 * 事件:switch 触发全局重组
 * 功能:5 套情侣风主题;选择结果通过 SharedPreferences 持久化,MainPager 启动时 restore
 */
object ThemeStore {
    val all: List<KuiklyTheme> = listOf(
        KuiklyTheme("peach", "蜜桃恋语", Color(0xFFFF8FA3), Color(0xFFFFB199), Color(0xFFFDF6F4)),
        KuiklyTheme("sunset", "落日甜心", Color(0xFFFF9A56), Color(0xFFFF6B95), Color(0xFFFFF7F2)),
        KuiklyTheme("mint", "薄荷奶绿", Color(0xFF6FCF97), Color(0xFF3EA77E), Color(0xFFF2FAF5)),
        KuiklyTheme("ocean", "海盐气泡", Color(0xFF6FB6FF), Color(0xFF5A8DEE), Color(0xFFF3F8FD)),
        KuiklyTheme("lavender", "晚风紫", Color(0xFF9B8CFF), Color(0xFFC08CFF), Color(0xFFF8F6FD)),
    )

    var current: KuiklyTheme by mutableStateOf(all.first())
        private set

    const val PREF_KEY = "app_theme_id"

    fun switch(theme: KuiklyTheme) {
        current = theme
    }

    /**
     * 主页面启动时恢复上次选择的主题
     * 注意:必须用 getModule(可空),acquireModule 在模块未就绪时会抛"异步炸弹"
     * (throwRuntimeError 内部 setTimeout(1) 再抛一次,try/catch 接不住,直接崩 APP);
     * 且 Pager 生命周期 willInit() 早于 initModule(),过早调用会拿不到模块 → 返回 null 走默认主题
     */
    fun restore(pager: Pager) {
        try {
            pager.getModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)?.let { sp ->
                all.firstOrNull { it.id == sp.getItem(PREF_KEY) }?.let { current = it }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 从启动参数恢复主题(willInit 首帧前调用):宿主把持久化的主题 id 经 pageData 传入,
     * 首帧即渲染正确主题,消除"先渲染默认主题 → didInit restore → 全局重组"的启动二次渲染。
     * pageData 在 willInit 时已就绪(不走模块,无时序问题);didInit 的 restore 仅作无参数端兜底
     */
    fun initFromPageData(pager: Pager) {
        try {
            val id = pager.pageData.params.optString(PREF_KEY)
            if (id.isNotEmpty()) {
                all.firstOrNull { it.id == id }?.let { current = it }
            }
        } catch (_: Exception) {
        }
    }

    /** 切换主题后调用,持久化本次选择 */
    fun persist(pager: Pager) {
        try {
            pager.getModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                ?.setItem(PREF_KEY, current.id)
        } catch (_: Exception) {
        }
    }
}

/**
 * statusBarPaddingDp — 顶部状态栏高度(逻辑像素),页面头部用
 */
@Composable
fun statusBarPaddingDp(): Dp {
    val activity = LocalActivity.current
    return activity.pageData.statusBarHeight.dp
}

/**
 * navBarSafePaddingDp — 底部导航栏/手势条安全距离(dp),页面底部避让用
 * 来源:原生渲染下发的 pageData.safeAreaInsets(取自 rootWindowInsets 的 stable insets,已换算 dp)。
 * Android 宿主开沉浸式(透明导航栏 + setDecorFitsSystemWindows(false))后即为手势条/导航栏高度,
 * 未开启沉浸式或其他端拿不到时为 0,无副作用
 */
@Composable
fun navBarSafePaddingDp(): Dp {
    val activity = LocalActivity.current
    return activity.pageData.safeAreaInsets.bottom.dp
}
