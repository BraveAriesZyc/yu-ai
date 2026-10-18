package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.toArgb
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BridgeModule
import com.zyc.love_baby.base.WebSocketModule
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.data.model.AnniversaryInfo
import com.zyc.love_baby.data.model.CheckinInfo
import com.zyc.love_baby.data.model.CheckinSummaryInfo
import com.zyc.love_baby.data.model.MessageInfo
import com.zyc.love_baby.data.model.MoodOptionInfo
import com.zyc.love_baby.data.model.MoodRecordInfo
import com.zyc.love_baby.data.model.TodoInfo
import com.zyc.love_baby.network.AnniversaryApi
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.CheckinApi
import com.zyc.love_baby.network.MessageApi
import com.zyc.love_baby.network.MoodApi
import com.zyc.love_baby.network.TodoApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * AppState — 全局页面状态(TodoStore/MoodStore/ChatStore,M3 服务端同步版,§7.4 写穿透)
 * 用法:各页面直接读写;状态为全局单例,Tab 切换不丢失;读路径零改动,写路径一律带 pager 走接口
 * 依赖:androidx.compose.runtime 快照状态;TodoApi/MoodApi/MessageApi(HttpClient 拆包/401 单飞);
 *      SessionStore(消息 isMine 判定/换号清残留);BridgeModule(端上当日日期,可空 getModule 降级)
 * 事件:写方法(toggle/delete/add/send/sendWishes/recordMood)由页面协程调用并按返回 ApiResult toast;
 *      拉取方法(loadAll/loadMonth/loadInitial/loadIncrement)失败静默——只读降级,旧数据可见不崩(§11)
 * 功能:
 *  - TodoStore 待办清单:id 一律 String(§5.1 NumberSerializer 数字/字符串双向兼容);整表覆盖 + 写穿透
 *    (乐观更新失败回滚 / create 成功才用服务端回包追加,§7.4)
 *  - AnniversaryStore 纪念日(M5,D-17):整表覆盖 + 写穿透同范式;倒数算术是 (list, todayIndex)
 *    纯函数(commonMain 禁 java.time,daysFromCivil/civilFromDays/daysInMonthYear 公历算术),
 *    每年重复 2/29 钳当年月末、过期 +1 年,首页卡与独立页共消费
 *  - MoodStore 心愿选择(端上字典保留,§7.4)+ 每日心情月历缓存(双方记录,当月)
 *  - ChatPushStore WS 触发器(D-16):长连接只发「有新消息」纯信号,驱动 ChatStore.loadIncrement;
 *    未桥接端(iOS/ohos/H5)与断线由既有 5s 轮询兜底——渐进降级,数据完整性恒由 lastId 游标保证
 *  - ChatStore 悄悄话:id String + lastId 增量游标(§5.2 message/list);心愿消息快照化
 *    (单条 wishTitle/wishKey/colorHex/iconName 四字段,§5.2 快照式),本地假回复整体移除;
 *    createTime 透传(epoch 毫秒,端上时间行/日期分组渲染用),覆盖拉按 id 对账合并防驱逐
 *    并发已落库的本端消息(F6),pull 以 Mutex 串行防下拉刷新空返回闪现(F13)
 *  - 颜色/图标映射:Color↔hex、AppIconType↔name 双向收口在本文件(§7.6 铁律⑥,DTO 只传字符串)
 */

// ---------- 颜色/图标映射(§7.6 铁律⑥:DTO 只传 hex 与 icon name,UI 类型双向映射收口在此) ----------

/** 默认标签与色值(todo/create 无 UI 选择器,新增一律「随手记」+ 粉橘) */
internal const val TODO_DEFAULT_TAG = "随手记"
internal const val TODO_DEFAULT_COLOR_HEX = "#FF8FA3"

private fun byteHex(v: Int): String {
    val s = v.toString(16)
    return if (s.length < 2) "0$s" else s
}

/** Color → "#RRGGBB"(业务色恒不透明,丢弃 alpha;toArgb 为 Kuikly compose 同包扩展) */
internal fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return "#" + byteHex((argb shr 16) and 0xFF) + byteHex((argb shr 8) and 0xFF) + byteHex(argb and 0xFF)
}

/**
 * "#RRGGBB"/"#AARRGGBB" → Color;解析失败回 fallback(服务端脏数据/旧版本色值不崩,
 * 断网旧数据可见语义 §11)。fallback 默认 = 新增待办的默认标签色
 */
internal fun colorFromHex(hex: String, fallback: Color = Color(0xFFFF8FA3)): Color {
    val v = hex.trim().removePrefix("#")
    val argb = when (v.length) {
        6 -> 0xFF000000L or (v.toLongOrNull(16) ?: return fallback)
        8 -> v.toLongOrNull(16) ?: return fallback
        else -> return fallback
    }
    return Color(argb)
}

/** AppIconType → icon name(快照消息 icon 字段,取 enum name,如 "Heart") */
internal fun appIconName(icon: AppIconType): String = icon.name

/** icon name → AppIconType;映射缺失(他端字典新增图标)兜底 Heart,渲染不空转 */
internal fun appIconFromName(name: String): AppIconType =
    AppIconType.entries.firstOrNull { it.name == name } ?: AppIconType.Heart

// ---------- 月份/日期工具(心情日历;commonMain 无 java.time,纯公历算术) ----------

/** 两位补零(聊天时间格式化 ChatTime.kt 复用,故放宽 internal;月份/日期工具内部也用) */
internal fun pad2(v: Int): String {
    val s = v.toString()
    return if (s.length < 2) "0$s" else s
}

/** "yyyy-MM" → (年, 月);非法输入回 (0, 0)(调用方按空态渲染) */
internal fun monthParts(ym: String): Pair<Int, Int> {
    val parts = ym.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return if (y in 1..9999 && m in 1..12) y to m else 0 to 0
}

/** 月份推移:("2026-10", -1) = "2026-09",跨年自动进退位;非法输入回空串 */
internal fun monthShift(ym: String, delta: Int): String {
    val (y, m) = monthParts(ym)
    if (y == 0) return ""
    val total = y * 12 + (m - 1) + delta
    return "${total / 12}-${pad2(total % 12 + 1)}"
}

/** 某月天数(含闰年) */
internal fun daysInMonthYear(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

/** 当月 1 号是星期几(周一=0..周日=6;与设备时区无关,只做公历算术) */
internal fun firstWeekdayOfMondayFirst(year: Int, month: Int): Int {
    val days = daysFromCivil(year, month, 1)
    return ((days % 7) + 7 + 3).toInt() % 7 // 1970-01-01 周四:0+3=3 → 周一=0 制下周四=3
}

/** (年,月,日) → "yyyy-MM-dd"(日历格与服务端 moodDate 字符串直接比对,§5.2 端上不做格式换算) */
internal fun dateKeyOf(year: Int, month: Int, day: Int): String = "$year-${pad2(month)}-${pad2(day)}"

/** Howard Hinnant days_from_civil:公历 (y,m,d) → 距 1970-01-01 天数(纪念日倒数纯函数复用,放宽 internal) */
internal fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (m + 9) % 12
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

/** Howard Hinnant civil_from_days:距 1970-01-01 天数 → (y,m,d)(聊天日期条 ChatTime.kt 复用,故放宽 internal) */
internal fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    val z = z0 + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (if (m <= 2) y + 1 else y).toInt()
    return Triple(year, m, d)
}

/** epoch 毫秒 → UTC "yyyy-MM-dd"(BridgeModule 不可用时的兜底;时区差仅月末/月初临界日有影响) */
private fun utcDate(epochMs: Long): String {
    val (y, m, d) = civilFromDays(epochMs.floorDiv(86_400_000L))
    return "$y-${pad2(m)}-${pad2(d)}"
}

/**
 * 端上当日 "yyyy-MM-dd"(宿主 dateFormatter,设备时区,formatBindTime 同款可空 getModule 降级;
 * 模块不可用回退 UTC 墙钟)。心情日历今日高亮与 recordMood 乐观占位共用
 */
