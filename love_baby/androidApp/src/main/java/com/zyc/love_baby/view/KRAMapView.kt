package com.zyc.love_baby.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import kotlin.math.abs
import org.json.JSONObject

/**
 * KRAMapView — 位置页原生动态地图视图(M27,首个自建原生 RenderView):
 * FrameLayout 容器实现 IKuiklyRenderViewExport,内嵌高德 3dmap MapView 子视图(容器化是
 * 刻意为之:MapView.onDestroy/onResume 系 final,无法在 MapView 子类上覆写接口默认实现,
 * 容器层导出 + 子视图直调生命周期是唯一干净路线,也是 KR 视图导出的惯例形态)。
 * common 侧 base/AMapNativeView(viewName "HRAMapView") 经 callMethod 驱动,
 * 全页地图 + 双人头像 Marker。
 * 依赖:com.amap.api:3dmap:10.0.600(Maven Central);Android 平台 key 在 AndroidManifest
 *      meta-data(未配 key 瓦片空白不崩,marker/采集/推送照常);隐私合规在 KRApplication
 *      (updatePrivacyShow/updatePrivacyAgree 必须先于本类任何调用)
 * 用法:KuiklyRenderActivity.registerExternalRenderView 注册(⚠ creator 按位置传参,尾随
 *      lambda 会绑到末位 shadowExportCreator 上);iOS/鸿蒙/H5 未注册,common 侧整页降级
 * 方法(call 分发,参数 JSON 字符串):
 *  - updatePoints {"points":[{role,lat,lng,avatarUrl,initial,colorStart,colorEnd,textColor}]}:
 *    增量落 marker(新建/setPosition/图标指纹变了才重绘);本轮缺席的 role 移除
 *    (TA 关共享/退后台过期 → marker 消失);lat/lng 已是 GCJ-02,原生直用
 *  - fitMarkers {}:两点取 LatLngBounds 包络,单点 newLatLngZoom;地图未量宽 post 重试;
 *    顺带清除回看标注(回到视野 = 清回看,M28)
 *  - focusPoint {"lat":..,"lng":..,"color":"#RRGGBB"}:回看标注(M28 地点列表点击)——品牌色
 *    圆点(白描边)落位 + animateCamera 飞 16 级;标注不进 markers map(不参与增量生命周期)
 * 视觉/动效(2026-10-25 打磨):我方 marker 附 GPS 精度圈(半透明品牌色圆,随 accuracy
 *      半径联动);marker 位移 600ms 线性插值平滑滑行(跨城 >0.02° 直接跳变防横屏长动画);
 *      相机 fit 走 animateCamera 顺滑过渡;内置缩放钮关闭(双指捏合缩放保留)
 * 图标:头像 URL → Glide asBitmap+CircleCrop 异步加载(loading 先文字圆兜底,失败定格
 *      文字圆);空 URL → Canvas 渐变文字圆;统一白描边,marker 按 role 持引用增量更新
 * 生命周期:MapView.onCreate 程序化起步(构造时);onResume/onPause 挂宿主 Activity
 *      LifecycleObserver(单 Activity 一页模型,Activity 前后台=页面可见性);onDestroy 走
 *      框架销毁链(渲染层 view 移除时多态派发),内部直调 MapView.onDestroy 释放高德资源
 */
class KRAMapView(context: Context) : FrameLayout(context), IKuiklyRenderViewExport {

    /** 高德地图子视图(MATCH_PARENT 铺满 Kuikly 分配的 frame) */
    private val mapView = MapView(context)

    /** 地图控制器(mapView.onCreate 后可用;构造即调 onCreate,恒非空) */
    private val aMap: AMap

    /** role("self"/"partner") → marker(增量更新/缺席移除) */
    private val markers = HashMap<String, Marker>()

    /** 我方 GPS 精度圈(半透明品牌色圆,随 accuracy 半径联动;仅 role=self 有) */
    private var selfCircle: Circle? = null

    /** 回看标注 marker(M28 地点列表点击;不在 markers map,不参与 updatePoints 增删) */
    private var focusMarker: Marker? = null

    /** marker 平滑移动动画表(marker.id → 在飞插值;新目标接管/销毁时取消) */
    private val markerAnimators = HashMap<String, ValueAnimator>()

    /** role → 当前图标数据指纹(URL|initial|三色),变了才重绘图标(位置更新不触发重绘) */
    private val iconKeys = HashMap<String, String>()

    /** 头像 URL → 已加载圆图(页内存活 2-3 个条目,无需 LRU) */
    private val avatarCache = HashMap<String, Bitmap>()

    /** 地图已销毁(onDestroy 幂等护栏;后续 onResume/onPause/call/Glide 回调全部静默) */
    private var destroyed = false

