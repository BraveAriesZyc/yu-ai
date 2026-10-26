@file:OptIn(com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi::class)

package com.zyc.love_baby.features.chat

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.combinedClickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.KuiklyTheme
import com.zyc.love_baby.state.ChatMsg
import com.zyc.love_baby.state.ChatStickers
import com.zyc.love_baby.state.MSG_SEND_FAILED
import com.zyc.love_baby.state.MSG_SEND_SENDING
import com.zyc.love_baby.state.MSG_SEND_SENT
import com.zyc.love_baby.state.appIconFromName

/**
 * ChatBubbles — 悄悄话富媒体气泡组件单源(M23):语音/视频/贴纸/心愿墙卡片四类正式气泡
 * (M24 修订:待发视频预览条 ChatVideoPreviewBar 随预览确认流程删除——媒体直入消息列表乐观发送)。
 * 自 ChatPage 拆出独立文件(页已近 1300 行,气泡组件域内单源避免继续膨胀,计划 S5 裁决)。
 * 铁律对齐:
 *  - 气泡背景两分支一律 background(brush=) 同参类型(铁律7,taBubbleBrush 注释);
 *  - 可长按撤回的气泡用 combinedClickable 长重载(interactionSource=null+indication=null,
 *    不给无手势气泡凭空加按压视觉,TextBubble 同规);onLongClick 为 null 时退短重载或无手势;
 *  - 贴纸无气泡背景,透明底与手势同节点常驻(铁律⑤:无背景 clickable 被表面子视图吞点击);
 *  - 视频定边画幅保 LazyColumn item 高度稳定(ImageBubble 同思路)。
 * 长按撤回:onLongClick 由调用方传入(canRecall 窗口外传 null),null 时不挂任何长按手势,
 * 槽位复用安全(M12 同规)。
 */

/**
 * TA 气泡底(自 ChatPage 迁入单源,语音气泡同用):同色渐变(视觉=纯白,AppAvatar 同款先例)。
 * ⚠ 必须与我方 brandBrush 同走 background(brush=) 重载——Kuikly 的 background(color=) 与
 * background(brush=) 写两个不同原生 prop/图层且层序首次创建后冻结:同一气泡视图若先后走过
 * 两重载(Lazy 槽位跨 mine/TA 复用),白实色层会冻结在渐变层上方(2026-10-16 Q2 真机事故)。
 * 纯色一律同色渐变归一,仅参数值切换、重载类型不得切换(铁律⑦)
 */
internal val taBubbleBrush = Brush.linearGradient(listOf(AppTheme.CardBg, AppTheme.CardBg))

/** 卡片气泡底(M23):CardBg 同色渐变归一(同 taBubbleBrush 理由;静态值防重载混用) */
private val cardBubbleBrush = Brush.linearGradient(listOf(AppTheme.CardBg, AppTheme.CardBg))

/** 视频占位底(M23):浅灰同色渐变(与 ImageBubble 同为 AppTheme.InputBg,brush 归一) */
private val VIDEO_PLACEHOLDER_BRUSH =
    Brush.linearGradient(listOf(AppTheme.InputBg, AppTheme.InputBg))

/** 视频气泡定边(M23):180×120 横向画幅,高度恒定 → item 高度不随封面加载变化不跳 */
private val VIDEO_BUBBLE_WIDTH = 180.dp
private val VIDEO_BUBBLE_HEIGHT = 120.dp

/**
 * VoiceBubble — 语音消息气泡(M23):宽度 100dp 基线 + 每秒 2dp 系数(60s 顶格,与文本气泡
 * 260 封顶同量级);播放/暂停图标切换 + 三根静态波形条(不跑逐帧动画,铁律 10①,播放态靠
 * 图标表达)+ "12″" 时长;mine=brandBrush / TA=taBubbleBrush 同参 brush(铁律7)。
 * 播放态由调用方 playingVoiceId 页内态驱动(自然播完/被顶掉原生不回调,按消息时长定时收口)。
 */
