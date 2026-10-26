package com.zyc.love_baby.features.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.BackHandler
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppOverlayCard
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.runAfterBlur
import com.zyc.love_baby.features.couple.BindInputField
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.LoveConfigStore
import com.zyc.love_baby.state.MoodStore
import com.zyc.love_baby.state.colorFromHex
import kotlinx.coroutines.launch

/** 心情名长度上限(与服务端 @Length(max=16) 一致;超长输入端上截断不报错) */
private const val MOOD_LABEL_MAX_LENGTH = 16

/** 选项数上限(与服务端 MoodOptionServiceImpl 上限一致,超限服务端 1_003_002_002;端上预检少一趟请求) */
private const val MOOD_OPTION_MAX = 20

/**
 * MoodOptionManageOverlay — 心情字典管理浮层(M3.5 D-14):couple 私有心情选项的增删面板。
 * 用法:MoodOptionManageOverlay(pager, onDismiss),由 MainPager 根 Box 挂载(照 NicknameEditOverlay,
 *      F6:灰幕盖全屏含底栏,页内滚动项不吞浮层触摸);MoodPage 选择器「＋」芯片经 onOpenManage 打开
 * 依赖:MoodStore(字典与选中态)/BindInputField/BindPrimaryButton(couple 域既有件);MoodApi 经 Store 走透
 * 事件:✕ 删除 → MoodStore.deleteOption(乐观移除+失败回滚,toast 收口;删的是选中项时清
 *      MoodStore.selectedOptionId);「添加」→ MoodStore.addOption(服务端先,回包带 id 才落态,
 *      防 TodoStore.add 同款脏 key);灰幕/「完成」→ onDismiss
 * 功能:已有选项列表(固定高 LazyColumn,超 4 行滚动;空字典显示空态提示)+ 添加表单
 *      (label 输入 ≤16 字 + emoji 预设 LazyRow + 色板预设 LazyRow + 添加钮)。
 *      删除确认省略:删错可原参数重加回,不设二次确认(退出登录/解绑才配二次确认);
 *      历史月历不受删除影响——记录渲染走快照列(D-14),提示语已向用户声明
 * 铁律对齐:灰幕+卡片 background+clickable 同节点吞点击防透传(⑤);所有 clickable 均带背景
 *      (透明底也算,命中测试以背景视图为准);选中态一律换色不增删背景节点(③);
 *      LazyRow/LazyColumn 横向/纵向各只一层,无同向嵌套(⑦)
 */
