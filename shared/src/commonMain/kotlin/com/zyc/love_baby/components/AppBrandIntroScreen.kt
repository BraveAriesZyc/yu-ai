package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppBrandIntroScreen — 品牌渐变引导屏骨架(登录门/绑定引导共用整屏底座)
 * 用法:AppBrandIntroScreen("宝贝 & 我", "登录后开启你们的小世界") {
 *          AuthCard { ... }   // 引导卡内容(hero 之下)
 *       }
 * 依赖:ThemeStore.current.brandBrush(懒缓存渐变,铁律⑧)/statusBarPaddingDp/
 *      navBarSafePaddingDp/AppIcons;无动画(纯条件渲染,铁律④)
 * 事件:无(整屏空 clickable{} 吞点击防透传——background 与 clickable 同节点,铁律⑤;
 *      交互由 content 内部控件自理)
 * 功能:
 *  - hero 头(单源化,源 = GatePanel.GateHeader 与 BindingPanel.BindHeader 逐字同构):
 *    64dp 白 30% 圆 + Heart 白 30dp + 26sp 白 Bold 标题 + 13sp 白 85% 副标题
 *  - 骨架:状态栏顶留白(statusBar+40dp,两处源同值)+ hero + 26dp + content + 底部
 *    24dp+navBarSafePaddingDp 避让(铁律⑨);GatePanel/BindingPanel 本体后续波次接入,
 *    既有页面零改动
 */
@Composable
internal fun AppBrandIntroScreen(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(ThemeStore.current.brandBrush)
            .clickable { },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(statusBarPaddingDp() + 40.dp))
            // hero 头(GateHeader/BindHeader 同款画法:半透明白底圆 + 心形白 icon)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppTheme.PageHPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(64.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconType.Heart, Color.White, Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            content()
            Spacer(modifier = Modifier.height(24.dp + navBarSafePaddingDp()))
        }
    }
}