internal fun currentDate(pager: Pager): String {
    val now = nowEpochMs()
    val formatted = try {
        pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)
            ?.dateFormatter(now, "yyyy-MM-dd")
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
    return formatted ?: utcDate(now)
}

@OptIn(ExperimentalTime::class)
internal fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

// ---------- 心情选项 UI 语义(M3.5 D-14:couple 私有字典服务端下发,端上只做 hex→Color 预解析) ----------

internal data class MoodOption(
    val id: String, // 服务端选项编号(§5.1 String;记录心情/删除均回传此值)
    val label: String,
    val emoji: String,
    val colorHex: String,
    val color: Color, // colorFromHex 预解析(粉兜底;选择器芯片渲染用,铁律⑥)
)

/**
 * 存量兜底:M3.5 前旧记录 mood_type 1-5 → 配色(hex 与 love.sql 回填段 CASE 映射逐字符一致)。
 * 常态渲染不经过此映射(新记录带 color 快照;存量行已被回填段补齐快照)——仅服务端未跑回填段的
 * 窗口期保险,死路径保留
 */
private val legacyMoodColorHexes = mapOf(
    1 to "#FFB93C",
    2 to "#6C8BFF",
    3 to "#8FA3BF",
    4 to "#FF5A5A",
    5 to "#9B6BFF",
)

/**
 * 记录取色:color 快照优先(记录时点定格——删除选项后历史月历不变灰,D-14)→ 快照缺失走存量
 * moodType(1-5)兜底 → 再回 [fallback](日历格灰点 TextTertiary,由 UI 层定夺)
 */
internal fun moodRecordColor(record: MoodRecordInfo, fallback: Color): Color {
    if (record.color.isNotEmpty()) return colorFromHex(record.color, fallback)
    val hex = legacyMoodColorHexes[record.moodType] ?: return fallback
    return colorFromHex(hex, fallback)
}

// ---------- 待办 ----------

internal data class TodoItem(
    val id: String, // 服务端 Long 按 String 解析(§5.1);本地不再自增
    val title: String,
    val tag: String,
    val tagColor: Color, // 端上 UI 色;上送前经 colorToHex 转 hex(§7.6 铁律⑥)
    val done: Boolean = false,
    val createTime: Long = 0L, // 创建时刻 epoch 毫秒(服务端 create_time asc 排序语义源);0=未回传,日期分组归「更早」
)

internal object TodoStore {
    var todos by mutableStateOf(emptyList<TodoItem>())
        private set
    var filter by mutableIntStateOf(0) // 0全部 1进行中 2已完成

    val doneCount: Int get() = todos.count { it.done }
    val pendingCount: Int get() = todos.count { !it.done }

    /**
     * 全量拉取(§7.4 拉取触发点):服务端按 create_time asc,id asc 排序,端上整表覆盖(不重排)。
     * 失败静默——只读降级,旧数据可见(§11),toast 交给「写失败」场景
     */
    suspend fun loadAll(pager: Pager): ApiResult {
        val result = TodoApi.list(pager)
        if (result is ApiResult.Ok) {
            todos = TodoInfo.listFrom(result.dataArray).map { info ->
                TodoItem(
                    id = info.id,
                    title = info.title,
                    tag = info.tag.ifEmpty { TODO_DEFAULT_TAG },
                    tagColor = colorFromHex(info.color.ifEmpty { TODO_DEFAULT_COLOR_HEX }),
                    done = info.done,
                    createTime = info.createTime,
                )
            }
        }
        return result
    }

    /**
     * 勾选/取消:乐观翻转 → update 五字段全量上送(last-write,§7.4 D-6)→ 失败回滚 + 调用方 toast。
     * 成功不回填 update 回包:乐观态即端上最新值,回包仅作确认;并发覆盖由下次 loadAll 校准。
     * 目标不存在(id 已被对端删)直接幂等返回 Ok,不请求不 toast
     */
    suspend fun toggle(pager: Pager, id: String): ApiResult {
        val target = todos.firstOrNull { it.id == id } ?: return ApiResult.Ok(JSONObject())
        val snapshot = todos
        todos = todos.map { if (it.id == id) it.copy(done = !it.done) else it }
        val updated = todos.first { it.id == id }
        val result = TodoApi.update(pager, id, updated.title, updated.done, updated.tag, colorToHex(updated.tagColor))
        if (result !is ApiResult.Ok) {
            todos = snapshot
        }
        return result
    }

    /** 删除:乐观移除 → delete 失败回滚 + 调用方 toast(§7.4 写穿透失败即回滚) */
    suspend fun delete(pager: Pager, id: String): ApiResult {
        val snapshot = todos
        todos = todos.filter { it.id != id }
        val result = TodoApi.delete(pager, id)
        if (result !is ApiResult.Ok) {
            todos = snapshot
        }
        return result
    }

    /**
     * 新增:create 成功后用回包(含服务端 id)追加;失败不落任何本地态(调用方 toast,无脏数据,§7.4)。
     * 不做乐观插入:id 必须来自服务端(LazyColumn key 去重/后续 update/delete 都靠它)
     */
    suspend fun add(pager: Pager, title: String): ApiResult {
        val result = TodoApi.create(pager, title, TODO_DEFAULT_TAG, TODO_DEFAULT_COLOR_HEX)
        if (result is ApiResult.Ok) {
            TodoInfo.from(result.data)?.let { info ->
                todos = todos + TodoItem(
                    id = info.id,
                    title = info.title.ifEmpty { title },
                    tag = info.tag.ifEmpty { TODO_DEFAULT_TAG },
                    tagColor = colorFromHex(info.color.ifEmpty { TODO_DEFAULT_COLOR_HEX }),
                    done = info.done,
                    createTime = info.createTime,
                )
            }
        }
        return result
    }

    /** 登出/换号清内存(MainPager 接线调用),防上会话待办残留 */
    fun reset() {
        todos = emptyList()
        filter = 0
    }
}

// ---------- 纪念日(M5,§12.4 预留落位;D-17:倒数算术全端上,服务端只存原始日期) ----------

/**
 * AnniversaryStore 纪念日清单:整表覆盖 + 写穿透(§7.4 TodoStore 同范式)。
 * anniversaries 保留服务端序(anniv_date asc, id asc),展示序由顶层 [sortedAnniversaries]
 * 纯函数在消费侧重排——跨天只重算不重拉
 */
internal object AnniversaryStore {
    var anniversaries by mutableStateOf(emptyList<AnniversaryInfo>())
        private set

    /**
     * 全量拉取(§7.4 触发点:首页 bindState 首拉 / 独立页进入 / 回前台):整表覆盖。
     * 失败静默——只读降级,旧数据可见(§11),不 toast(toast 交给「写失败」场景)
     */
    suspend fun loadList(pager: Pager): ApiResult {
        val result = AnniversaryApi.list(pager)
        if (result is ApiResult.Ok) {
            anniversaries = AnniversaryInfo.listFrom(result.dataArray)
        }
        return result
    }

    /**
     * 新增:create 成功后用回包(含服务端 id)追加;失败不落任何本地态(调用方 toast,§7.4)。
     * 不做乐观插入:id 必须来自服务端(LazyColumn key/后续 delete 都靠它);回包解析失败不落态
     * 但按成功返回——服务端已建行,下次 loadList 整表覆盖自愈(TodoStore.add 同款口径)
     */
    suspend fun add(
        pager: Pager,
        title: String,
        annivDate: String,
        repeatType: Int,
        emoji: String,
    ): ApiResult {
        val result = AnniversaryApi.create(pager, title, annivDate, repeatType, emoji)
        if (result is ApiResult.Ok) {
            AnniversaryInfo.from(result.data)?.let { info ->
                anniversaries = anniversaries + info
            }
        }
        return result
    }

    /** 删除:乐观移除 → delete 失败回滚 + 调用方 toast(§7.4 写穿透失败即回滚,TodoStore.delete 同款) */
    suspend fun delete(pager: Pager, id: String): ApiResult {
        val snapshot = anniversaries
        anniversaries = anniversaries.filter { it.id != id }
        val result = AnniversaryApi.delete(pager, id)
        if (result !is ApiResult.Ok) {
            anniversaries = snapshot
        }
        return result
    }

