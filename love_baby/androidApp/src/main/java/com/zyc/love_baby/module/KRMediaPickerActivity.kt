package com.zyc.love_baby

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zyc.love_baby.module.KRFileModule

/**
 * KRMediaPickerActivity — 应用内媒体选择器(M24 十轮,微信式自建相册页;用户裁决「人家微信可以,
 * 人家咋做的」)——微信从不用系统选择器,自读媒体库自绘网格页,故既无文件管理器(DocumentsUI)
 * 的浏览/打开加载条,也不受系统相册 ACTION_PICK 仅图片之限,图/视频混选全设备体验一致。
 * 五至九轮一直在系统选择器内打转(照片选择器→系统相册→文件管理器三级回退),用户设备无
 * 照片选择器模块始终落到文件管理器吃加载条——天花板在"系统选择器"本身,本轮绕开它。
 * 实现:MediaStore 图片+视频两集合分别查询(模式固定免 LOWER 风险;DATE_ADDED DESC 归并取最新
 * MAX_ITEMS 条),RecyclerView 4 列网格 Glide 缩略图(视频帧 Glide 原生支持),视频格时长胶囊角标,
 * 单选即点即回(程序化 UI 零 XML 布局)。十二轮亮色微信式改版(白底细缝/4 列/按压反馈/状态栏
 * 反色)+部分授权适配——Android14 权限框「仅允许选中的照片」= READ_MEDIA_VISUAL_USER_SELECTED
 * 单独 granted,MediaStore 只回当时勾选项(真机实证「只有一张照片和一个视频」),顶栏下横幅
 * 一键重新请求升级全量授权(系统重弹「选择更多/全部允许」),全量授权后横幅隐藏自动刷新。
 * 权限:读媒体库需运行时申请——33+ READ_MEDIA_IMAGES/VIDEO(+READ_MEDIA_VISUAL_USER_SELECTED
 * 兼容 Android 14「仅选中照片」部分授权),33- READ_EXTERNAL_STORAGE;
 * **拒绝降级不封路**:KRPickImageActivity(MEDIA_MIXED)系统选择器三级链接管(五/九轮成果
 * 保留为降级层),连桥都起不来才按取消收场。
 * ⚠ 交付契约(挂起安全):复用 KRFileModule.onPickResult 静态收口——MODE_PICK_MEDIA 分支对
 * content:// 的落盘/mime 判型/时长体量回包零改动,common 侧 pickMedia 回调链零感知。任何退出
 * 路径必须**恰好交付一次**:选图=onPickResult(listOf(uri)),取消/系统返回=onPickResult(null),
 * 否则 common 侧 suspendCoroutine 永悬挂(mediaPicking 卡死);delivered 闸防取消/点选竞速双交付。
 * 十一轮:修首开即崩——lateinit adapter 字段曾被 RecyclerView.apply 内 `.also { adapter = it }`
 * 的内层属性遮蔽而永不赋值,媒体查询回主线程 notifyDataSetChanged 时 UninitializedPropertyAccess-
 * Exception 崩进程(真机 MIUI FATAL 实证);赋值挪出 apply 块落 Activity 方法作用域(见网格构建处注)。
 */
class KRMediaPickerActivity : AppCompatActivity() {

    private data class MediaItem(val uri: Uri, val isVideo: Boolean, val durationSec: Long)

