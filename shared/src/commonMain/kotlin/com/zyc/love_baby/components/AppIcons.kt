package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.StrokeJoin
import com.tencent.kuikly.compose.ui.graphics.drawscope.DrawScope
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AppIcons — 自绘矢量图标库(24x24 视口,圆头线性风格)
 * 用法:AppIcon(AppIconType.Home, tint = Color.White, modifier = Modifier.size(24.dp))
 * 依赖:纯 Path/Canvas 绘制,无外部资源;跨端一致
 * 事件:无;AppIconType 新增图标时在 drawAppIcon 的 when 中补分支
 */
enum class AppIconType {
    Home, Todo, Shop, Mine,          // 底部导航
    ChevronRight, ChevronLeft, Plus, Search, // 通用
    Close, Check, Chat, Send,        // 通用
    Image,                           // 通用(M10 聊天发图入口)
    Pin, Wallet, Service, Info,      // 我的页
    Components,                      // 组件列表入口
    Flag,                            // 我的页(M14 恋爱清单入口)
    Star, Heart, Bell, Moon, Sun,    // 业务装饰
}

/**
 * AppIcon — 图标组件
 * @param type 图标类型
 * @param tint 着色
 * @param filled 部分图标支持实心模式(Star/Heart)
 */
@Composable
fun AppIcon(
    type: AppIconType,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Canvas(modifier = modifier) {
        drawAppIcon(type, tint, filled)
    }
}

/**
 * drawAppIcon — 在当前 DrawScope 内绘制图标(自动以短边归一化到 24 视口)
 */