    /** 登出/换号/解绑清内存(MainPager 接线调用),防上会话/他人纪念日残留 */
    fun reset() {
        anniversaries = emptyList()
    }
}

/** "yyyy-MM-dd" → 天序号(1970-01-01=0);非法输入(空/格式错/日历不存在如 2-30)回 null,调用方按无效沉底 */
internal fun anniversaryDayIndex(dateKey: String): Long? {
    val parts = dateKey.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    val d = parts.getOrNull(2)?.toIntOrNull() ?: return null
    if (y !in 1..9999 || m !in 1..12 || d !in 1..daysInMonthYear(y, m)) return null
    return daysFromCivil(y, m, d)
}

/**
 * 距 [item] 下一次发生的倒数天数(相对 [todayIndex],GMT+8 天序号):
 *  - 每年重复(repeatType=1):下一次 = 今年的月日,已过(今天不算)则 +1 年;2/29 类不存在于
 *    当年的月日用 [daysInMonthYear] 钳到当年月末(2/29→2/28,不折算 3/1,D-17 冻结口径)
 *  - 一次性(repeatType=2):目标日天序号 − 今天,可为负(已过)
 * 解析失败回 null(首页显 "--",列表沉底)
 */
internal fun anniversaryCountdownDays(item: AnniversaryInfo, todayIndex: Long): Long? {
    val base = anniversaryDayIndex(item.annivDate) ?: return null
    if (item.repeatType != 1) return base - todayIndex
    val (_, m, d) = civilFromDays(base)
    val clamp: (Int) -> Int = { year -> d.coerceAtMost(daysInMonthYear(year, m)) }
    val thisYear = civilFromDays(todayIndex).first
    val thisOccurrence = daysFromCivil(thisYear, m, clamp(thisYear))
    val target = if (thisOccurrence < todayIndex) {
        val nextYear = thisYear + 1
        daysFromCivil(nextYear, m, clamp(nextYear))
    } else {
        thisOccurrence
    }
    return target - todayIndex
}

/**
 * 展示序(D-17):将来的(倒数 ≥ 0,含今天)按倒数升序在前;已过一次性沉底(倒数升序 = 日期
 * 升序,同义);解析失败最后。纯函数(不写 Store)——HomePage 与独立页共同消费,跨天只需重算
 */
internal fun sortedAnniversaries(
    items: List<AnniversaryInfo>,
    todayIndex: Long,
): List<AnniversaryInfo> =
    items
        .map { it to anniversaryCountdownDays(it, todayIndex) }
        .sortedWith(
            compareBy(
                { (_, c) ->
                    when {
                        c == null -> 2 // 解析失败最后
                        c < 0 -> 1     // 已过一次性沉底
                        else -> 0      // 将来/今天在前
                    }
                },
                { (_, c) -> c ?: Long.MAX_VALUE },
            ),
        )
        .map { it.first }

/** 最近一个将来的纪念日倒数(min 非负);无(空表/只剩已过一次性/解析全失败)回 null——首页显 "--" */
internal fun nextAnniversaryCountdownDays(items: List<AnniversaryInfo>, todayIndex: Long): Long? =
    items.mapNotNull { anniversaryCountdownDays(it, todayIndex) }.filter { it >= 0 }.minOrNull()

/** 倒数徽标文案:还有 N 天 / 就是今天(=0)/ 已 N 天(一次性已过);解析失败回空串(调用方隐藏徽标) */
internal fun anniversaryCountdownLabel(item: AnniversaryInfo, todayIndex: Long): String {
    val c = anniversaryCountdownDays(item, todayIndex) ?: return ""
    return when {
        c > 0 -> "还有 $c 天"
        c == 0L -> "就是今天"
        else -> "已 ${-c} 天"
    }
}

// ---------- 每日打卡(M8,§12.4 预留落位;纯日期点 + 共同连续,无补打/无撤销) ----------

/**
 * CheckinStore 每日打卡:当月缓存 + 汇总 + 写穿透(§7.4 TodoStore 范式)。
 * checkins 只装「最后一次成功拉取的月份」的 couple 双方记录(displayMonth 标记);
 * 今日双布尔(checkedToday/partnerCheckedToday)独立于月缓存——翻月浏览不丢今天的打卡状态,
 * 每次拉到当月明细时按服务端校准。
 */
internal object CheckinStore {
    /** 当月打卡缓存(§5.2 checkin/list couple 维度双方记录);切月/进入整表覆盖 */
    var checkins by mutableStateOf(emptyList<CheckinInfo>())
        private set

    /** 日历展示月(yyyy-MM;空串=未初始化,进入时由页面传当月起拉) */
    var displayMonth by mutableStateOf("")
        private set

    /** 打卡汇总(首页「今日打卡」条与打卡页页头共用;null=尚未拉到,调用方显 "--") */
    var summary by mutableStateOf<CheckinSummaryInfo?>(null)
        private set

    /** 我今天是否已打卡(独立于月缓存:翻月浏览不丢今日状态;check 成功置位,拉当月明细校准) */
    var checkedToday by mutableStateOf(false)
        private set

    /** TA 今天是否已打卡(同上;仅拉到当月明细时可校准) */
    var partnerCheckedToday by mutableStateOf(false)
        private set

    /** 我在 [dateKey](yyyy-MM-dd)的打卡记录;无记录回 null(月历格按空缺渲染) */
    fun myCheckinOn(dateKey: String): CheckinInfo? =
        checkins.firstOrNull { it.userId == SessionStore.userId && it.checkinDate == dateKey }

    /** 伴侣在 [dateKey] 的打卡记录(userId ≠ 我;页仅在已绑定时可达) */
    fun partnerCheckinOn(dateKey: String): CheckinInfo? =
        checkins.firstOrNull { it.userId != SessionStore.userId && it.checkinDate == dateKey }

    /** 切月请求序号:连点切月并发防护(MoodStore.loadMonth 同款)——仅最后一个发出的请求可落状态 */
    private var monthReqSeq = 0

    /**
     * 按月拉取(§7.4 拉取触发点:进入/切月):成功才更新 displayMonth 与缓存,
     * 失败保留现月数据静默返回(只读降级);month 空串(尚未初始化)直接幂等返回。
     * 过期回包作废(M3 评审修正:连点切月请求乱序完成,后完成的旧月回包不得覆盖新月)
     */
    suspend fun loadMonth(pager: Pager, month: String): ApiResult {
        if (month.isEmpty()) return ApiResult.Ok(JSONObject())
        val seq = ++monthReqSeq
        val result = CheckinApi.list(pager, month)
        if (seq != monthReqSeq) return result // 过期回包:期间已有更新的切月请求,不落状态
        if (result is ApiResult.Ok) {
            displayMonth = month
            val rows = CheckinInfo.listFrom(result.dataArray)
            checkins = rows
            syncTodayFlags(pager, month, rows)
        }
        return result
    }

    /**
     * 拉取汇总(§7.4 触发点:首页 bindState 首拉 / 打卡页进入 / check 成功后):
     * 成功整对象覆盖,失败静默保留旧值——只读降级,toast 交给写失败场景
     */
    suspend fun loadSummary(pager: Pager): ApiResult {
        val result = CheckinApi.summary(pager)
        if (result is ApiResult.Ok) {
            summary = CheckinSummaryInfo.from(result.data)
        }
        return result
    }