    init {
        mapView.onCreate(null)
        addView(mapView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        aMap = mapView.map
        aMap.uiSettings.apply {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            // 内置缩放钮(右下角 +/-)关闭:双指捏合/双击缩放保留,悬浮 UI 只留页面自绘层
            isZoomControlsEnabled = false
        }
        // Activity 前后台 → MapView.onResume/onPause(DefaultLifecycleObserver 观察宿主;
        // context.activity 由框架扩展解析,极端场景 null 则放弃观察,地图照常渲染)
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!destroyed) mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                if (!destroyed) mapView.onPause()
            }
        })
    }

    /**
     * 销毁(框架渲染层 view 移除链派发;FrameLayout 无同名方法,此处是唯一实现无覆写冲突):
     * MapView.onDestroy 释放高德资源,marker/缓存清场;destroyed 护栏挡后续一切回调
     */
    override fun onDestroy() {
        if (destroyed) return
        destroyed = true
        markerAnimators.values.forEach { it.cancel() }
        markerAnimators.clear()
        markers.clear()
        iconKeys.clear()
        avatarCache.clear()
        selfCircle = null
        focusMarker = null
        mapView.onDestroy()
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        when (method) {
            METHOD_UPDATE_POINTS -> {
                params?.let { updatePoints(it) }
                return ""
            }
            METHOD_FIT_MARKERS -> {
                fitMarkers()
                return ""
            }
            METHOD_FOCUS_POINT -> {
                params?.let { focusPoint(it) }
                return ""
            }
        }
        return super.call(method, params, callback)
    }

    /**
     * 增量落点:每个 role 建/移/移 marker,图标按指纹懒重绘;坐标 (0,0) 跳过(与
     * ShareServiceImpl.validateCoordinate 同口径,null-island 不上屏)
     */
    private fun updatePoints(params: String) {
        if (destroyed) return
        val points = try {
            JSONObject(params).optJSONArray("points")
        } catch (_: Exception) {
            null
        } ?: return
        val seen = HashSet<String>()
        for (i in 0 until points.length()) {
            val o = points.optJSONObject(i) ?: continue
            val role = o.optString("role")
            if (role.isEmpty()) continue
            val lat = o.optDouble("lat", 0.0)
            val lng = o.optDouble("lng", 0.0)
            if (lat == 0.0 && lng == 0.0) continue
            seen.add(role)
            applyPoint(
                role = role,
                lat = lat,
                lng = lng,
                accuracy = o.optDouble("accuracy", 0.0),
                avatarUrl = o.optString("avatarUrl"),
                initial = o.optString("initial"),
                colorStart = parseColor(o.optString("colorStart"), DEFAULT_COLOR_START),
                colorEnd = parseColor(o.optString("colorEnd"), DEFAULT_COLOR_END),
                textColor = parseColor(o.optString("textColor"), Color.WHITE),
            )
        }
        // 本轮未出现的 role 移除(TA 关共享由 common 层下发单点列表实现「对方 marker 消失」)
        markers.keys.filter { it !in seen }.toList().forEach { role ->
            markers.remove(role)?.remove()
            iconKeys.remove(role)
            if (role == ROLE_SELF) {
                selfCircle?.remove()
                selfCircle = null
            }
        }
    }

    private fun applyPoint(
        role: String,
        lat: Double,
        lng: Double,
        accuracy: Double,
        avatarUrl: String,
        initial: String,
        colorStart: Int,
        colorEnd: Int,
        textColor: Int,
    ) {
        val pos = LatLng(lat, lng)
        val marker = markers[role]
        if (marker == null) {
            markers[role] = aMap.addMarker(
                MarkerOptions()
                    .position(pos)
                    .anchor(0.5f, 0.5f)
                    // 先文字圆兜底即刻可见,URL 头像异步到位后换图
                    .icon(BitmapDescriptorFactory.fromBitmap(buildAvatarBitmap(null, initial, colorStart, colorEnd, textColor)))
            )
        } else {
            moveMarker(marker, pos)
        }
        if (role == ROLE_SELF) {
            updateSelfCircle(pos, accuracy, colorStart)
        }
        val iconKey = "$avatarUrl|$initial|$colorStart|$colorEnd|$textColor"
        if (iconKeys[role] == iconKey) return
        iconKeys[role] = iconKey
        resolveIcon(role, avatarUrl, initial, colorStart, colorEnd, textColor)
    }

    /**
     * marker 平滑滑向新点(600ms 线性插值,微信共享实时位置观感):小位移(_GPS 抖动米级)
     * 逐帧插值柔和跟随,跨城大位移(>0.02°≈2km)直接跳变防横穿屏幕长动画;在飞动画被
     * 新目标接管(先取消); <0.000001°(≈0.1m)视为原地抖动忽略。动画每帧只 setPosition,
     * 纯原生主线程 ValueAnimator,不经 compose 管线
     */
    private fun moveMarker(marker: Marker, target: LatLng) {
        val from = marker.position
        val delta = abs(target.latitude - from.latitude) + abs(target.longitude - from.longitude)
        if (delta < MIN_MOVE_DELTA) return
        markerAnimators.remove(marker.id)?.cancel()
        if (delta > JUMP_DELTA) {
            marker.position = target
            return
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MARKER_ANIM_MS
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                if (destroyed) {
                    va.cancel()
                    return@addUpdateListener
                }
                val t = va.animatedValue as Float
                marker.position = LatLng(
                    from.latitude + (target.latitude - from.latitude) * t,
                    from.longitude + (target.longitude - from.longitude) * t,
                )
            }
        }
        markerAnimators[marker.id] = anim
        anim.start()
    }

    /**
     * 我方精度圈:半透明品牌色圆随 GPS accuracy 半径联动(30–300m 夹紧,0=未报精度给
     * 默认 40m);Circle 是地面叠加层,渲染在 marker 之下不挡头像
     */
    private fun updateSelfCircle(pos: LatLng, accuracyM: Double, brandColor: Int) {
        val radius = (if (accuracyM <= 0.0) ACC_DEFAULT_M else accuracyM).coerceIn(ACC_MIN_M, ACC_MAX_M)
        val circle = selfCircle
        if (circle == null) {
            selfCircle = aMap.addCircle(
                CircleOptions()
                    .center(pos)
                    .radius(radius)
                    .fillColor((brandColor and 0x00FFFFFF) or 0x14000000)
                    .strokeColor((brandColor and 0x00FFFFFF) or 0x33000000)
                    .strokeWidth(1f)
            )
        } else {
            circle.center = pos
            circle.radius = radius
        }
    }

    /**
     * 图标解析:URL 空 → 文字圆同步上;有 URL → 缓存命中直接上,未命中 Glide 异步加载
     * (到位后指纹仍匹配才 setIcon,防快速切换/复用错图;失败定格文字圆不重试——
     * OSS 公共读失败多为瞬时网络,下一轮图标指纹变更才有机会重载)
     */
    private fun resolveIcon(role: String, avatarUrl: String, initial: String, colorStart: Int, colorEnd: Int, textColor: Int) {
        val setIcon: (Bitmap?) -> Unit = { bmp ->
            markers[role]?.setIcon(BitmapDescriptorFactory.fromBitmap(buildAvatarBitmap(bmp, initial, colorStart, colorEnd, textColor)))
        }
        if (avatarUrl.isEmpty()) {
            setIcon(null)
            return
        }
        avatarCache[avatarUrl]?.let {
            setIcon(it)
            return
        }
        setIcon(null)
        try {
            Glide.with(this)
                .asBitmap()
                .load(avatarUrl)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        if (destroyed) return
                        avatarCache[avatarUrl] = resource
                        if (iconKeys[role]?.startsWith("$avatarUrl|") == true) {
                            setIcon(resource)
                        }
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) = Unit

                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        // 定格文字圆(resolveIcon 进场已 set 文字圆,此处无需动作)
                    }
                })
        } catch (_: Exception) {
            // Glide 异常(线程/生命周期边角)定格文字圆,不崩
        }
    }

    /**
     * 头像 Marker 位图:白描边圆底 + 内圆(头像位图或渐变文字圆)。
     * Glide circleCrop 出的是圆图(四角透明),缩放铺满内圆后四角透出白描边 = 头像自带白圈
     */
    private fun buildAvatarBitmap(avatar: Bitmap?, initial: String, colorStart: Int, colorEnd: Int, textColor: Int): Bitmap {
        val density = resources.displayMetrics.density
        val size = (MARKER_SIZE_DP * density).toInt().coerceAtLeast(24)
        val border = (MARKER_BORDER_DP * density).toInt().coerceAtLeast(2)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f
        val innerRadius = radius - border
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // 白描边圆底
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
        if (avatar != null) {
            // 头像圆裁铺满内圆(circleCrop 圆图取 min 边 = 内圆直径,居中画)
            val left = cx - innerRadius
            val top = cy - innerRadius
            canvas.save()
            val clip = Path()
            clip.addCircle(cx, cy, innerRadius, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawBitmap(avatar, null, RectF(left, top, left + innerRadius * 2, top + innerRadius * 2), paint)
            canvas.restore()
        } else {
            // 渐变文字圆(ChatPage 文字头像同构配色:我=主题渐变,TA=品牌洗底)
            paint.shader = LinearGradient(cx, cy - innerRadius, cx, cy + innerRadius, colorStart, colorEnd, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, innerRadius, paint)
            paint.shader = null
            paint.color = textColor
            paint.textSize = size * 0.42f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            canvas.drawText(initial.take(1).ifEmpty { "爱" }, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        }
        return out
    }

    /**
     * 回到视野:两点包络(150dp 边距),单点 15 级缩放;地图未量宽(首帧 fit)post 重试——
     * newLatLngBounds 在 map 未 layout 时抛 IllegalStateException,捕获兜底不崩
     */
    private fun fitMarkers() {
        if (destroyed || markers.isEmpty()) return
        // 回到视野 = 清回看(回实时语义;先清再 fit,标注不参与包络计算)
        focusMarker?.remove()
        focusMarker = null
        if (mapView.width <= 0 || mapView.height <= 0) {
            post { fitMarkers() }
            return
        }
        try {
            if (markers.size == 1) {
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(markers.values.first().position, DEFAULT_SINGLE_ZOOM))
                return
            }
            val builder = LatLngBounds.Builder()
            markers.values.forEach { builder.include(it.position) }
            aMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    builder.build(),
                    (FIT_PADDING_DP * resources.displayMetrics.density).toInt(),
                )
            )
        } catch (_: Exception) {
            // 相机计算边角异常(坐标越界等)静默,保持当前视野
        }
    }

    /** 颜色串 → Int(#RRGGBB/#AARRGGBB;解析失败回默认) */
    private fun parseColor(text: String, fallback: Int): Int = try {
        Color.parseColor(text)
    } catch (_: Exception) {
        fallback
    }

    /**
     * 回看标注(M28 地点列表点击):移除旧标注 → 品牌色圆点(白描边)落位 → 相机飞 16 级。
     * 标注不进 markers map(不参与 updatePoints 增删/缺席移除);fitMarkers(回到视野)
     * 顺带清除。坐标 (0,0) 跳过(与 updatePoints 同口径)
     */
    private fun focusPoint(params: String) {
        if (destroyed) return
        val o = try {
            JSONObject(params)
        } catch (_: Exception) {
            null
        } ?: return
        val lat = o.optDouble("lat", 0.0)
        val lng = o.optDouble("lng", 0.0)
        if (lat == 0.0 && lng == 0.0) return
        focusMarker?.remove()
        focusMarker = aMap.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(buildFocusBitmap(parseColor(o.optString("color"), DEFAULT_COLOR_START))))
        )
        try {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), FOCUS_ZOOM))
        } catch (_: Exception) {
            // 相机计算边角异常静默,标注已落位
        }
    }

    /** 回看标注位图:白描边 + 品牌色实心圆点(区别于头像 marker 的轻量纯色小点) */
    private fun buildFocusBitmap(brandColor: Int): Bitmap {
        val density = resources.displayMetrics.density
        val size = (FOCUS_SIZE_DP * density).toInt().coerceAtLeast(12)
        val border = (FOCUS_BORDER_DP * density).toInt().coerceAtLeast(2)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = brandColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - border, paint)
        return out
    }

    companion object {
        const val METHOD_UPDATE_POINTS = "updatePoints"
        const val METHOD_FIT_MARKERS = "fitMarkers"
        const val METHOD_FOCUS_POINT = "focusPoint"

        /** 注册名(common 侧 AMapNativeView.viewName 同串双源,勿单改一边) */
        const val VIEW_NAME = "HRAMapView"

        /** 我方 role(精度圈挂它;common 侧 buildPointsJson 同串契约) */
        const val ROLE_SELF = "self"

        private const val MARKER_SIZE_DP = 44f
        private const val MARKER_BORDER_DP = 3f
        private const val FIT_PADDING_DP = 150f
        private const val DEFAULT_SINGLE_ZOOM = 15f

        /** marker 平滑移动:时长/大位移跳变阈值(0.02°≈2.2km)/原地抖动忽略阈值(≈0.1m) */
        private const val MARKER_ANIM_MS = 600L
        private const val JUMP_DELTA = 0.02
        private const val MIN_MOVE_DELTA = 0.000001

        /** 精度圈半径夹紧(m):GPS 未报精度给 40,过小不可见过大糊屏 */
        private const val ACC_DEFAULT_M = 40.0
        private const val ACC_MIN_M = 30.0
        private const val ACC_MAX_M = 300.0

        /** 回看标注(M28):点径 16dp / 白描边 3dp / 相机落位缩放级 */
        private const val FOCUS_SIZE_DP = 16f
        private const val FOCUS_BORDER_DP = 3f
        private const val FOCUS_ZOOM = 16f

        private val DEFAULT_COLOR_START = 0xFFFF9A9E.toInt()
        private val DEFAULT_COLOR_END = 0xFFFAD0C4.toInt()
    }
}