fun DrawScope.drawAppIcon(type: AppIconType, color: Color, filled: Boolean = false) {
    val u = size.minDimension / 24f
    fun f(v: Float) = v * u
    fun off(x: Float, y: Float) = Offset(f(x), f(y))
    val strokeStyle = Stroke(width = f(2f), cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (type) {
        AppIconType.Home -> {
            // 屋顶
            val roof = Path().apply {
                moveTo(f(4f), f(11f))
                lineTo(f(12f), f(4f))
                lineTo(f(20f), f(11f))
            }
            drawPath(roof, color, style = strokeStyle)
            // 屋身 + 门
            val body = Path().apply {
                moveTo(f(6f), f(9.5f))
                lineTo(f(6f), f(19f))
                lineTo(f(18f), f(19f))
                lineTo(f(18f), f(9.5f))
            }
            drawPath(body, color, style = strokeStyle)
            val door = Path().apply {
                moveTo(f(10f), f(19f))
                lineTo(f(10f), f(14f))
                lineTo(f(14f), f(14f))
                lineTo(f(14f), f(19f))
            }
            drawPath(door, color, style = strokeStyle)
        }

        AppIconType.Todo -> {
            // 清单板
            val board = Path().apply {
                moveTo(f(7f), f(4f))
                lineTo(f(17f), f(4f))
                quadraticBezierTo(f(20f), f(4f), f(20f), f(7f))
                lineTo(f(20f), f(17f))
                quadraticBezierTo(f(20f), f(20f), f(17f), f(20f))
                lineTo(f(7f), f(20f))
                quadraticBezierTo(f(4f), f(20f), f(4f), f(17f))
                lineTo(f(4f), f(7f))
                quadraticBezierTo(f(4f), f(4f), f(7f), f(4f))
                close()
            }
            drawPath(board, color, style = strokeStyle)
            // 勾
            val check = Path().apply {
                moveTo(f(8.5f), f(12.5f))
                lineTo(f(11f), f(15f))
                lineTo(f(15.5f), f(9.5f))
            }
            drawPath(check, color, style = strokeStyle)
        }

        AppIconType.Shop -> {
            // 袋身
            val body = Path().apply {
                moveTo(f(5.5f), f(8f))
                lineTo(f(18.5f), f(8f))
                lineTo(f(18.5f), f(18f))
                quadraticBezierTo(f(18.5f), f(20.5f), f(16f), f(20.5f))
                lineTo(f(8f), f(20.5f))
                quadraticBezierTo(f(5.5f), f(20.5f), f(5.5f), f(18f))
                close()
            }
            drawPath(body, color, style = strokeStyle)
            // 提手
            val handle = Path().apply {
                moveTo(f(9f), f(8f))
                lineTo(f(9f), f(6.5f))
                quadraticBezierTo(f(12f), f(3.2f), f(15f), f(6.5f))
                lineTo(f(15f), f(8f))
            }
            drawPath(handle, color, style = strokeStyle)
        }

        AppIconType.Mine -> {
            // 头
            drawCircle(color, radius = f(3.6f), center = off(12f, 8.2f), style = strokeStyle)
            // 肩
            val shoulder = Path().apply {
                moveTo(f(5f), f(19.5f))
                cubicTo(f(5f), f(15.2f), f(8f), f(13.5f), f(12f), f(13.5f))
                cubicTo(f(16f), f(13.5f), f(19f), f(15.2f), f(19f), f(19.5f))
            }
            drawPath(shoulder, color, style = strokeStyle)
        }

        AppIconType.ChevronRight -> {
            val p = Path().apply {
                moveTo(f(9.5f), f(5.5f))
                lineTo(f(16f), f(12f))
                lineTo(f(9.5f), f(18.5f))
            }
            drawPath(p, color, style = strokeStyle)
        }

        AppIconType.ChevronLeft -> {
            val p = Path().apply {
                moveTo(f(14.5f), f(5.5f))
                lineTo(f(8f), f(12f))
                lineTo(f(14.5f), f(18.5f))
            }
            drawPath(p, color, style = strokeStyle)
        }

        AppIconType.Components -> {
            // 四宫格:四个圆角小方块(2x2)
            fun tile(x: Float, y: Float) {
                val s = 6.4f
                val r = 1.8f
                val sq = Path().apply {
                    moveTo(f(x + r), f(y))
                    lineTo(f(x + s - r), f(y))
                    quadraticBezierTo(f(x + s), f(y), f(x + s), f(y + r))
                    lineTo(f(x + s), f(y + s - r))
                    quadraticBezierTo(f(x + s), f(y + s), f(x + s - r), f(y + s))
                    lineTo(f(x + r), f(y + s))
                    quadraticBezierTo(f(x), f(y + s), f(x), f(y + s - r))
                    lineTo(f(x), f(y + r))
                    quadraticBezierTo(f(x), f(y), f(x + r), f(y))
                    close()
                }
                drawPath(sq, color, style = strokeStyle)
            }
            tile(4.2f, 4.2f)
            tile(13.4f, 4.2f)
            tile(4.2f, 13.4f)
            tile(13.4f, 13.4f)
        }

        AppIconType.Plus -> {
            drawLine(color, off(12f, 5f), off(12f, 19f), strokeWidth = f(2f), cap = StrokeCap.Round)
            drawLine(color, off(5f, 12f), off(19f, 12f), strokeWidth = f(2f), cap = StrokeCap.Round)
        }

        AppIconType.Search -> {
            drawCircle(color, radius = f(6f), center = off(11f, 11f), style = strokeStyle)
            val handle = Path().apply {
                moveTo(f(15.5f), f(15.5f))
                lineTo(f(20f), f(20f))
            }
            drawPath(handle, color, style = strokeStyle)
        }

        AppIconType.Close -> {
            drawLine(color, off(6.5f, 6.5f), off(17.5f, 17.5f), strokeWidth = f(2f), cap = StrokeCap.Round)
            drawLine(color, off(17.5f, 6.5f), off(6.5f, 17.5f), strokeWidth = f(2f), cap = StrokeCap.Round)
        }

        AppIconType.Check -> {
            val p = Path().apply {
                moveTo(f(5f), f(12.5f))
                lineTo(f(10f), f(17.5f))
                lineTo(f(19f), f(7f))
            }
            drawPath(p, color, style = strokeStyle)
        }

        AppIconType.Pin -> {
            val body = Path().apply {
                moveTo(f(12f), f(21f))
                cubicTo(f(8f), f(16.5f), f(5f), f(13f), f(5f), f(9.8f))
                quadraticBezierTo(f(5f), f(3.5f), f(12f), f(3.5f))
                quadraticBezierTo(f(19f), f(3.5f), f(19f), f(9.8f))
                cubicTo(f(19f), f(13f), f(16f), f(16.5f), f(12f), f(21f))
                close()
            }
            drawPath(body, color, style = strokeStyle)
            drawCircle(color, radius = f(2.4f), center = off(12f, 9.8f), style = strokeStyle)
        }

        AppIconType.Wallet -> {
            val body = Path().apply {
                moveTo(f(4f), f(7f))
                quadraticBezierTo(f(4f), f(4.5f), f(6.5f), f(4.5f))
                lineTo(f(17.5f), f(4.5f))
                quadraticBezierTo(f(20f), f(4.5f), f(20f), f(7f))
                lineTo(f(20f), f(17f))
                quadraticBezierTo(f(20f), f(19.5f), f(17.5f), f(19.5f))
                lineTo(f(6.5f), f(19.5f))
                quadraticBezierTo(f(4f), f(19.5f), f(4f), f(17f))
                close()
            }
            drawPath(body, color, style = strokeStyle)
            drawLine(color, off(4f, 9.5f), off(20f, 9.5f), strokeWidth = f(2f), cap = StrokeCap.Round)
            drawCircle(color, radius = f(1.3f), center = off(15.8f, 14.5f))
        }

        AppIconType.Service -> {
            // 头戴两侧
            val arc = Path().apply {
                moveTo(f(4.5f), f(13f))
                quadraticBezierTo(f(4.5f), f(5.5f), f(12f), f(5.5f))
                quadraticBezierTo(f(19.5f), f(5.5f), f(19.5f), f(13f))
            }
            drawPath(arc, color, style = strokeStyle)
            drawCircle(color, radius = f(2.2f), center = off(4.5f, 13f), style = strokeStyle)
            drawCircle(color, radius = f(2.2f), center = off(19.5f, 13f), style = strokeStyle)
            // 话筒
            val mic = Path().apply {
                moveTo(f(19.5f), f(15.2f))
                quadraticBezierTo(f(19.5f), f(19.5f), f(14f), f(19.8f))
            }
            drawPath(mic, color, style = strokeStyle)
        }

        AppIconType.Info -> {
            drawCircle(color, radius = f(8.5f), center = off(12f, 12f), style = strokeStyle)
            drawCircle(color, radius = f(1.1f), center = off(12f, 7.6f))
            drawLine(color, off(12f, 11f), off(12f, 16.5f), strokeWidth = f(2f), cap = StrokeCap.Round)
        }

        AppIconType.Star -> {
            val p = Path().apply {
                moveTo(f(12f), f(3.6f))
                lineTo(f(14.6f), f(9f))
                lineTo(f(20.5f), f(9.8f))
                lineTo(f(16.2f), f(13.9f))
                lineTo(f(17.3f), f(19.8f))
                lineTo(f(12f), f(17f))
                lineTo(f(6.7f), f(19.8f))
                lineTo(f(7.8f), f(13.9f))
                lineTo(f(3.5f), f(9.8f))
                lineTo(f(9.4f), f(9f))
                close()
            }
            if (filled) {
                drawPath(p, color)
            } else {
                drawPath(p, color, style = strokeStyle)
            }
        }

        AppIconType.Heart -> {
            val p = Path().apply {
                moveTo(f(12f), f(20f))
                cubicTo(f(4f), f(14.5f), f(3.5f), f(9.5f), f(6.5f), f(6.5f))
                quadraticBezierTo(f(10f), f(3.5f), f(12f), f(7.5f))
                quadraticBezierTo(f(14f), f(3.5f), f(17.5f), f(6.5f))
                cubicTo(f(20.5f), f(9.5f), f(20f), f(14.5f), f(12f), f(20f))
                close()
            }
            if (filled) {
                drawPath(p, color)
            } else {
                drawPath(p, color, style = strokeStyle)
            }
        }

        AppIconType.Bell -> {
            val body = Path().apply {
                moveTo(f(5.5f), f(16.5f))
                cubicTo(f(7f), f(15f), f(7f), f(13f), f(7f), f(10.5f))
                quadraticBezierTo(f(7f), f(4.5f), f(12f), f(4.5f))
                quadraticBezierTo(f(17f), f(4.5f), f(17f), f(10.5f))
                cubicTo(f(17f), f(13f), f(17f), f(15f), f(18.5f), f(16.5f))
                quadraticBezierTo(f(19f), f(17.5f), f(18f), f(17.5f))
                lineTo(f(6f), f(17.5f))
                quadraticBezierTo(f(5f), f(17.5f), f(5.5f), f(16.5f))
                close()
            }
            drawPath(body, color, style = strokeStyle)
            val clapper = Path().apply {
                moveTo(f(10.2f), f(20.5f))
                quadraticBezierTo(f(12f), f(22f), f(13.8f), f(20.5f))
            }
            drawPath(clapper, color, style = strokeStyle)
        }

        AppIconType.Moon -> {
            val moon = Path().apply {
                moveTo(f(16f), f(3.8f))
                cubicTo(f(9.8f), f(4.6f), f(7f), f(8.2f), f(7f), f(12f))
                cubicTo(f(7f), f(15.8f), f(9.8f), f(19.4f), f(16f), f(20.2f))
                cubicTo(f(11.4f), f(18.4f), f(11.6f), f(5.6f), f(16f), f(3.8f))
                close()
            }
            drawPath(moon, color, style = strokeStyle)
        }

        AppIconType.Sun -> {
            drawCircle(color, radius = f(3.8f), center = off(12f, 12f), style = strokeStyle)
            for (i in 0 until 8) {
                val angle = kotlin.math.PI / 4 * i
                val cosA = kotlin.math.cos(angle).toFloat()
                val sinA = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color,
                    off(12f + 5.8f * cosA, 12f + 5.8f * sinA),
                    off(12f + 7.8f * cosA, 12f + 7.8f * sinA),
                    strokeWidth = f(2f),
                    cap = StrokeCap.Round,
                )
            }
        }

        AppIconType.Chat -> {
            val bubble = Path().apply {
                moveTo(f(5.5f), f(4.5f))
                lineTo(f(18.5f), f(4.5f))
                quadraticBezierTo(f(20.5f), f(4.5f), f(20.5f), f(6.5f))
                lineTo(f(20.5f), f(13.5f))
                quadraticBezierTo(f(20.5f), f(15.5f), f(18.5f), f(15.5f))
                lineTo(f(10f), f(15.5f))
                lineTo(f(6.5f), f(19f))
                lineTo(f(6.5f), f(15.5f))
                lineTo(f(5.5f), f(15.5f))
                quadraticBezierTo(f(3.5f), f(15.5f), f(3.5f), f(13.5f))
                lineTo(f(3.5f), f(6.5f))
                quadraticBezierTo(f(3.5f), f(4.5f), f(5.5f), f(4.5f))
                close()
            }
            drawPath(bubble, color, style = strokeStyle)
            drawCircle(color, radius = f(1.15f), center = off(8.2f, 10f))
            drawCircle(color, radius = f(1.15f), center = off(12f, 10f))
            drawCircle(color, radius = f(1.15f), center = off(15.8f, 10f))
        }

        AppIconType.Send -> {
            val plane = Path().apply {
                moveTo(f(21f), f(3.5f))
                lineTo(f(3.2f), f(10.4f))
                lineTo(f(10.2f), f(13.4f))
                lineTo(f(13.2f), f(20.4f))
                close()
            }
            drawPath(plane, color)
        }

        AppIconType.Image -> {
            // 圆角相框
            val frame = Path().apply {
                moveTo(f(7f), f(4f))
                lineTo(f(17f), f(4f))
                quadraticBezierTo(f(20f), f(4f), f(20f), f(7f))
                lineTo(f(20f), f(17f))
                quadraticBezierTo(f(20f), f(20f), f(17f), f(20f))
                lineTo(f(7f), f(20f))
                quadraticBezierTo(f(4f), f(20f), f(4f), f(17f))
                lineTo(f(4f), f(7f))
                quadraticBezierTo(f(4f), f(4f), f(7f), f(4f))
                close()
            }
            drawPath(frame, color, style = strokeStyle)
            // 左上小圆(太阳)
            drawCircle(color, radius = f(1.6f), center = off(9f, 9f))
            // 山形折线
            val mountain = Path().apply {
                moveTo(f(4.5f), f(17.5f))
                lineTo(f(10f), f(12f))
                lineTo(f(13.5f), f(15.5f))
                lineTo(f(16f), f(13f))
                lineTo(f(19.8f), f(16.8f))
            }
            drawPath(mountain, color, style = strokeStyle)
        }

        AppIconType.Flag -> {
            // 旗杆
            val pole = Path().apply {
                moveTo(f(7.2f), f(3.8f))
                lineTo(f(7.2f), f(20.6f))
            }
            drawPath(pole, color, style = strokeStyle)
            // 燕尾旗面
            val banner = Path().apply {
                moveTo(f(7.2f), f(5.6f))
                lineTo(f(18f), f(5.6f))
                lineTo(f(15.2f), f(9.4f))
                lineTo(f(18f), f(13.2f))
                lineTo(f(7.2f), f(13.2f))
                close()
            }
            drawPath(banner, color, style = strokeStyle)
        }
    }
}