    /**
     * 前台校准今日双布尔(M18 修复:首页/回前台此前无通道——checkedToday 仅 check 置位、
     * syncTodayFlags 仅打卡页 loadMonth 落值,冷启动/回前台时首页打卡条长时陈旧:上午打过卡
     * 下午冷启动仍显未打卡,对方打卡后恒「等 TA 中」)。拉当月明细只校准 flags;displayMonth==
     * 当月(打卡页正看当月)时缓存一并刷新防 flags 与月历脱节;翻月浏览中则不动
     * checkins/displayMonth(浏览不被拽回当月)
     */
    suspend fun syncToday(pager: Pager): ApiResult {
        val month = currentDate(pager).take(7)
        if (month.isEmpty()) return ApiResult.Ok(JSONObject())
        // 与 loadMonth 共用 monthReqSeq 乱序闸(对抗审查 M18:syncToday 是新的写通道,
        // 迟到回包不得覆盖更晚发起的切月请求落态)
        val seq = ++monthReqSeq
        val result = CheckinApi.list(pager, month)
        if (seq != monthReqSeq) return result // 过期回包:期间已有更新的拉取/打卡,不落状态
        if (result is ApiResult.Ok) {
            val rows = CheckinInfo.listFrom(result.dataArray)
            if (displayMonth == month) checkins = rows
            syncTodayFlags(pager, month, rows)
        }
        return result
    }

    /**
     * 打卡今天(§7.4 写穿透):uk 幂等由服务端保证(重复打卡回已有记录不打错),端上按成功处理。
     * 回包落态:checkedToday 置位;回包记录并入当月缓存(仅当回包月 == 展示月,防跨月脏插);
     * 随后重拉 summary(打卡直接改变 streak/累计,跨日口径本地增量算不准,整对象覆盖最稳)
     */
    suspend fun check(pager: Pager): ApiResult {
        val result = CheckinApi.check(pager)
        if (result is ApiResult.Ok) {
            // 写使在飞读作废(对抗审查 M18:弱网下首页 syncToday/打卡页 loadMonth 与打卡并发,
            // 服务端处理早于打卡的迟到回包会把 checkedToday/checkins 打回打卡前状态)——
            // 打卡成功即 bump seq,所有在飞拉取回包过期不落;月历完整性由下方并入今日行兜底
            monthReqSeq++
            checkedToday = true
            val info = CheckinInfo.from(result.data)
            if (info != null && displayMonth.isNotEmpty() && info.checkinDate.take(7) == displayMonth) {
                checkins = checkins.filterNot {
                    it.userId == info.userId && it.checkinDate == info.checkinDate
                } + info
            }
            loadSummary(pager)
        }
        return result
    }

    /** 登出/换号/解绑清内存(MainPager 接线调用),防上会话/他人打卡残留 */
    fun reset() {
        checkins = emptyList()
        displayMonth = ""
        summary = null
        checkedToday = false
        partnerCheckedToday = false
    }

    /** 请求月 = 当月时按服务端明细校准今日双布尔(翻月浏览不丢今天的打卡状态) */
    private fun syncTodayFlags(pager: Pager, month: String, rows: List<CheckinInfo>) {
        val todayKey = currentDate(pager) // yyyy-MM-dd(dateFormatter 设备时区,UTC 兜底)
        if (todayKey.take(7) != month) return
        checkedToday = rows.any { it.userId == SessionStore.userId && it.checkinDate == todayKey }
        partnerCheckedToday = rows.any { it.userId != SessionStore.userId && it.checkinDate == todayKey }
    }
}

// ---------- 心愿(情绪)与每日心情 ----------

internal data class Wish(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
    val icon: AppIconType,
)

val moodCategories = listOf("想亲亲", "想抱抱", "想吃点", "想念", "撒个娇", "和好")

private val catColors = mapOf(
    "想亲亲" to listOf(Color(0xFFFF9AC4), Color(0xFFFF5A8C)),
    "想抱抱" to listOf(Color(0xFFFFB27A), Color(0xFFFF8A5C)),
    "想吃点" to listOf(Color(0xFFFFD98E), Color(0xFFFFB25C)),
    "想念" to listOf(Color(0xFF8FB8FF), Color(0xFF6C8BFF)),
    "撒个娇" to listOf(Color(0xFFC09AFF), Color(0xFF9B6BFF)),
    "和好" to listOf(Color(0xFF9BEBB5), Color(0xFF4EB087)),
)

/**
 * 24 条心愿文案字典留端上(§7.4:服务端无 wish 实体,只同步 kind=2 快照消息与 love_mood_record);
 * 送出时按快照四字段(id/title/主色 hex/icon name)上送,两设备还原靠快照而非字典版本
 */
internal val allWishes: List<Wish> = listOf(
    Wish("w1", "想亲亲", "求亲亲", "mua 一口就跑", catColors["想亲亲"]!!, AppIconType.Heart),
    Wish("w2", "想亲亲", "贴贴脸", "脸贴脸蹭蹭", catColors["想亲亲"]!!, AppIconType.Star),
    Wish("w3", "想亲亲", "早安吻", "叫醒服务", catColors["想亲亲"]!!, AppIconType.Bell),
    Wish("w4", "想亲亲", "晚安吻", "睡前专属", catColors["想亲亲"]!!, AppIconType.Moon),
    Wish("w5", "想抱抱", "充电式抱抱", "充到 100%", catColors["想抱抱"]!!, AppIconType.Heart),
    Wish("w6", "想抱抱", "熊抱", "抱到悬浮", catColors["想抱抱"]!!, AppIconType.Star),
    Wish("w7", "想抱抱", "背后抱", "突然袭击", catColors["想抱抱"]!!, AppIconType.Bell),
    Wish("w8", "想抱抱", "举高高", "转圈圈", catColors["想抱抱"]!!, AppIconType.Sun),
    Wish("w9", "想吃点", "蛋糕一块", "草莓味的", catColors["想吃点"]!!, AppIconType.Heart),
    Wish("w10", "想吃点", "奶茶三分甜", "去冰加珍珠", catColors["想吃点"]!!, AppIconType.Star),
    Wish("w11", "想吃点", "火锅一顿", "鸳鸯锅安排", catColors["想吃点"]!!, AppIconType.Sun),
    Wish("w12", "想吃点", "冰淇淋", "甜到心里", catColors["想吃点"]!!, AppIconType.Moon),
    Wish("w13", "想念", "想你了", "超想那种", catColors["想念"]!!, AppIconType.Heart),
    Wish("w14", "想念", "视频一下", "看看彼此", catColors["想念"]!!, AppIconType.Bell),
    Wish("w15", "想念", "来见我", "立刻马上", catColors["想念"]!!, AppIconType.Star),
    Wish("w16", "想念", "写封情书", "手写才有诚意", catColors["想念"]!!, AppIconType.Moon),
    Wish("w17", "撒个娇", "夸夸我", "彩虹屁来十句", catColors["撒个娇"]!!, AppIconType.Star),
    Wish("w18", "撒个娇", "捏捏脸", "轻轻的那种", catColors["撒个娇"]!!, AppIconType.Heart),
    Wish("w19", "撒个娇", "陪我看剧", "要抱着看", catColors["撒个娇"]!!, AppIconType.Moon),
    Wish("w20", "撒个娇", "哄睡服务", "讲个故事", catColors["撒个娇"]!!, AppIconType.Bell),
    Wish("w21", "和好", "抱抱和好", "不许生气了", catColors["和好"]!!, AppIconType.Heart),
    Wish("w22", "和好", "请你吃饭", "你选地方", catColors["和好"]!!, AppIconType.Sun),
    Wish("w23", "和好", "看场电影", "我买爆米花", catColors["和好"]!!, AppIconType.Star),
    Wish("w24", "和好", "散步谈心", "手牵手那种", catColors["和好"]!!, AppIconType.Moon),
)

internal object MoodStore {
    var categoryIndex by mutableIntStateOf(0)

    /**
     * 当前选中心情选项 id(空串 = 未选;M3.5)。住 Store 而非页内 remember:选中态要与 categoryIndex
     * 同级跨组件共享——管理浮层(挂 MainPager 根 Box)删除选中项后需清此值,页内 remember 够不着
     */
    var selectedOptionId by mutableStateOf("")

    var selectedIds by mutableStateOf(setOf<String>())
        private set

    /** 当月心情缓存(§5.2 mood/record/list couple 维度双方记录);切月/进入整表覆盖 */
    var moodRecords by mutableStateOf(emptyList<MoodRecordInfo>())
        private set

