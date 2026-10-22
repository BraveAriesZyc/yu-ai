package com.zyc.love_baby.view

import android.content.Context
import android.graphics.Typeface
import com.tencent.kuikly.core.render.android.expand.component.KRTextFieldView

/**
 * KRDefaultTypefaceTextFieldView — 默认字体输入框视图(KRTextFieldView 子类,密码框占位符字体修复)
 * 依赖:引擎 2.29 render-android KRTextFieldView(open class 可覆写,placeholder=native hint);
 *      注册点 KuiklyRenderActivity.registerExternalRenderView(本类以 KRTextFieldView.VIEW_NAME
 *      同名后写覆盖内置注册)
 * 用法:仅注册零直接引用——框架建输入视图时按 viewName 取 creator(KuiklyRenderView:1097-1100),
 *      全 App 所有单行输入框实际都落到本类,setProp 全量透传 super,仅密码分支追加复位
 * 入参/事件:同父类 setProp(propKey, propValue),无自有对外接口
 * 功能:
 *  - 根因:Android TextView.setInputType 对 password 型 inputType 会把整个字段(含 hint)的
 *    typeface 无条件换成等宽字体(平台固有行为,官方通行修法即「setInputType 之后立刻
 *    setTypeface 回默认」);Kuikly 渲染层 placeholder 就是原生 hint(KRTextFieldView.setPlaceHolder:
 *    hint = propValue),hint 与正文共用字体 → 注册「设置密码…」/登录「密码」占位符渲染成
 *    等宽字形(真机反馈仅密码框异常:手机号/验证码 keyboardType=number 不触发平台换字,吻合)。
 *    引擎 2.29 的 prop 表无 typeface/fontFamily 分支(KRTextFieldView:184-237,fontFamily 落
 *    else 丢弃),commonMain 侧无解,必须宿主视图层修
 *  - 修法:super.setProp 应用 keyboardType="password"(inputType 已写、平台换字已发生)后
 *    立即 typeface=Typeface.DEFAULT 复位——顺序天然保证;key 用字面量 "keyboardType"
 *    (父类 private const KEYBOARD_TYPE 不可引用,值同串双源勿漂移);复位幂等,prop 重投无害
 *  - ⚠️ 升级红线:覆盖依赖「delegate.registerExternalRenderView 在内置注册之后回调 +
 *    viewExportCreator 同名后写覆盖」(KuiklyRenderViewBaseDelegator:476/KuiklyRenderView:1082
 *    2.29 事实)——引擎升级若改注册时序,覆盖静默失效(症状回退=密码占位符变等宽),升级后
 *    必须回归验证;未来加「密码可见切换」(直接改 inputType)须仍走本 setProp 分支或切换后补复位
 */
open class KRDefaultTypefaceTextFieldView(context: Context, softInputMode: Int?) :
    KRTextFieldView(context, softInputMode) {

    override fun setProp(propKey: String, propValue: Any): Boolean {
        val handled = super.setProp(propKey, propValue)
        if (handled && propKey == "keyboardType" && propValue == "password") {
            typeface = Typeface.DEFAULT
        }
        return handled
    }
}
