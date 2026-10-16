package com.zyc.love_baby.features.chat

import com.zyc.love_baby.state.civilFromDays
import com.zyc.love_baby.state.nowEpochMs
import com.zyc.love_baby.state.pad2

/**
 * ChatTime — 聊天时间格式化(GMT+8 固定偏移,产品为中国大陆场景)
 * 用法:跨天判定 chatDayIndex(a) != chatDayIndex(b);气泡下单行时刻用 chatHHmm;日期分隔条文案 chatDayLabel
 * 依赖:AppState 的 civilFromDays/nowEpochMs/pad2(放宽 internal 直接复用,不复制 Hinnant 算法);
 *      commonMain 禁 JVM-only(无 java.time/String.format,纯 Long 整除算术)
 * 事件:无(纯函数)
 * 功能:epochMs<=0 一律返回 -1/空串,调用侧据此整层隐藏时间元素(服务端未回传 createTime 不造假数据)
 * 说明:多时区需走 BridgeModule 原生 dateFormatter 桥(二期);消息按服务端 id 升序追加,
 *      createTime 假定与 id 同向单调(同表自增),端上不排序
 */
private const val MS_HOUR = 3_600_000L
private const val MS_DAY = 86_400_000L

/** GMT+8 当日天序号(1970-01-01 = 0);epochMs<=0 返回 -1 */
internal fun chatDayIndex(epochMs: Long): Long =
    if (epochMs <= 0) -1L else (epochMs + 8 * MS_HOUR) / MS_DAY

/** "HH:mm"(pad2 两位补零);epochMs<=0 返回空串 */
internal fun chatHHmm(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val shifted = epochMs + 8 * MS_HOUR
    return "${pad2(((shifted / MS_HOUR) % 24).toInt())}:${pad2(((shifted / 60_000L) % 60).toInt())}"
}

/** 日期条文案:与今天同年 → "M月d日";跨年 → "yyyy年M月d日";epochMs<=0 返回空串 */
internal fun chatDayLabel(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val (y, m, d) = civilFromDays(chatDayIndex(epochMs))
    val today = civilFromDays((nowEpochMs() + 8 * MS_HOUR) / MS_DAY)
    return if (y == today.first) "${m}月${d}日" else "${y}年${m}月${d}日"
}
