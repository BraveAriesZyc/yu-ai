package com.zyc.love_baby.state

/**
 * TimeKit — 聊天时间格式化(GMT+8 固定偏移,产品为中国大陆场景)
 * 用法:跨天判定 chatDayIndex(a) != chatDayIndex(b);气泡下单行时刻用 chatHHmm;日期分隔条文案 chatDayLabel
 * 依赖:AppState 的 civilFromDays/pad2(放宽 internal 直接复用,不复制 Hinnant 算法);nowEpochMs 本件
 *      单源(2026-10-26 自 AppState 迁入,原 AppState/SessionStore/CoupleStore 三份同体收口);
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

/** 友好日期条文案:今天 / 昨天 / 其余 chatDayLabel(同年 M月d日/跨年 yyyy年M月d日);epochMs<=0 返回空串。
 *  只服务新页(心愿时间线/待办分组),聊天页保持 chatDayLabel 原样(不动已验收面) */
internal fun chatDayLabelFriendly(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val todayIndex = (nowEpochMs() + 8 * MS_HOUR) / MS_DAY
    return when (chatDayIndex(epochMs)) {
        todayIndex -> "今天"
        todayIndex - 1 -> "昨天"
        else -> chatDayLabel(epochMs)
    }
}

/** 任意天序号 → "yyyy-MM-dd" 日期键(GMT+8):moodDate 字符串分组比对用(mood record 无时刻只有日期) */
internal fun civilDateKeyGmt8(dayIndex: Long): String {
    val (y, m, d) = civilFromDays(dayIndex)
    return "$y-${pad2(m)}-${pad2(d)}"
}

/** 今晨 GMT+8 天序号(配合 civilDateKeyGmt8 取今天/昨天日期键) */
internal fun todayDayIndexGmt8(): Long = (nowEpochMs() + 8 * MS_HOUR) / MS_DAY

/** 时段徽标文案(GMT+8):清晨5-7/上午8-10/中午11-12/下午13-17/傍晚18/晚上19-22/深夜23-4;
 *  epochMs<=0 返回空串(调用侧整层隐藏,不造假数据) */
internal fun daypartLabel(epochMs: Long): String {
    if (epochMs <= 0) return ""
    return when (((epochMs + 8 * MS_HOUR) / MS_HOUR) % 24) {
        in 5..7 -> "清晨"
        in 8..10 -> "上午"
        in 11..12 -> "中午"
        in 13..17 -> "下午"
        18L -> "傍晚"
        in 19..22 -> "晚上"
        else -> "深夜" // 23-4
    }
}

/** 相对时间文案(M26 电量/位置「X 前」式,GMT+8):刚刚/N分钟前/N小时前/今天|昨天+HH:mm/
 *  M月d日/跨年补年份;epochMs<=0 返回空串(调用侧显「从未上报」,不造假时间) */
internal fun relativeTimeLabel(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val now = nowEpochMs()
    val diff = now - epochMs
    if (diff >= 0) {
        if (diff < 60_000L) return "刚刚"
        if (diff < MS_HOUR) return "${diff / 60_000L}分钟前"
        if (diff < MS_DAY) return "${diff / MS_HOUR}小时前"
    }
    // 超 1 天(或时钟回拨的负差):落到日期语义
    val (y, m, d) = civilFromDays(chatDayIndex(epochMs))
    val hhmm = chatHHmm(epochMs)
    val todayIndex = (now + 8 * MS_HOUR) / MS_DAY
    return when (chatDayIndex(epochMs)) {
        todayIndex -> "今天 $hhmm"
        todayIndex - 1 -> "昨天 $hhmm"
        else -> {
            val (nowY, _, _) = civilFromDays(todayIndex)
            if (y == nowY) "${m}月${d}日 $hhmm" else "${y}年${m}月${d}日"
        }
    }
}

/** commonMain 墙钟毫秒(expect/actual,M35:KBA Kotlin 2.0.21-ohos 无 kotlin.time.Clock——
 *  2.1.20+ stdlib 才有,ohos 工具链编不过;各端 actual 取原生墙钟,均为 UTC epoch 毫秒语义等价,
 *  state 包内调用零改动。actual:android=System.currentTimeMillis / js=Date.now /
 *  ios+ohos=posix clock_gettime(CLOCK_REALTIME)) */
internal expect fun nowEpochMs(): Long

/** "m:ss"(分:秒,秒两位补零):语音/视频时长等 <1h 计时文案单源(commonMain 禁 String.format,
 *  纯整除+pad2;原 MediaPickerSheet.formatDuration、ChatPage.recordTimeLabel 同体收编) */
internal fun formatMmss(totalSeconds: Int): String = "${totalSeconds / 60}:${pad2(totalSeconds % 60)}"
