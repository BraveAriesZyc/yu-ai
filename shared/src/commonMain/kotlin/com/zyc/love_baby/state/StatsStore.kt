package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi

/**
 * StatsStore — 全局恋爱统计快照单例(SessionStore/CoupleStore 单例范式,「统计真数据化」里程碑)
 * 用法:读 StatsStore.bindDays/messageCount/todoCount/moodCount(首页「在一起(天)」与我的页统计卡);
 *      刷新由 MainPager 接线:登录后首拉 + 回前台(F8)effect;登出/解绑清场调 reset
 * 依赖:androidx.compose.runtime 快照状态;UserApi.getStats(HttpClient 401 单飞刷新兜底)
 * 事件:refresh 成功才落值,失败静默保留旧值(只读降级,§7.4 失败不拦 UI);
 *      统计数值变化 = 消息/待办/心情写穿透成功后由 MainPager 顺手 refresh(见 MainPager 接线点)
 * 功能:
 *  - 服务端 GET /love/user/stats 拆包:bindDays/messageCount/todoCount/moodCount 四真数
 *    (bindDays=bind_time 自然日含当天;计数=当前 couple 归属、逻辑删不计;未绑定全 0)
 *  - 首页未绑定态显示 "--"(UI 侧按 CoupleStore.bindState 分支,本 Store 恒持数字)
 */
internal object StatsStore {

    /** 在一起天数(含绑定当天;未绑定/清场后为 0) */
    var bindDays by mutableIntStateOf(0)
        private set

    /** 悄悄话消息总数(含文本与心愿) */
    var messageCount by mutableIntStateOf(0)
        private set

    /** 待办总数 */
    var todoCount by mutableIntStateOf(0)
        private set
    /** 心情记录总数 */
    var moodCount by mutableIntStateOf(0)
        private set

    /**
     * 拉取恋爱统计(登录后首拉/回前台/写穿透后顺手),失败静默保留旧值。
     * 挂 MainPager 既有效应,不在 UI 层自持(五页驻留,同组合同步可见)
     */
    suspend fun refresh(pager: Pager) {
        val result = UserApi.getStats(pager)
        if (result is ApiResult.Ok) {
            val data = result.data
            bindDays = data.optInt("bindDays", 0)
            messageCount = data.optInt("messageCount", 0)
            todoCount = data.optInt("todoCount", 0)
            moodCount = data.optInt("moodCount", 0)
        }
    }

    /** 登出/解绑清场:四项归零(与 ChatStore/TodoStore.reset 同范式,重复清幂等) */
    fun reset() {
        bindDays = 0
        messageCount = 0
        todoCount = 0
        moodCount = 0
    }
}