    /** 日历展示月(yyyy-MM;空串=未初始化,进入时 ensureDisplayMonth 定当月,切月由日历卡驱动) */
    var displayMonth by mutableStateOf("")

    /**
     * couple 私有心情选项字典(M3.5 D-14):进入心情页/切回 Tab 整表覆盖服务端排序
     * (sort asc, id asc:播种行在前、新增落尾),端上按回包顺序渲染不重排;
     * 空表 = 空字典(模板空或全被删),选择器走空态自愈提示「点 + 添加心情」
     */
    var moodOptions by mutableStateOf(emptyList<MoodOption>())
        private set

    val selectedWishes: List<Wish>
        get() = allWishes.filter { it.id in selectedIds }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun clear() {
        selectedIds = emptySet()
    }

    /** 我在 [dateKey](yyyy-MM-dd)的记录;无记录回 null(日历格按空缺渲染) */
    fun myMoodOn(dateKey: String): MoodRecordInfo? =
        moodRecords.firstOrNull { it.userId == SessionStore.userId && it.moodDate == dateKey }

    /** 伴侣在 [dateKey] 的记录(userId ≠ 我;页仅在已绑定时可达) */
    fun partnerMoodOn(dateKey: String): MoodRecordInfo? =
        moodRecords.firstOrNull { it.userId != SessionStore.userId && it.moodDate == dateKey }

    /** 初始化展示月为当月(幂等;dateFormatter 设备时区,UTC 兜底见 [currentDate]) */
    fun ensureDisplayMonth(pager: Pager) {
        if (displayMonth.isNotEmpty()) return
        displayMonth = currentDate(pager).take(7) // yyyy-MM-dd → yyyy-MM
    }

    /** 切月请求序号:连点切月并发防护——仅最后一个发出的请求可落状态,过期回包丢弃(M3 评审修正) */
    private var monthReqSeq = 0

    /**
     * 按月拉取(§7.4 拉取触发点:进入/切月):成功才更新 displayMonth 与缓存,
     * 失败保留现月数据静默返回(只读降级);month 空串(尚未初始化)直接幂等返回。
     * 并发防护(displayMonth 已按「请求携带的月份」回填仍不够):连点切月时请求乱序完成,
     * 后完成的旧月回包会覆盖新月——以自增序号作废过期回包,最终展示月与最后一次点击一致
     */
    suspend fun loadMonth(pager: Pager, month: String): ApiResult {
        if (month.isEmpty()) return ApiResult.Ok(JSONObject())
        val seq = ++monthReqSeq
        val result = MoodApi.list(pager, month)
        if (seq != monthReqSeq) return result // 过期回包:期间已有更新的切月请求,不落状态
        if (result is ApiResult.Ok) {
            displayMonth = month
            moodRecords = MoodRecordInfo.listFrom(result.dataArray)
        }
        return result
    }

    /**
     * 拉取 couple 私有选项字典(§7.4 拉取触发点:进入心情页首拉/切回 Tab 补拉):
     * 成功整表覆盖(服务端 sort asc, id asc 排序),失败静默保留旧字典——只读降级,toast 交给写失败场景。
     * 回包 data 为数组,空数组 = 空字典属正常回包(端上空态自愈,不视为失败)
     */
    suspend fun loadOptions(pager: Pager): ApiResult {
        val result = MoodApi.optionList(pager)
        if (result is ApiResult.Ok) {
            moodOptions = MoodOptionInfo.listFrom(result.dataArray).map { info ->
                MoodOption(
                    id = info.id,
                    label = info.label,
                    emoji = info.emoji,
                    colorHex = info.color,
                    color = colorFromHex(info.color),
                )
            }
        }
        return result
    }

    /**
     * 新增选项(§7.4 写穿透):服务端先,回包(含服务端 id)才落态——照 TodoStore.add,防脏 key
     * (选项 id 是记录心情/删除的回传键,必须服务端权威)。回包解析失败(id 空判无效)不落态但按
     * 成功返回:服务端已建行,下次 loadOptions 整表覆盖自愈;调用方仅按失败 toast
     */
    suspend fun addOption(pager: Pager, label: String, emoji: String, colorHex: String): ApiResult {
        val result = MoodApi.optionCreate(pager, label, emoji, colorHex)
        if (result is ApiResult.Ok) {
            MoodOptionInfo.from(result.data)?.let { info ->
                moodOptions = moodOptions + MoodOption(
                    id = info.id,
                    label = info.label.ifEmpty { label },
                    emoji = info.emoji,
                    colorHex = info.color,
                    color = colorFromHex(info.color),
                )
            }
        }
        return result
    }

    /**
     * 删除选项(§7.4 写穿透):乐观移除 → 失败回滚 + 调用方 toast(重名/上限等服务端 1_003_002_xxx)。
     * 删的是当前选中项时由调用方清 [selectedOptionId](选中态住 Store,管理浮层与页共用);
     * 历史月历不受影响——记录渲染走快照列,与字典无关(D-14)
     */
    suspend fun deleteOption(pager: Pager, id: String): ApiResult {
        val snapshot = moodOptions
        moodOptions = moodOptions.filterNot { it.id == id }
        val result = MoodApi.optionDelete(pager, id)
        if (result !is ApiResult.Ok) {
            moodOptions = snapshot
        }
        return result
    }

    /**
     * 记当日心情(§7.4 写穿透):乐观覆盖我的当日记录为携带快照三列的占位(label/emoji/color 取自
     * 当前选中选项,id 空串 = 未确认)→ POST 失败回滚 + 调用方 toast。成功后占位直接定稿:服务端
     * mood/record 回 Boolean 无 VO 回包,不解析;日历格渲染吃快照列不经字典回查(选项可能已被对端
     * 删,D-14),下次进入/切月按月整表覆盖自愈校正(§4 uk_user_date 服务端覆盖写,无脏语义)。
     * 展示月非当月(在翻历史月)时不做乐观,避免把今天写进历史月缓存。
     * 选项不在本地字典(极端:对端刚删)时仍照发——服务端按 1_003_002_000 拒绝并回滚,不落脏数据
     */
    suspend fun recordMood(pager: Pager, optionId: String): ApiResult {
        val option = moodOptions.firstOrNull { it.id == optionId }
        val snapshot = moodRecords
        val today = currentDate(pager)
        val sameMonth = displayMonth.isEmpty() || today.take(7) == displayMonth
        if (option != null && today.isNotEmpty() && sameMonth) {
            moodRecords = moodRecords.filterNot { it.userId == SessionStore.userId && it.moodDate == today } +
                MoodRecordInfo("", SessionStore.userId, today, 0, option.id, option.label, option.emoji, option.colorHex, nowEpochMs())
        }
        val result = MoodApi.record(pager, optionId)
        if (result !is ApiResult.Ok) {
            moodRecords = snapshot
        }
        return result
    }

    /** 登出/换号/解绑清内存(MainPager 接线调用);字典随清——couple 私有,换绑定重新播种 */
    fun reset() {
        categoryIndex = 0
        selectedOptionId = ""
        selectedIds = emptySet()
        moodRecords = emptyList()
        moodOptions = emptyList()
        displayMonth = ""
    }
}

// ---------- 聊天 ----------

internal enum class MsgKind { TEXT, WISH, IMAGE }

/**
 * 悄悄话/心愿消息。M3 快照化:原 wishTitles 多标题结构删除,改为单条心愿消息四字段
 * (§5.2 send-wishes 快照式:wishKey/text/color/icon 整体入 love_message,kind=WISH 时渲染
 * icon+color+title,colorHex/iconName 为服务端原样字符串,渲染时经 colorFromHex/appIconFromName
 * 双向映射并兜底,§7.6 铁律⑥)
 */
internal data class ChatMsg(
    val id: String, // 服务端 Long 按 String 解析(§5.1)
    val isMine: Boolean,
    val text: String,
    val kind: MsgKind = MsgKind.TEXT,
    val wishTitle: String = "",
    val wishKey: String = "",
    val colorHex: String = "",
    val iconName: String = "",
    val imageUrl: String = "", // kind=IMAGE 时为图片完整 URL(content 列透传,M10)
    val createTime: Long = 0L, // epoch 毫秒(服务端 VO 回传);0 = 未回传,渲染侧整层隐藏时间元素
    val recalled: Boolean = false, // M12 墓碑:已撤回消息渲染灰条(服务端不删行,原字段保留)
)

