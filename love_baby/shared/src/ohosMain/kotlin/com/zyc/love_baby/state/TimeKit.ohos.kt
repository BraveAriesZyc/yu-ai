package com.zyc.love_baby.state

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

/** ohos actual:posix clock_gettime 实时钟毫秒(UTC epoch ms,见 TimeKit.nowEpochMs;
 *  KBA 工具链 Windows 可编,本文件已经 :shared:compileKotlinOhosArm64 实证) */
@OptIn(ExperimentalForeignApi::class)
internal actual fun nowEpochMs(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME, ts.ptr)
    ts.tv_sec.toLong() * 1000L + ts.tv_nsec.toLong() / 1_000_000L
}
