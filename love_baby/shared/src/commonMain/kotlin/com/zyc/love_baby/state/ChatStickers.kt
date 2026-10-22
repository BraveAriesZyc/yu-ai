package com.zyc.love_baby.state

/**
 * ChatStickers — 悄悄话贴纸字典(M23,kind=6):key ↔ emoji 内置映射。
 * 放 state/ 层(非 features/chat):ChatStore.previewOf 通知摘要要用 emojiOf,
 * 与 allWishes 愿望字典留在 state/ 同一先例;ChatPage 贴纸面板/气泡反向引用无层级问题。
 * 用法:发送面板遍历 [ORDER] 取 key;渲染/摘要一律 [emojiOf] 取 emoji。
 * ⚠ append-only 铁则:只增不改不删——content 落库为 key 明文(快照式同 wishKey,服务端不校验
 * 字典),历史消息渲染完全依赖端上字典;改已有 key 的映射会让两端历史消息显示漂移。
 * 未知 key(未来版本新增/跨版本窗口)emojiOf 占位 "🤍",不崩不空。
 */
internal object ChatStickers {

    /** key → emoji 映射(渲染与发送面板共用;展示顺序不看这里,看 [ORDER]) */
    private val EMOJI: Map<String, String> = mapOf(
        "hug" to "🤗",
        "kiss" to "😘",
        "love" to "🥰",
        "heart" to "❤️",
        "blush" to "😊",
        "laugh" to "😂",
        "cry" to "😭",
        "angry" to "😠",
        "shocked" to "😲",
        "think" to "🤔",
        "sleepy" to "😴",
        "thumbsup" to "👍",
        "ok" to "👌",
        "clap" to "👏",
        "wave" to "👋",
        "pray" to "🙏",
        "flex" to "💪",
        "party" to "🎉",
        "cake" to "🎂",
        "rose" to "🌹",
        "star" to "⭐",
        "moon" to "🌙",
        "doge" to "🐶",
        "ghost" to "👻",
    )

    /** 贴纸面板展示顺序(独立于 EMOJI,便于只调排序不动映射;新 key 同时登记两处) */
    val ORDER: List<String> = listOf(
        "hug",
        "kiss",
        "love",
        "heart",
        "blush",
        "laugh",
        "cry",
        "angry",
        "shocked",
        "think",
        "sleepy",
        "thumbsup",
        "ok",
        "clap",
        "wave",
        "pray",
        "flex",
        "party",
        "cake",
        "rose",
        "star",
        "moon",
        "doge",
        "ghost",
    )

    /** key → emoji;未知 key 占位 🤍(append-only 铁则兜底,见类注释) */
    fun emojiOf(key: String): String = EMOJI[key] ?: "🤍"
}
