package com.zyc.love_baby.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.combinedClickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.aspectRatio
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.grid.GridCells
import com.tencent.kuikly.compose.foundation.lazy.grid.LazyVerticalGrid
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.ExperimentalMaterial3Api
import com.tencent.kuikly.compose.material3.ModalBottomSheet
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.state.formatMmss

/**
 * MediaPickerSheet — 聊天相册弹层(M24 十三轮,微信式;十四轮打磨:单选默认/长按进多选/分类下拉/
 * 顶栏 ✕+分类 icon/圆角收小/流畅性)。官方 material3 ModalBottomSheet 底部弹层 + LazyVerticalGrid
 * 4 列网格。
 * 选择语义(微信):弹层默认**单选**——点格子选中(再点取消),顶栏「发送(1)」;**长按任意格进入
 * 多选**——此后点格子=多选 toggle,序号圆标按选择顺序编号,上限 9(微信同款)。复开复位单选。
 * 分类(十四轮):顶栏右侧分类 icon → 下拉相册面板(「全部」+ 各相册文件夹[原生 bucket 回包],
 * 纯文字行+选中勾),切分类清空选择;标题随当前分类显「图片和视频」/相册名。
 * 数据流:打开时 queryMediaLibrary 桥拉媒体库(content:// 直出;缩略图零原生工作——KRImageAdapter
 * content 分支 Glide 直载降采样,视频帧 Glide 原生解)→ 发送时交回 ChatPage 走 copyMedia 落盘收口
 * (本组件零上传零网络,体量守卫在选中时拦截)。
 * 流畅性(十四轮):格子恒挂浅灰底(加载前不白闪)+ 网格 contentType 稳定 + key=uri 复用 +
 * 下拉列表 LazyColumn 按 bucket 数动态定高。
 * 权限:queryMediaLibrary 未授权原生自动挂系统申请(拒绝回错文案落空态,不封路不降级跳页)。
 * 复开语义:媒体库每次打开静默刷新(有缓存不闪加载态),选中/多选/分类每次打开复位。
 */
/** 单条媒体库条目(queryMediaLibrary 回包逐项;图片 duration 恒 0) */
private data class LibraryMedia(val uri: String, val kind: String, val duration: Int, val size: Long)

/** 多选上限(微信同款 9) */
private const val MAX_SELECT = 9

