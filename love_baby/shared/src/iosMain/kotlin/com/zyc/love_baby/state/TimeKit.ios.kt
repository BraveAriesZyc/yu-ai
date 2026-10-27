package com.zyc.love_baby.state

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

/** iOS actual:posix clock_gettime 实时钟毫秒(UTC epoch ms,见 TimeKit.nowEpochMs;
 *  iOS 目标在 Windows 被 cocoapods 插件禁用,本文件待 macOS 首次构建编译核) */
@OptIn(ExperimentalForeignApi::class)
internal actual fun nowEpochMs(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME, ts.ptr)
    ts.tv_sec.toLong() * 1000L + ts.tv_nsec.toLong() / 1_000_000L
}
