package com.zyc.love_baby.features.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.zyc.love_baby.base.setTimeout
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.features.couple.BindInputField
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.MoodStore
import com.zyc.love_baby.state.colorFromHex
import kotlinx.coroutines.launch

/** 心情名长度上限(与服务端 @Length(max=16) 一致;超长输入端上截断不报错) */
private const val MOOD_LABEL_MAX_LENGTH = 16

/** 选项数上限(与服务端 MoodOptionServiceImpl 上限一致,超限服务端 1_003_002_002;端上预检少一趟请求) */
private const val MOOD_OPTION_MAX = 20

/**
 * emoji 预设集(约 12 个,全部 Unicode 11 及以前——老系统字体可渲染,勿随手加新版字形;
 * H5/鸿蒙字体覆盖 M4 多端测试再核)。只影响上送的 emoji 字符串,服务端 varchar(16) 原样存
 */
private val emojiPresets = listOf("😄", "😊", "🥰", "😌", "😎", "🥺", "😭", "😤", "😠", "😴", "🤒", "🥳")

/**
 * 色板预设集(8 色,取仓内既有色值:5 档 legacy 配色 + 主题粉 fallback + 和好绿 + 拥抱橙)。
 * 选定后上送 hex 字符串,服务端 varchar(16) 原样存
 */
private val colorPresets = listOf("#FFB93C", "#FF8FA3", "#FF5A5A", "#FF8A5C", "#4EB087", "#6C8BFF", "#9B6BFF", "#8FA3BF")

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
    var label by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(emojiPresets.first()) }
    var colorHex by remember { mutableStateOf(colorPresets.first()) }
    var adding by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf("") }
    // 关浮层先清焦(同 LoginPanel.submit 焦点转移问题):浮层卸载时持焦输入框被移除,Android
    // 把窗口焦点转给待办页输入框(五页全驻留),Pager 滚去露出它 → 主界面乱滚
    val focusManager = LocalFocusManager.current
    fun dismiss() {
        focusManager.clearFocus()
        // 卸载迟一拍(机制详见 features.profile.FieldInputOverlay.dismiss):clearFocus 的原生 blur 走 setTimeout
        // 批处理异步落地,onDismiss 同回合执行会让浮层卸载与 blur 竞速——持焦输入框带焦点被移除
        // 时焦点转给待办页输入框,Pager 滚去露出它=主界面乱滚(2026-10-18 ProfileEditOverlay
        // 真机复现同源)。onDismiss 推迟一拍入同一 MessageQueue,按入队序必然排在 blur 之后,
        // 卸载时树中已无持焦视图
        pager.setTimeout(0) { onDismiss() }
    }

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

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { dismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // 吞点击防透传(铁律⑤):卡片 background+clickable 同节点,点卡片不落灰幕
            modifier = Modifier.padding(horizontal = 32.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { }
                .padding(horizontal = 18.dp, vertical = 18.dp),
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
                        .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp))
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
                    items(MoodStore.moodOptions.size) { index ->
                        val option = MoodStore.moodOptions[index]
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp))
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
                onValueChange = { label = it.take(MOOD_LABEL_MAX_LENGTH) },
                placeholder = "心情名(最多 $MOOD_LABEL_MAX_LENGTH 字)",
            )
            Spacer(modifier = Modifier.height(10.dp))
            // emoji 预设(横向单层滚动,跨轴于卡片纵向,铁律⑦;选中换底色不改结构,铁律③)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emojiPresets.size) { index ->
                    val selected = emojiPresets[index] == emoji
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) ThemeStore.current.brandStart else Color(0xFFF5F6FA),
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
                                if (selected) swatch.copy(alpha = 0.30f) else Color(0xFFF5F6FA),
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
}