/** 格子底色(缩略图加载前占位,避免白底网格上白闪) */
private val CELL_PLACEHOLDER = Color(0xFFF2F3F5)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MediaPickerSheet(
    visible: Boolean,
    pager: Pager,
    maxVideoBytes: Long,
    onToast: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: (List<String>) -> Unit,
) {
    val theme = ThemeStore.current
    var loading by remember { mutableStateOf(false) }
    var emptyMsg by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<LibraryMedia>() }
    val selected = remember { mutableStateListOf<String>() } // 按 选择顺序 记 content uri
    var multiSelect by remember { mutableStateOf(false) } // 长按进入多选(微信语义,复开复位)

    fun loadLibrary() {
        if (loading) {
            return
        }
        if (items.isEmpty()) {
            loading = true // 首开显加载态;复开静默刷新不闪(网格保留至新回包整体替换)
        }
        emptyMsg = ""
        val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
        if (module == null) {
            loading = false
            emptyMsg = "当前端暂不支持选择照片/视频"
            return
        }
        module.queryMediaLibrary { result ->
            loading = false
            val code = result?.optInt("code", -1) ?: -1
            val data = result?.optString("data").orEmpty()
            if (code != 0) {
                emptyMsg = result?.optString("msg").orEmpty().ifEmpty { "读取相册失败,请重试" }
                return@queryMediaLibrary
            }
            val parsed = mutableListOf<LibraryMedia>()
            try {
                val arr = JSONArray(data)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    parsed.add(
                        LibraryMedia(
                            uri = o.optString("uri").orEmpty(),
                            kind = o.optString("kind") ?: "image",
                            duration = o.optInt("duration"),
                            size = o.optLong("size"),
                        ),
                    )
                }
            } catch (_: Exception) {
            }
            if (parsed.isEmpty()) {
                emptyMsg = "没有可发送的照片/视频"
            }
            items.clear()
            items.addAll(parsed)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            selected.clear() // 复开清选中并复位单选(微信语义:弹层关闭即弃选)
            multiSelect = false
            loadLibrary()
        }
    }

    /** 单选模式点格(radio:选 A 换 B;再点已选=取消) */
    fun pickSingle(item: LibraryMedia) {
        if (selected.contains(item.uri)) {
            selected.clear()
        } else {
            selected.clear()
            selected.add(item.uri)
        }
    }

    /** 多选 toggle(视频体量拦截 + 上限 9;序号=选择顺序) */
    fun toggleMulti(item: LibraryMedia) {
        if (selected.contains(item.uri)) {
            selected.remove(item.uri)
            return
        }
        if (item.kind == "video" && item.size > maxVideoBytes) {
            onToast("视频过大(超过 16MB),无法选择")
            return
        }
        if (selected.size >= MAX_SELECT) {
            onToast("最多选择 $MAX_SELECT 张照片/视频")
            return
        }
        selected.add(item.uri)
    }

    /** 长按进入多选(微信语义:长按那张同时选中) */
    fun enterMulti(item: LibraryMedia) {
        if (!multiSelect) {
            multiSelect = true
        }
        toggleMulti(item)
    }

    ModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        containerColor = AppTheme.CardBg,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        // ⚠ dismissOnDrag 必须保持默认 false(十五轮真机实锤):官方实现该开关为 true 时把
        // detectVerticalDragGestures 挂在整个 sheet Surface 上(material3/ModalBottomSheet.kt
        // drag 层),父层垂直拖拽手势与内容区滚动容器(LazyVerticalGrid)抢事件 → 网格完全
        // 滚不动;官方 demo 可滚动内容即默认 false。关闭走 ✕/点遮罩
        dismissOnDrag = false,
        modifier = Modifier.clip(RoundedCornerShape(8.dp, 8.dp)),
    ) {
        Box {
            Column {
                // 顶栏(十四轮微信式,十六轮删分类入口):左 ✕ 关闭 / 中标题 / 右 发送(N)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = AppTheme.PageHPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.clickable { onDismiss() }.padding(6.dp),
                    ) {
                        AppIcon(AppIconType.Close, AppTheme.TextPrimary, Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "图片和视频",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    if (selected.isEmpty()) {
                        Text("发送", fontSize = 15.sp, color = AppTheme.TextTertiary)
                    } else {
                        Text(
                            "发送(${selected.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.brandStart,
                            modifier = Modifier.clickable { onSend(selected.toList()) },
                        )
                    }
                }
                // 内容区(定高网格,弹层总高≈顶栏+定高+安全垫;数据三态:加载/空错/网格)
                Box(
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        Text("加载中…", fontSize = 14.sp, color = AppTheme.TextTertiary)
                    } else if (emptyMsg.isNotEmpty()) {
                        Text(emptyMsg, fontSize = 14.sp, color = AppTheme.TextTertiary)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(
                                items.size,
                                key = { items[it].uri },
                                contentType = { "media" },
                            ) { index ->
                                val item = items[index]
                                // 重组优化(RULE-B 拆订阅粒度,kuikly-recomposition-analyzer 方案 3):
                                // 派生本格选中序号——其他格子 toggle 时本格派生值不变即不重组;
                                // 直读 selected.indexOf 会让全部可见格随任一 toggle 级联重组
                                val selIndex by remember(item.uri) {
                                    derivedStateOf { selected.indexOf(item.uri) }
                                }
                                Box(
                                    modifier = Modifier.aspectRatio(1f)
                                        .background(CELL_PLACEHOLDER) // 加载前灰底不白闪(十四轮流畅性)
                                        .combinedClickable(
                                            onClick = {
                                                if (multiSelect) toggleMulti(item) else pickSingle(item)
                                            },
                                            onLongClick = { enterMulti(item) },
                                        ),
                                ) {
                                    // content:// 直出:KRImageAdapter Glide 分支降采样(视频帧 Glide 原生解)
                                    Image(
                                        painter = rememberAsyncImagePainter(item.uri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (selIndex >= 0) {
                                        // 选中整格蒙灰(微信语义;badge 之后渲染压其上)
                                        Box(
                                            modifier = Modifier.fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.25f)),
                                        )
                                    }
                                    if (item.kind == "video") {
                                        Text(
                                            "▶ ${formatMmss(item.duration)}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                    if (selIndex >= 0) {
                                        if (multiSelect) {
                                            // 多选:序号圆标(选择顺序编号,1 起)
                                            Box(
                                                modifier = Modifier.align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(20.dp)
                                                    .background(theme.brandStart, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "${selIndex + 1}",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        } else {
                                            // 单选:对勾圆标
                                            Box(
                                                modifier = Modifier.align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(20.dp)
                                                    .background(theme.brandStart, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AppIcon(
                                                    AppIconType.Check,
                                                    Color.White,
                                                    Modifier.size(13.dp),
                                                )
                                            }
                                        }
                                    } else {
                                        // 未选中:半透明暗圆占位(对齐微信空圈位置)
                                        Box(
                                            modifier = Modifier.align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(20.dp)
                                                .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // 底部安全垫(手势条避让,弹层贴底不顶内容)
                Spacer(modifier = Modifier.height(navBarSafePaddingDp()))
            }
        }
    }
}