@Composable
internal fun MoodOptionManageOverlay(
    pager: Pager,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // M25 预设服务端化(LoveConfigStore 两 KV,后台「业务配置」页维护):兜底值与原内置
    // 12+8 完全一致,无配置/坏 JSON 时行为不变。重组间只取一次——取值器逐次解析 JSON,
    // 勿下放到下方 items lambda 反复解析
    val emojiPresets = LoveConfigStore.moodEmojiPresets
    val colorPresets = LoveConfigStore.moodColorPresets
    var label by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(emojiPresets.first()) }
    var colorHex by remember { mutableStateOf(colorPresets.first()) }
    var adding by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf("") }
    // 关浮层收口(铁律 16):components.runAfterBlur 单源——浮层卸载时持焦输入框被移除,不先
    // 清焦+迟一拍,Android 会把窗口焦点转给待办页输入框(五页全驻留),Pager 滚去露出它
    // → 主界面乱滚(2026-10-18 ProfileEditOverlay 真机复现同源;机制详见该件 KDoc)
    val focusManager = LocalFocusManager.current
    fun dismiss() {
        pager.runAfterBlur(focusManager) { onDismiss() }
    }

    // 返回键关闭浮层(2026-10-22 浮层返回键接线):走灰幕同款 dismiss(清焦+迟一拍卸载,铁律 16)
    BackHandler { dismiss() }

    fun delete(id: String) {
        // 单飞写:增删同时在飞会让回滚快照互相踩,一次只放一个写请求
        if (adding || deletingId.isNotEmpty()) return
        deletingId = id
        scope.launch {
            val result = MoodStore.deleteOption(pager, id)
            deletingId = ""
            if (result is ApiResult.Ok) {
                // 删的是当前选中项:选中态同步清空,防止选择器悬空选中已删项
                if (MoodStore.selectedOptionId == id) {
                    MoodStore.selectedOptionId = ""
                }
                toastCouple(pager, "已删除")
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    fun add() {
        if (adding || deletingId.isNotEmpty()) return
        val name = label.trim()
        if (name.isEmpty()) {
            toastCouple(pager, "先给心情起个名字吧")
            return
        }
        if (MoodStore.moodOptions.size >= MOOD_OPTION_MAX) {
            toastCouple(pager, "心情选项最多 $MOOD_OPTION_MAX 个")
            return
        }
        adding = true
        scope.launch {
            val result = MoodStore.addOption(pager, name, emoji, colorHex)
            adding = false
            if (result is ApiResult.Ok) {
                label = "" // 清名不清 emoji/色板:连续添加时保留上次配色偏好
                toastCouple(pager, "已添加,选它记录今天吧")
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    AppOverlayCard(
        onDismiss = { dismiss() },
        // busy 不传(原灰幕无在飞闸:添加/删除失败 toast 即回,关闭恒可用,语义保真)
        cardOuterPadding = PaddingValues(horizontal = 32.dp),
        cardPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        // 标题行 + 完成(透明底 clickable 同节点,铁律⑤命中测试)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("管理心情", color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { dismiss() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("完成", color = ThemeStore.current.brandStart, fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "心情选项两人共享;删除不影响已记录的历史",
            color = AppTheme.TextTertiary,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 已有选项:固定高 LazyColumn(行高 ~40 + 间距 6,4 行封顶滚动;
        // 卡片 Column 非滚动容器,单层纵向滚动无同向嵌套,铁律⑦)
        if (MoodStore.moodOptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(AppTheme.InputBg, RoundedCornerShape(12.dp))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有心情选项,下面添加一个吧", color = AppTheme.TextTertiary, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .height((MoodStore.moodOptions.size * 46 + 6).coerceAtMost(190).dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // key 按 id(官方 L2):删中间项时其余行按身份复用,不再位置错位/状态串行
                items(MoodStore.moodOptions.size, key = { MoodStore.moodOptions[it].id }) { index ->
                    val option = MoodStore.moodOptions[index]
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(AppTheme.InputBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.emoji, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            option.label,
                            color = AppTheme.TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        // 删除钮(透明底 clickable 同节点;在飞行该行换文案「…」不改结构)
                        Box(
                            modifier = Modifier.size(26.dp)
                                .background(Color.Transparent, RoundedCornerShape(13.dp))
                                .clickable { delete(option.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (deletingId == option.id) "…" else "✕",
                                color = AppTheme.TextTertiary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("添加心情", color = AppTheme.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        BindInputField(
            value = label,
            onValueChange = { label = it },
            placeholder = "心情名(最多 $MOOD_LABEL_MAX_LENGTH 字)",
            maxLength = MOOD_LABEL_MAX_LENGTH, // 渲染层拒超限(官方禁 onValueChange take 截断,光标 bug)
        )
        Spacer(modifier = Modifier.height(10.dp))
        // emoji 预设(横向单层滚动,跨轴于卡片纵向,铁律⑦;选中换底色不改结构,铁律③)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(emojiPresets.size) { index ->
                val selected = emojiPresets[index] == emoji
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) ThemeStore.current.brandStart else AppTheme.InputBg,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { emoji = emojiPresets[index] }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emojiPresets[index], fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // 色板预设(同上单层横向;选中=同色淡光晕底,常驻双层结构只换外层色,铁律③)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colorPresets.size) { index ->
                val hex = colorPresets[index]
                val swatch = colorFromHex(hex)
                val selected = hex == colorHex
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(
                            if (selected) swatch.copy(alpha = 0.30f) else AppTheme.InputBg,
                            RoundedCornerShape(17.dp),
                        )
                        .clickable { colorHex = hex }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(22.dp)
                            .background(swatch, RoundedCornerShape(11.dp)),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        BindPrimaryButton(text = "添 加", submitting = adding) { add() }
    }
}
