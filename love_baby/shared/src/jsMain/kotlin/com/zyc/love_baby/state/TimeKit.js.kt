package com.zyc.love_baby.state

/** js actual:Date.now() 毫秒墙钟(UTC epoch ms,见 TimeKit.nowEpochMs) */
internal actual fun nowEpochMs(): Long = kotlin.js.Date.now().toLong()