internal object ChatStore {

    /** 停留聊天页期间的轻轮询间隔(M3 实施修正,§7.4):设计原触发点(进页/下拉/本端发送)不含
     *  停留期间自动刷新,对方发消息须切 Tab 才可见;补 5s 静默增量轮询收口(仅 BOUND 且停留本页时跑) */
    const val POLL_INTERVAL_MS = 5_000L

    var messages by mutableStateOf(emptyList<ChatMsg>())
        private set

    /** 服务端已拉取回包中的最大消息 id(§5.2 message/list 增量游标)。**仅由 pull 回包推进**——send 只入列
     *  不推游标:本端消息 id 若顶游标,会跳过对方并发已落库的低 id 消息(id 区间盲区),增量永不重拉
     *  =并发丢消息(2026-10-17 真机验收发现修复);空串 = 尚未成功拉过任何消息 */
    var lastId by mutableStateOf("")
        private set

    /** 未读红点数(M11,服务端口径:couple 内 id>我方 chat_read_cursor 且非本人;仅 refreshUnread/markRead 维护) */
    var unreadCount by mutableIntStateOf(0)
        private set

    /** 聊天页可见态(ChatPage effect 上报,M11):true=停留本页,pull 落态走 markRead;false=走 refreshUnread */
    private var chatVisible = false

    /** 本端已成功标记的游标(M11 markRead guard:未越过即不发 POST,防 5s 轮询重发;失败不推进=下轮重试自愈) */
    private var markedCursor = ""

    /**
     * 在飞拉取互斥(F13):首组合(页面 LaunchedEffect)/切 Tab(MainPager)/下拉刷新/停留轮询可能
     * 并发触发 pull,串行排队「等待后各拉各的」而非命中在飞即空返回——空返回会让下拉刷新指示器
     * 闪现即收(刷新像坏了);等待方拿到自己的新鲜回包,重复条目由 appendDedupe 幂等兜底。
     * 顺带消除覆盖拉与增量拉的 check-then-act 竞态(原 pullInFlight 标记在 Kuikly 单线程事件循环
     * 下无挂起间隙才原子,依赖实现细节)
     */
    private val pullMutex = Mutex()

    /**
     * 首屏是否已成功拉过(与 lastId 解耦的双保险):进页 LaunchedEffect 的幂等守卫——未拉过才空游标
     * 整页覆盖,拉过则重进靠切 Tab/轮询增量,不重复覆盖(防每次进页整表重建跳动)。send 不再推游标后,
     * 空串 lastId 事实上已等价「未首拉」,守卫仍独立于游标值——语义直白且防未来回归(M3 评审修正沿革)
     */
    // 首屏已成功拉取(loadInitial/resync Ok 置位,reset 复位):公开可观察,ChatPage 空态文案
    // 以此为门——首拉在飞期间列表区静默空白,不闪「还没有悄悄话」再被消息顶掉(2026-10-17
    // 真机反馈;微信语义:加载期不落空态,拿到空回包才确认真空)
    var initialLoaded: Boolean by mutableStateOf(false)
        private set

    /**
     * 首屏拉取(§7.4 ChatPage 进入触发):按 initialLoaded 判幂等(与 lastId 解耦),未拉过以空游标调
     * list 拿最近一页正序整表覆盖——覆盖语义含本人已发消息(服务端已落库,且必为最新一页尾部),覆盖安全;
     * 已拉过幂等返回(再进入的增量由 MainPager 切 Tab/下拉刷新触发补拉)。失败不置标志,下次触发可重试
     */
    suspend fun loadInitial(pager: Pager): ApiResult {
        if (initialLoaded) return ApiResult.Ok(JSONObject())
        val result = pull(pager, overwrite = true)
        if (result is ApiResult.Ok) {
            initialLoaded = true
        }
        return result
    }

    /**
     * 增量拉取(§7.4:切 Tab/下拉刷新):lastId 起服务端扫 id > lastId 升序返回,追加去重;
     * 首屏尚未成功拉到时(初拉失败的重试路径)退化按首屏覆盖拉,防本地乱序拼接
     */
    suspend fun loadIncrement(pager: Pager): ApiResult = pull(pager, overwrite = !initialLoaded)

    private suspend fun pull(pager: Pager, overwrite: Boolean): ApiResult {
        val result = pullMutex.withLock {
            val listResult = MessageApi.list(pager, if (overwrite) "" else lastId)
            if (listResult is ApiResult.Ok) {
                val infos = MessageInfo.listFrom(listResult.dataArray)
                if (overwrite) {
                    // 首屏覆盖(F6):按 id 对账合并而非整表替换——覆盖拉在飞时本端 send/sendWishes 可能
                    // 已 POST 落库并先入列(其服务端 id 必大于本页快照最大 id),整表替换会把它们驱逐,
                    // 而 lastId 已随覆盖推进、该区间永不重拉,消息凭空消失直至冷启动;保留 id 更大的
                    // 本端消息接在快照尾部(升序不破),下一轮增量会再拉到同 id 消息并去重
                    val serverMsgs = infos.map { toMsg(it) }
                    val maxServerId =
                        infos.maxOfOrNull { it.id.toLongOrNull() ?: Long.MIN_VALUE } ?: Long.MIN_VALUE
                    val localNewer = messages.filter { (it.id.toLongOrNull() ?: Long.MIN_VALUE) > maxServerId }
                    messages = serverMsgs + localNewer
                } else {
                    // 增量走归位合并:本端 send 不推游标后,回包含自己已入列条目(dedupe 掉)与对方并发迟到
                    // 的低 id 条目——后者须按 id 数值序插回正确位置,尾插会让时序错乱
                    appendDedupe(infos.map { toMsg(it) }, orderedMerge = true)
                }
                infos.forEach { advanceLastId(it.id) }
            }
            listResult
        }
        // M11 未读同步钩子(锁外,网络调用不进 pullMutex——HttpClient.silentRefresh 同款裁决):
        // 覆盖 loadInitial/loadIncrement 全部拉取路径(首拉/切 Tab/下拉/5s 轮询/WS 信号);send 系不经过
        // 此处(本端发送只增加对方未读,不改我方)。失败静默(只读降级 §11),下个触发点自愈
        if (result is ApiResult.Ok) {
            syncReadState(pager)
        }
        return result
    }

    /**
     * 发文本悄悄话(§7.4 写穿透):POST 成功才用回包消息(含服务端 id)入列。**不推进游标**(见 lastId
     * 注释:本端 id 顶游标会吞掉对方并发低 id 消息)——本条将由下轮增量拉回、appendDedupe 幂等去重;
     * 失败不追加,调用方 toast 后输入可重发(无脏数据)
     */
    suspend fun send(pager: Pager, text: String): ApiResult {
        val result = MessageApi.send(pager, text)
        if (result is ApiResult.Ok) {
            MessageInfo.from(result.data)?.let { appendDedupe(listOf(toMsg(it))) }
        }
        return result
    }

    /**
     * 发图片消息(M10,§7.4 写穿透同 send):POST send-image 成功才用回包消息入列。
     * **不推进游标**(同 send:本端 id 顶游标会吞掉对方并发低 id 消息)——本条由下轮增量拉回去重;
     * 失败不追加,调用方 toast 后预览图保留可重发
     */
    suspend fun sendImage(pager: Pager, imageUrl: String): ApiResult {
        val result = MessageApi.sendImage(pager, imageUrl)
        if (result is ApiResult.Ok) {
            MessageInfo.from(result.data)?.let { appendDedupe(listOf(toMsg(it))) }
        }
        return result
    }

