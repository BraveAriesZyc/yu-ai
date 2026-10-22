package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.BackHandler
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppConfirmOverlay — 通用危险操作二次确认浮层(M18 自 WishListPage.DeleteWishOverlay 抽离
 * 通用化,LogoutConfirmOverlay 同款结构):全屏灰幕(45% 黑)+ 居中白卡(20dp 圆角),标题 +
 * 后果说明 + 取消/确认双钮(确认钮 Danger 红)。
 * 用法:AppConfirmOverlay(title, message, onConfirm, onDismiss, confirmText=…, busy=…),
 *      挂调用方根 Box 最后(后组合者在上);⚠ Tab 页(待办/心情/聊天)必须状态提升挂 MainPager
 *      根级——页内挂 z 序低于浮岛底栏盖不住(铁律 14);独立 @Page 页页内挂即可(WishListPage 先例)
 * 依赖:AppTheme(CardBg/TextPrimary/TextSecondary/Danger);无动画(铁律④纯条件渲染)
 * 事件:遮罩/「取消」onDismiss;「确认」onConfirm;busy=true 在飞:确认钮显 busyText 并拦截
 *      全部出口(遮罩/取消/确认),防重复提交与提交中误关
 * 功能:危险操作(删除/解绑类)统一确认语言;文案由调用方给(应说明后果与影响范围,如
 *      「双方都看不到」);moment/wish/todo/anniv 四域删除已接入或待接入
 */
@Composable
fun AppConfirmOverlay(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "删除",
    busy: Boolean = false,
    busyText: String = "删除中…",
) {
    // 返回键关闭浮层(2026-10-22 浮层返回键接线):busy(提交在飞)不关,与灰幕点击同闸;
    // 组合期注册/离场注销,调用方按状态条件组合时天然只在浮层开着时拦截返回
    BackHandler { if (!busy) onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!busy) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { } // 吞点击防透传到遮罩(铁律⑤:background 与 clickable 同节点)
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(title, color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = AppTheme.TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Box(
                    modifier = Modifier.weight(1f)
                        .height(42.dp)
                        .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                        .clickable { if (!busy) onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f)
                        .height(42.dp)
                        .background(AppTheme.Danger, RoundedCornerShape(21.dp))
                        .clickable { if (!busy) onConfirm() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (busy) busyText else confirmText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