    private val items = ArrayList<MediaItem>()
    private lateinit var adapter: ThumbAdapter
    private lateinit var banner: TextView
    private lateinit var emptyView: TextView
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 亮色微信式:白底 + 深色前景图标(26- 老系统导航栏保持默认深色防白底白图标)
        window.statusBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true
        if (Build.VERSION.SDK_INT >= 26) {
            window.navigationBarColor = Color.WHITE
            WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightNavigationBars = true
        }
        // 程序化 UI(零 XML):顶栏(标题+取消) + 部分授权横幅 + 4 列网格 + 空态文案
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(BG_COLOR)
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(BG_COLOR)
        }
        topBar.addView(
            TextView(this).apply {
                text = "图片和视频"
                setTextColor(TEXT_PRIMARY)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        topBar.addView(
            TextView(this).apply {
                text = "取消"
                setTextColor(TEXT_PRIMARY)
                textSize = 16f
                setPadding(dp(12), dp(6), 0, dp(6))
                setOnClickListener { deliver(null) }
            },
        )
        column.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(
            View(this).apply { setBackgroundColor(HAIRLINE) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)),
        )
        // Android14「仅允许选中的照片」部分授权:MediaStore 只回勾选项——横幅引导升级全量授权
        banner = TextView(this).apply {
            text = "仅能访问选中的照片和视频，点击允许访问全部"
            setTextColor(ACCENT)
            textSize = 13f
            setBackgroundColor(BANNER_BG)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
            setOnClickListener {
                ActivityCompat.requestPermissions(this@KRMediaPickerActivity, mediaPermissions(), REQUEST_PERMS)
            }
        }
        column.addView(banner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // ⚠ 十一轮闪退根因(真机 FATAL 实证):lateinit 字段赋值禁写 `adapter = ThumbAdapter().also
        //  { adapter = it }` 于 RecyclerView.apply 内——also 块里裸名 adapter 被内层接收者 RV 的同名
        //  属性遮蔽(赋给了 RV 自身),Activity 字段永不初始化,媒体查询回来 notifyDataSetChanged 即
        //  UninitializedPropertyAccessException 崩进程。赋值必须落 Activity 方法作用域,再显式装上。
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@KRMediaPickerActivity, 4)
            setBackgroundColor(BG_COLOR)
        }
        adapter = ThumbAdapter()
        grid.adapter = adapter
        column.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        emptyView = TextView(this).apply {
            text = "没有可发送的图片或视频"
            setTextColor(TEXT_HINT)
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        column.addView(
            emptyView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(column)
        when {
            hasFullMediaPermission() -> loadMediaAsync()
            hasPartialMediaPermission() -> {
                banner.visibility = View.VISIBLE
                loadMediaAsync()
            }
            else -> ActivityCompat.requestPermissions(this, mediaPermissions(), REQUEST_PERMS)
        }
    }

    // ---------- 权限:全量/部分/无 三态;拒绝降级系统选择器三级链(五/九轮成果为降级层) ----------

    private fun mediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            // Android 14「仅选中照片」部分授权形态:用户拒全量但选部分照片时本权限 granted
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** 全量读库(33+ 任一细分媒体权限 / 33- 传统存储权限)——MediaStore 可见设备全部媒体 */
    private fun hasFullMediaPermission(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** 部分授权(Android14「仅允许选中的照片」):MediaStore 只回用户勾选项(十二轮真机实证) */
    private fun hasPartialMediaPermission(): Boolean = !hasFullMediaPermission() &&
        Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMS) {
            return
        }
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            // 全量授权隐藏横幅;部分授权(升级被再次「仅选照片」或维持原状)保持横幅引导
            banner.visibility = if (hasFullMediaPermission()) View.GONE else View.VISIBLE
            loadMediaAsync()
        } else {
            // 拒绝/不再询问:降级系统选择器三级链,交付职责移交桥(含取消路径);桥起不来才取消收场
            try {
                startActivity(
                    Intent(this, KRPickImageActivity::class.java)
                        .putExtra(KRPickImageActivity.EXTRA_MEDIA_TYPE, KRPickImageActivity.MEDIA_MIXED),
                )
                finish()
            } catch (_: Exception) {
                deliver(null)
            }
        }
    }

    // ---------- 媒体库查询:图片/视频两集合归并(模式固定,全 API 级安全),后台线程 ----------

    private fun loadMediaAsync() {
        Thread {
            // uri / isVideo / (dateAdded, durationMs) —— 归并后按日期倒序取最新 MAX_ITEMS 条
            val merged = ArrayList<Triple<Uri, Boolean, Pair<Long, Long>>>()
            try {
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    while (c.moveToNext()) {
                        merged.add(
                            Triple(
                                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)),
                                false,
                                Pair(c.getLong(dateCol), 0L),
                            ),
                        )
                    }
                }
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.Video.Media.DURATION),
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        merged.add(
                            Triple(
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)),
                                true,
                                Pair(c.getLong(dateCol), if (durCol >= 0) c.getLong(durCol) else 0L),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                // 媒体库读失败:落空态文案(空列表),不崩不卡
            }
            merged.sortByDescending { it.third.first }
            val latest = merged.take(MAX_ITEMS).map { MediaItem(it.first, it.second, it.third.second / 1000) }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                items.clear()
                items.addAll(latest)
                adapter.notifyDataSetChanged()
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                if (items.isEmpty()) {
                    emptyView.text = if (banner.visibility == View.VISIBLE) {
                        "尚未选择照片\n点击上方横幅允许访问全部"
                    } else {
                        "没有可发送的图片或视频"
                    }
                }
            }
        }.start()
    }

    // ---------- 交付:恰好一次(见类注挂起安全) ----------

    private fun deliver(uris: List<Uri>?) {
        if (delivered) {
            return
        }
        delivered = true
        KRFileModule.onPickResult(uris)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        deliver(null) // 系统返回 = 取消(delivered 闸防与点选竞速双交付)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun formatDur(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

    /** 正方格:以宽定高(网格单元 1:1;四周 1dp margin 露出白底=微信式细缝;前景按压整格反馈) */
    private class SquareFrameLayout(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val w = View.MeasureSpec.getSize(widthSpec)
            super.onMeasure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            )
        }
    }

    private inner class ThumbViewHolder(cell: View, val thumb: ImageView, val badge: TextView) :
        RecyclerView.ViewHolder(cell)

    private inner class ThumbAdapter : RecyclerView.Adapter<ThumbViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbViewHolder {
            val cell = SquareFrameLayout(parent.context)
            cell.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) }
            // 前景按压反馈(mask 全格遮罩限定波纹范围;minSdk 23 支持foreground)
            cell.foreground = RippleDrawable(
                ColorStateList.valueOf(0x40000000),
                null,
                ColorDrawable(Color.WHITE),
            )
            val thumb = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cell.addView(thumb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val badge = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = GradientDrawable().apply {
                    setColor(0x99000000.toInt())
                    cornerRadius = dp(10).toFloat()
                }
            }
            cell.addView(
                badge,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END,
                ).apply { setMargins(0, 0, dp(6), dp(6)) },
            )
            return ThumbViewHolder(cell, thumb, badge)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ThumbViewHolder, position: Int) {
            val item = items[position]
            // Glide 原生支持视频 content uri 取帧;override 限解码尺寸防大图 OOM
            Glide.with(holder.thumb)
                .load(item.uri)
                .centerCrop()
                .override(320)
                .placeholder(ColorDrawable(PLACEHOLDER))
                .into(holder.thumb)
            holder.badge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            holder.badge.text = "▶ ${formatDur(item.durationSec)}"
            holder.itemView.setOnClickListener { deliver(listOf(item.uri)) }
        }
    }

    companion object {
        private const val REQUEST_PERMS = 2001
        /** 媒体库条数上限(DATE_ADDED DESC 截头,保最新;防超大媒体库全量入内存) */
        private const val MAX_ITEMS = 2000

        /** 亮色微信式配色(白底/深字/发丝线/蓝色动作色/横幅浅蓝底/缩略图占位灰) */
        private val BG_COLOR = Color.WHITE
        private val TEXT_PRIMARY = 0xFF1A1A1A.toInt()
        private val TEXT_HINT = 0xFF999999.toInt()
        private val HAIRLINE = 0xFFEEEEEE.toInt()
        private val ACCENT = 0xFF1677FF.toInt()
        private val BANNER_BG = 0xFFEAF3FF.toInt()
        private val PLACEHOLDER = 0xFFF0F0F0.toInt()
    }
}