    /**
     * 送出选中心愿(§7.4 写穿透):逐条 POST send-wishes,快照四字段 wishKey=wish.id/text=wish.title/
     * color=wish 主色 hex/icon=wish.icon 的 name(§5.2)。首错即停:某条失败立即停止余下,已成功发送的
     * 不回滚不重发(撤选已生效,剩余选中可重试补发,已送达的不重复发送);部分失败由调用方按
     * 「已发条数」toast(已发条数 = 传入条数 - 送出后 MoodStore.selectedIds 数,因成功一条撤选一条)
     */
    suspend fun sendWishes(pager: Pager, wishes: List<Wish>): ApiResult {
        if (wishes.isEmpty()) return ApiResult.Ok(JSONObject())
        for (wish in wishes) {
            val result = MessageApi.sendWishes(
                pager,
                wishKey = wish.id,
                text = wish.title,
                color = colorToHex(wish.colors.first()),
                icon = appIconName(wish.icon),
            )
            if (result is ApiResult.Ok) {
                MoodStore.toggle(wish.id)
                MessageInfo.from(result.data)?.let { appendDedupe(listOf(toMsg(it))) } // 不推游标,同 send
            } else {
                return result
            }
        }
        return ApiResult.Ok(JSONObject())
    }

    /** 登出/换号清内存(MainPager 接线调用),防上会话悄悄话残留 */
    fun reset() {
        messages = emptyList()
        lastId = ""
        initialLoaded = false
        unreadCount = 0
        markedCursor = ""
        chatVisible = false // 停留本页解绑→重绑场景由 ChatPage bindState effect 重断言(A4)
        partnerTyping = false // M13:typing 是纯瞬态位,登出/解绑即清(seq 不动,过期协程自灭无害)
        typingSeq = 0
        lastTypingSentAt = 0L
    }

    /** MessageInfo → ChatMsg:isMine 以 senderId 比对当前会话;kind=2 走快照字段,kind=3 图片
     *  (imageUrl=content 透传),其余按文本;createTime 透传(消息时间行/日期分组用,send/sendWishes/
     *  sendImage 走服务端回包 VO 天然有值);recalled 墓碑透传(M12,list/回包统一携带) */
    private fun toMsg(info: MessageInfo): ChatMsg {
        val mine = info.senderId == SessionStore.userId
        return when (info.kind) {
            MessageInfo.KIND_WISH -> ChatMsg(
                id = info.id,
                isMine = mine,
                text = "",
                kind = MsgKind.WISH,
                wishTitle = info.content,
                wishKey = info.wishKey,
                colorHex = info.color,
                iconName = info.icon,
                createTime = info.createTime,
                recalled = info.recalled,
            )
            MessageInfo.KIND_IMAGE -> ChatMsg(
                id = info.id,
                isMine = mine,
                text = "",
                kind = MsgKind.IMAGE,
                imageUrl = info.content,
                createTime = info.createTime,
                recalled = info.recalled,
            )
            else -> ChatMsg(
                id = info.id,
                isMine = mine,
                text = info.content,
                createTime = info.createTime,
                recalled = info.recalled,
            )
        }
    }

    /** 按 id 去重追加(并发触发/重试场景下同一条消息只入列一次;LazyColumn key 也不容重复)。
     *  orderedMerge=true 走归位合并:合并后按 id 数值序整体稳定排序,迟到低 id 消息插回正确位置
     *  (增量路径专用;send 尾插即最新,无需排序)。id 恒为服务端自增数字串,非数字兜底排最前(不可达) */
    private fun appendDedupe(list: List<ChatMsg>, orderedMerge: Boolean = false) {
        if (list.isEmpty()) return
        val known = HashSet<String>(messages.size + list.size)
        messages.forEach { known.add(it.id) }
        val fresh = ArrayList<ChatMsg>(list.size)
        list.forEach { if (known.add(it.id)) fresh.add(it) }
        if (fresh.isEmpty()) return
        messages = if (orderedMerge) {
            (messages + fresh).sortedBy { it.id.toLongOrNull() ?: Long.MIN_VALUE }
        } else {
            messages + fresh
        }
    }

    /** 推进游标到最大消息 id(id 为服务端自增数字串;非数字兜底字典序比对) */
    private fun advanceLastId(id: String) {
        if (id.isEmpty()) return
        if (lastId.isEmpty()) {
            lastId = id
            return
        }
        val cur = lastId.toLongOrNull()
        val next = id.toLongOrNull()
        val ahead = if (cur != null && next != null) next > cur else id > lastId
        if (ahead) lastId = id
    }

    // ---------- M11 未读红点 ----------

    /**
     * 进出聊天页上报(ChatPage LaunchedEffect 调,M11):置 true 主动 sync 一次——修「切 Tab 补拉在飞、
     * 本 effect 先完成导致红点没清」时序(补拉落态尾钩子会再补一刀,双保险 D6,缺一不可)
     */
    suspend fun setChatVisible(pager: Pager, visible: Boolean) {
        if (chatVisible == visible) return
        chatVisible = visible
        if (visible) syncReadState(pager)
    }

    /** 未读同步分派:停留本页标已读(红点清零)、离页刷未读数(红点现值);BOUND 才动(未绑定静默) */
    private suspend fun syncReadState(pager: Pager) {
        if (!SessionStore.isLoggedIn || CoupleStore.bindState != CoupleBindState.BOUND) return
        if (chatVisible) markRead(pager) else refreshUnread(pager)
    }

    /** POST read:guard lastId 越过 markedCursor 才发(服务端 GREATEST 另兜并发回退);失败静默
     *  不推进 markedCursor——下轮钩子/回前台重试自愈,驻留错值窗口 ≤ 下个成功触发点 */
    private suspend fun markRead(pager: Pager) {
        if (lastId.isEmpty() || !isNewer(lastId, markedCursor)) return
        val target = lastId
        val result = MessageApi.markRead(pager, target)
        if (result is ApiResult.Ok) {
            markedCursor = target
            unreadCount = 0
        }
    }

    /** GET unread:回读服务端真值(红点唯一事实源);失败静默保留旧值,不 toast(只读降级 §11) */
    private suspend fun refreshUnread(pager: Pager) {
        val result = MessageApi.unread(pager)
        if (result is ApiResult.Ok) {
            unreadCount = result.data.optInt("unread", 0)
        }
    }

    /** 数值序比较 a>b(advanceLastId 同款兜底:非数字字典序;空串 b 视为最旧) */
    private fun isNewer(a: String, b: String): Boolean {
        if (b.isEmpty()) return true
        val ca = a.toLongOrNull()
        val cb = b.toLongOrNull()
        return if (ca != null && cb != null) ca > cb else a > b
    }

    // ---------- M12 消息撤回 ----------

    /**
     * 撤回(§7.4 写穿透):POST 成功才本地置墓碑(服务端 CAS 幂等);失败不置位,调用方 toast
     * (1_003_003_001 超窗/1_003_003_000 非本人等文案由服务端回包携带)。回包为标量 true 经
     * toOk 归空对象,只判 Ok
     */
    suspend fun recall(pager: Pager, messageId: String): ApiResult {
        val result = MessageApi.recall(pager, messageId)
        if (result is ApiResult.Ok) markLocalRecalled(messageId)
        return result
    }

    /**
     * 全量重拉纠偏(M12):切 Tab 进聊天页触发——覆盖「我不在线时对方撤回」的漏更新。
     * 增量拉按 id > lastId 扫,拉不到已拉取老消息的字段变更,唯全量覆盖能纠偏;
     * 50 条量级成本可接受。成功置 initialLoaded(与 loadInitial 收敛同态,防首拉失败残留半态)
     */
    suspend fun resync(pager: Pager): ApiResult {
        val result = pull(pager, overwrite = true)
        if (result is ApiResult.Ok) initialLoaded = true
        return result
    }

    /**
     * WS 撤回信号入口(ChatPushStore 调,M12):本地置墓碑,不重拉——recall 信号携 messageId,
     * 数据在信号里(D-16 显式破例:增量拉拉不到老消息变更)。id 不在本地列表(未拉过/已清场)
     * 时 map 无命中即 no-op,墓碑由下次覆盖拉(resync/首拉)补齐
     */
    fun applyRecall(messageId: String) {
        if (messageId.isEmpty()) return
        markLocalRecalled(messageId)
    }

