package com.zyc.love_baby.state

/** Android actual:System 毫秒墙钟(与旧 kotlin.time.Clock.System.now() 同为 UTC epoch ms,见 TimeKit.nowEpochMs) */
internal actual fun nowEpochMs(): Long = System.currentTimeMillis()