@Composable
internal fun VoiceBubble(
    msg: ChatMsg,
    mine: Boolean,
    theme: KuiklyTheme,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val bubbleWidth = (100 + msg.durationSec * 2).coerceIn(100, 240)
    val iconTint = if (mine) Color.White else AppTheme.TextSecondary
    val barColor = if (mine) Color.White.copy(alpha = 0.85f) else theme.brandStart
    val durColor = if (mine) Color.White else AppTheme.TextPrimary
    Box(
        modifier = Modifier
            .width(bubbleWidth.dp)
            .background(brush = if (mine) theme.brandBrush else taBubbleBrush, shape = RoundedCornerShape(18.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(if (playing) AppIconType.Pause else AppIconType.Play, iconTint, Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(width = 3.dp, height = 8.dp).background(barColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(3.dp))
            Box(modifier = Modifier.size(width = 3.dp, height = 14.dp).background(barColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(3.dp))
            Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).background(barColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(9.dp))
            Text("${msg.durationSec}″", color = durColor, fontSize = 12.sp)
        }
    }
}

/**
 * ChatMediaStateOverlay — 媒体气泡发送状态蒙层(M24 修订美化,微信语义):盖满媒体画幅圆角,
 * SENDING=黑 28% + **进度环恒在场**(原生已报百分比→白描边进度环+百分数,Canvas 状态驱动重绘
 * 无逐帧动画;压缩/抽帧/排队等未上报窗口→空环+「上传中…」。M24 八轮:旧实现该窗口走白钟表
 * 分支,1080 压缩下小图整链亚秒完成,钟表一闪而过读不出形态、连续两轮被误读「进度条闪一下」,
 * 环恒在场后图/视频观感完全一致)/ FAILED=黑 50% + 红圆白「!」(点击落气泡宿主手势 = 原位
 * 重发,蒙层自身无手势不拦)。SENT 不渲染整层(Lazy 槽位复用零残留);蒙层 background 恒
 * color 同参重载仅切透明度(铁律7),圆角 16dp 与 Image/Video 画幅同形。
 */
@Composable
internal fun ChatMediaStateOverlay(sendState: Int, progress: Int = -1) {
    if (sendState == MSG_SEND_SENT) return
    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                Color.Black.copy(alpha = if (sendState == MSG_SEND_SENDING) 0.28f else 0.50f),
                RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (sendState == MSG_SEND_SENDING) {
            // 微信式进度环:底环 30% 白 + 已传弧长实白,弧度=进度比(200ms 轮询写状态驱动)。
            // M24 七轮(真机「圈圈四个角被裁」):drawArc 缺省 topLeft/size 取画布全域,弧
            // 贴满 34dp 边界,3dp 描边向外溢 1.5dp 被画布裁切(圈圈四边削平)。改显式内缩:
            // 外层 Box 定 36dp 尺寸兜底,弧矩形 = 画布 - 2×(描边半径+1dp 余量),环恒在
            // 画布内且StrokeCap.Round 圆头完整。
            // M24 八轮(真机「选完照片底部还是有进度条一闪而过」):环恒在场——压缩/排队窗口
            // (progress<0)也画底环(空环+「上传中…」),旧钟表分支删除:1080 压缩下小图整链
            // 亚秒完成,钟表+小字一闪而过读不出形态(用户连续两轮误读「进度条」);环恒在场后
            // 图/视频观感完全一致(用户裁决「和视频一样的中间是个圈圈」),弧仅在真实字节进度时叠加
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 3.dp.toPx()
                        val ring = Stroke(width = strokePx, cap = StrokeCap.Round)
                        val inset = strokePx / 2f + 1.dp.toPx()
                        val arcSize = Size(
                            (size.width - inset * 2f).coerceAtLeast(0f),
                            (size.height - inset * 2f).coerceAtLeast(0f),
                        )
                        drawArc(
                            Color.White.copy(alpha = 0.30f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = ring,
                        )
                        if (progress >= 0) {
                            drawArc(
                                Color.White,
                                startAngle = -90f,
                                sweepAngle = 360f * (progress.coerceIn(0, 100) / 100f),
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = ring,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (progress >= 0) "${progress.coerceIn(0, 100)}%" else "上传中…",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        } else {
            Box(
                modifier = Modifier.size(26.dp)
                    .background(AppTheme.Danger, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * VideoBubble — 视频消息气泡(M23;M24 修订美化):180×120 定边画幅,封面 AsyncImage Crop 叠
 * 浅灰占位底(coverUrl 空降级灰底,不再叠占位 Play 图标——中央黑 30% 圆播放角标是唯一播放
 * 具象,修双三角叠加);右下时长 chip。M24 修订:乐观发送/失败状态走 ChatMediaStateOverlay
 * (发送中隐藏播放角标避让蒙层时钟;失败蒙层红!即重发示能)。不做 mine/TA 背景分化(占位底
 * 恒同参 brush 单值,ImageBubble 同思路);点击外放/失败重发(调用方收口),长按撤回同
 * TextBubble 规。
 */
@Composable
internal fun VideoBubble(
    msg: ChatMsg,
    progress: Int = -1, // M24 三轮修订:SENDING 蒙层进度环读数(-1=未知/非上传窗口)
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .background(brush = VIDEO_PLACEHOLDER_BRUSH, shape = RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .size(VIDEO_BUBBLE_WIDTH, VIDEO_BUBBLE_HEIGHT),
    ) {
        if (msg.coverUrl.isNotEmpty()) {
            Image(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                painter = rememberAsyncImagePainter(msg.coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
        ChatMediaStateOverlay(msg.sendState, progress)
        if (msg.sendState == MSG_SEND_SENT) {
            // 播放角标仅常态渲染:发送中蒙层已有时钟示能、失败蒙层红!即重发示能,不叠双份
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.30f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconType.Play, Color.White, Modifier.size(18.dp))
            }
        }
        if (msg.durationSec > 0) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text("${msg.durationSec}″", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

/**
 * StickerBubble — 贴纸消息气泡(M23):字典 emoji 直渲(append-only 字典,未知 key 占位 🤍),
 * 无气泡背景(微信大表情语言);透明底与手势同节点常驻(铁律⑤),长按撤回仅可撤回时挂。
 */
@Composable
internal fun StickerBubble(msg: ChatMsg, onLongClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .background(Color.Transparent)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
    ) {
        // 48sp 大表情(快照式 key→emoji 映射两端一致即可;60sp 顶满行高压缩组距,取微信观感折中)
        Text(ChatStickers.emojiOf(msg.stickerKey), fontSize = 48.sp)
    }
}

/**
 * CardBubble — 自定义卡片消息气泡(M23,首个实例心愿墙):236dp 横卡 = 图标座(brandStart 10%
 * 洗底)+ 标题/副题 + ChevronRight;CardBg 同色渐变底(铁律7 归一);快照字段还原
 * (icon 未知/缺省兜底 Heart,渲染不空转,WishBubble 同思路),点击跳卡型对应页
 * (wishWall → 恋爱清单,由调用方分派)。
 */
@Composable
internal fun CardBubble(
    msg: ChatMsg,
    theme: KuiklyTheme,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.width(236.dp)
            .background(brush = cardBubbleBrush, shape = RoundedCornerShape(16.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .background(theme.brandStart.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(appIconFromName(msg.cardIcon), theme.brandStart, Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                msg.cardTitle.ifEmpty { "卡片消息" },
                color = AppTheme.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (msg.cardSub.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(msg.cardSub, color = AppTheme.TextTertiary, fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        AppIcon(AppIconType.ChevronRight, AppTheme.TextTertiary, Modifier.size(14.dp))
    }
}