    /** 原位替换置墓碑:key=m.id 不变,LazyColumn 无位移仅本行重组合;map 幂等,重复信号/重复 POST 无副作用 */
    private fun markLocalRecalled(messageId: String) {
        messages = messages.map { if (it.id == messageId) it.copy(recalled = true) else it }
    }

    // ---------- M13 正在输入 ----------

    /** 对方「正在输入」过期窗(D-18 端上唯一收口):无停止信号,须大于上报节流 3s,连续输入不闪断 */
    const val TYPING_EXPIRE_MS = 4_000L

    /** 我方输入上报节流(D-18 节流在端上,服务端不做限流):3s < 过期窗 4s,连续打字状态不闪断 */
    private const val TYPING_SEND_INTERVAL_MS = 3_000L

    /** 对方是否正在输入(WS love-typing 信号置位;4s 过期由 ChatPage typingSeq 协程收口) */
    var partnerTyping by mutableStateOf(false)
        private set

    /**
     * typing 信号单调序号(ChatPage 过期协程的 LaunchedEffect key):新信号 seq++ 重挂过期协程,
     * 旧 delay 被取消(协程取消语义天然去竞态);[clearPartnerTyping] 只清位不动 seq 不重触发
     */
    var typingSeq by mutableIntStateOf(0)
        private set

    /** 我方上次上报时刻(3s 节流位,noteTyping 内部维护) */
    private var lastTypingSentAt = 0L

    /** WS 正在输入信号入口(ChatPushStore 调,M13):置位 + seq++,重复信号幂等(位恒 true) */
    fun applyPartnerTyping() {
        partnerTyping = true
        typingSeq++
    }

    /** 过期收口(ChatPage 过期协程调):只清位不动 seq;期间新信号已重挂协程,本协程已取消不会误清 */
    fun clearPartnerTyping() {
        partnerTyping = false
    }

    /**
     * 我方输入上报(ChatPage onValueChange 调,M13):登录+BOUND+停留本页才发,3s 节流;
     * fire-and-forget 失败静默——瞬态信号不值得重试,下次击键 3s 后自然再报(不落库无存储放大)
     */
    fun noteTyping(pager: Pager) {
        if (!SessionStore.isLoggedIn || CoupleStore.bindState != CoupleBindState.BOUND) return
        if (!chatVisible) return
        val now = nowEpochMs()
        if (now - lastTypingSentAt < TYPING_SEND_INTERVAL_MS) return
        lastTypingSentAt = now
        pager.lifecycleScope.launch {
            MessageApi.typing(pager)
        }
    }
}

// ---------- ChatPushStore:WS 触发器(D-16,§5.2 WS 通道) ----------

/**
 * ChatPushStore — WS 长连接触发器(D-16):连接管理 + 「对方有新消息」信号驱动的增量拉。
 * 设计:WS 只做纯信号,数据仍走 ChatStore.loadIncrement 的 lastId 增量拉(D-15 游标语义)——
 *  断线/漏推/未桥接端(iOS/ohos/H5 未注册原生模块,getModule=null)自动退回既有 5s 轮询,
 *  渐进降级永不丢消息。
 * 生命周期:MainPager 连接 effect 驱动(isLoggedIn+bindState+foregroundTick 三 key)——
 *  登录且 BOUND 才 connect(幂等),否则 disconnect(登出/解绑翻 key 即断);断线自愈由
 *  聊天页 5s 轮询循环体顺手 connect(同入口,握手轻量无风暴)。
 * 线程:事件回调经原生主线程转投(见 KRWebSocketModule.fire),common 侧 lifecycleScope.launch
 *  主线程起步,与 LaunchedEffect 同构;state 只在主线程读写,无并发问题。
 * 事件:无对外事件——对内消化,message 信号即触发 loadIncrement,失败静默轮询兜底(§11)
 */
internal object ChatPushStore {

    /** 服务端信号类型(MessageServiceImpl.MESSAGE_TYPE_LOVE_NEW 同串,§5.2 契约) */
    private const val TYPE_LOVE_NEW = "love-message-new"

    /** 撤回信号类型(M12,MessageServiceImpl.MESSAGE_TYPE_LOVE_RECALL 同串):携 messageId,
     *  端上本地置墓碑不重拉(增量拉拉不到老消息变更,纠偏兜底 = 切 Tab resync) */
    private const val TYPE_LOVE_RECALL = "love-message-recall"

    /** 正在输入信号类型(M13,MessageServiceImpl.MESSAGE_TYPE_LOVE_TYPING 同串):纯瞬态信号,
     *  content 单键占位不消费;无「停止输入」信号,端上 4s 过期唯一收口(D-18) */
    private const val TYPE_LOVE_TYPING = "love-typing"

    /** 连接态:普通 var 非快照状态——不驱动 UI,只做幂等判重(主线程读写见头注释) */
    private const val STATE_DISCONNECTED = 0
    private const val STATE_CONNECTING = 1
    private const val STATE_CONNECTED = 2
    private var state = STATE_DISCONNECTED

    /** 事件回调闭包持有的 pager 引用(connect 时刷新;MainPager 常驻页,生命周期=App 会话) */
    private var currentPager: Pager? = null

    /**
     * 连接(幂等,亦作断线自愈 ensure 入口):未桥接端 getModule=null 静默降级纯轮询;
     * CONNECTING/CONNECTED 早退——触发源密集(连接 effect 三 key + 5s 轮询循环体),判重全靠 state
     */
    fun connect(pager: Pager) {
        val module = pager.getModule<WebSocketModule>(WebSocketModule.MODULE_NAME) ?: return
        if (state != STATE_DISCONNECTED || !SessionStore.isLoggedIn) return
        val token = SessionStore.accessToken
        if (token.isEmpty()) return
        state = STATE_CONNECTING
        currentPager = pager
        module.connect(ApiConfig.wsUrl(pager), token) { onEvent(it) }
    }

    /** 主动断开(登出/解绑:连接 effect else 分支驱动;未桥接端无连接可言,静默) */
    fun disconnect(pager: Pager) {
        val module = pager.getModule<WebSocketModule>(WebSocketModule.MODULE_NAME) ?: return
        module.disconnect()
        state = STATE_DISCONNECTED
        currentPager = null
    }

    /**
     * 事件分发(原生主线程回调):open 置已连;closed/error 回未连等下一触发点重连;
     * message 解析 JsonWebSocketMessage,type=love-message-new 即触发一次增量拉——
     * 信号只触发,数据由 lastId 游标保证(D-15);非 JSON 文本帧(如 "pong")忽略
     */
    private fun onEvent(data: JSONObject?) {
        if (data == null) return
        when (data.optString("event")) {
            "open" -> state = STATE_CONNECTED
            "closed", "error" -> state = STATE_DISCONNECTED
            "message" -> {
                val pager = currentPager ?: return
                val text = data.optString("data")
                val type = try {
                    JSONObject(text).optString("type")
                } catch (_: Exception) {
                    ""
                }
                if (type == TYPE_LOVE_NEW) {
                    pager.lifecycleScope.launch {
                        ChatStore.loadIncrement(pager)
                    }
                } else if (type == TYPE_LOVE_RECALL) {
                    // content 是字符串化的 JSON(JsonWebSocketMessage.content:String,双层解析),取 messageId
                    val messageId = try {
                        JSONObject(JSONObject(text).optString("content")).optString("messageId")
                    } catch (_: Exception) {
                        ""
                    }
                    if (messageId.isNotEmpty()) {
                        pager.lifecycleScope.launch {
                            ChatStore.applyRecall(messageId)
                        }
                    }
                } else if (type == TYPE_LOVE_TYPING) {
                    // M13 正在输入纯信号:content 单键占位不消费;置位 + seq++ 由 ChatPage 过期
                    // 协程 4s 收口(D-18 无停止信号);非停留聊天页时置位无害(副标题仅聊天页可见)
                    pager.lifecycleScope.launch {
                        ChatStore.applyPartnerTyping()
                    }
                }
            }
        }
    }
}
