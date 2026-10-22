package com.zyc.love_baby.components

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BridgeModule
import com.zyc.love_baby.network.ApiResult

/**
 * AppFeedback — 全局反馈收口(toast / 剪贴板 / 日期格式化,宿主桥单源)
 * 用法:AppFeedback.toast(pager, "已发布");AppFeedback.toastResult(pager, result);
 *      AppFeedback.copyToPasteboard(pager, code) { toast(pager, "已复制") };
 *      AppFeedback.formatBindTime(pager, epochMs)
 * 依赖:BridgeModule(base,toast/copyToPasteboard/dateFormatter)——一律可空 getModule 降级,
 *      禁 acquireModule 双抛(throwRuntimeError 内部 setTimeout(1) 再抛一次,try/catch 接不住
 *      直接崩 APP——铁律⑦;原 toastAuth 走 Utils.bridgeModule 即 acquireModule 路径,并入本件
 *      后统一改可空 getModule,消掉该隐患,语义不变)
 * 事件:无
 * 功能:
 *  - toast/toastResult 吸收 couple(toastCouple/toastCoupleResult)与 auth(toastAuth/
 *    toastAuthResult)两套逐字同构映射(§5.1 对照表:业务错误 toast msg,空串兜底「操作失败,
 *    请稍后再试」;登录失效 toast;网络异常 toast;不崩)——features 两域同名原件改薄壳委托,
 *    既有调用方 import 不动
 *  - copyToPasteboard:复制文本到宿主剪贴板(可空 getModule 降级,失败静默不崩),onCopied
 *    收尾回调(复制成功提示由调用方给)
 *  - formatBindTime:epoch 毫秒 → "yyyy-MM-dd"(宿主 dateFormatter,设备时区);时间未回传/
 *    模块不可用给「时间未知」占位,不造假数据
 */
internal object AppFeedback {

    fun toast(pager: Pager, message: String) {
        try {
            pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)?.toast(message)
        } catch (_: Exception) {
        }
    }

    /** ApiResult → toast(§5.1 对照表:业务错误 toast msg;网络异常 toast;不崩) */
    fun toastResult(pager: Pager, result: ApiResult) {
        when (result) {
            is ApiResult.Ok -> {}
            is ApiResult.BizError -> toast(pager, result.msg.ifEmpty { "操作失败,请稍后再试" })
            ApiResult.Unauthorized -> toast(pager, "登录已失效,请重新登录")
            ApiResult.NetworkError -> toast(pager, "网络异常,请稍后再试")
        }
    }

    fun copyToPasteboard(pager: Pager, text: String, onCopied: () -> Unit = {}) {
        try {
            // 桥缺失(getModule=null)时不写入也不回调 onCopied——「已复制」提示必须真复制过
            val mod = pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME) ?: return
            mod.copyToPasteboard(text)
            onCopied()
        } catch (_: Exception) {
        }
    }

    fun formatBindTime(pager: Pager, epochMs: Long): String {
        if (epochMs <= 0) return "时间未知"
        return try {
            pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)
                ?.dateFormatter(epochMs, "yyyy-MM-dd")
                ?.takeIf { it.isNotEmpty() } ?: "时间未知"
        } catch (_: Exception) {
            "时间未知"
        }
    }
}
