package com.zyc.love_baby.features.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.BackHandler
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppIconTile
import com.zyc.love_baby.components.AppMenuDivider
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppMenuRow
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.UserProfile
import com.zyc.love_baby.features.couple.BoundPartnerCard
import com.zyc.love_baby.features.couple.copyToPasteboard
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.permission.openPermissionPage
import com.zyc.love_baby.features.profile.PROFILE_WHO_MINE
import com.zyc.love_baby.features.profile.openProfilePage
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.LoveConfigStore
import com.zyc.love_baby.state.StatsStore
import kotlinx.coroutines.launch

/**
 * MinePage — 我的页:主题渐变头部 + 悬浮统计(恋爱数据) + 情侣空间卡(M2) + 主题外观切换 + 设置分组
 * 用法:MinePage(pager, profile, onUnbindClick, onLogoutClick)。M3(F6,§7.7)起本页无浮层/无拉取:
 *      profile 由 MainPager 提升持有并拉取(user/get),「解绑」「退出登录」仅回调上报,浮层统一挂在
 *      MainPageImpl 根 Box(后于底栏组合,盖住底栏且遮罩挡触摸)——浮层陷入本页 LazyColumn 时
 *      滚动项仍可穿透点击,且被底栏压住,均需根级承载
 * 依赖:ThemeStore/AppIcons/components 通用组件;RouterModule(打开组件列表页);
 *      UserProfile(MainPager 回填);CoupleStore/BoundPartnerCard(情侣空间,M2)
 * 事件:主题圆点点击 → ThemeStore.switch + persist;「组件列表」→ openPage("componentList");
 *      「帮助与客服」→ 复制客服微信号(M23 后台业务配置动态下发,LoveConfigStore 具名取值器,
 *      缺键走内置兜底)+ toast(页内自持);「关于我们」→ onAboutClick()(信息卡
 *      MineAboutOverlay 挂 MainPager 根);右上角「详情」/资料区整块 → openProfilePage(mine);
 *      「解绑」→ onUnbindClick();「退出登录」→ onLogoutClick()(动作由 MainPager 收口)
 * 功能:
 *  - 资料区(§7.7):昵称/头像展示由 profile 参数给定;资料编辑收进详情页(2026-10-18 用户裁决:
 *    右上角「编辑」改「详情」跳 profilePage,详情页 QQ 式行内编辑,各行点击直接改)
 *  - 情侣空间卡:已绑定时展示伴侣昵称/绑定时间 + 解绑入口(二次确认浮层在 MainPager);
 *    未绑定时本页被 BindingGuide 整屏覆盖不可达,故不设未绑定分支
 */
@Composable
internal fun MinePage(
    pager: Pager,
    profile: UserProfile?,
    onUnbindClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onAboutClick: () -> Unit = {},
    listScrollEnabled: Boolean = true,
) {
    // 根容器收背景(浮层在 MainPager 根 Box,需盖住整页;背景与 LazyColumn 原本同色,视觉无变化)
    Box(modifier = Modifier.fillMaxSize().background(ThemeStore.current.bg)) {
        // userScrollEnabled:浮层(解绑/退出/关于等 MainPager 根级)开着时关本页列表滚动——
        // Kuikly 遮罩的 clickable 只吃 tap 不吃拖拽,背景 LazyColumn 原生滚动仍收事件
        // (2026-10-17 真机实证「浮层开着还能滚背景」),官方开关是最干净的阻断通道
        LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = listScrollEnabled) {
            item {
                HeaderWithStats(
                    pager = pager,
                    profile = profile,
                )
            }
            item {
                Spacer(modifier = Modifier.height(38.dp))
                // 情侣空间卡(M2):伴侣信息 + 解绑入口;解绑确认浮层挂在 MainPager 根 Box
                if (CoupleStore.bindState == CoupleBindState.BOUND) {
                    BoundPartnerCard(pager, onUnbindClick = onUnbindClick)
                    Spacer(modifier = Modifier.height(14.dp))
                }
                ThemePickerCard(pager)
                Spacer(modifier = Modifier.height(14.dp))
                // 设置分组(2026-10-18:原四功能入口——纪念日提醒/每日打卡/我们的相册/恋爱清单
                // 迁至首页快捷入口区,图标/配色/命名同源;此处仅余设置向菜单)
                AppMenuGroup(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                    AppMenuRow(
                        icon = AppIconType.Service,
                        iconTint = Color(0xFF34C77B),
                        iconBg = Color(0xFFE6F8EF),
                        title = "帮助与客服",
                        // M23 动态化:客服微信号迁后台「业务配置」(customer_service_wechat),
                        // LoveConfigStore 具名取值器读快照,缺键/停用/拉取失败回内置兜底值;
                        // 点击时读当前值(非组合期闭包),后台改值即时生效
                        onClick = {
                            val wechat = LoveConfigStore.contactWechat
                            copyToPasteboard(pager, wechat) {
                                toastCouple(pager, "微信号 $wechat 已复制，去微信添加吧")
                            }
                        },
                    )
                    AppMenuDivider()
                    AppMenuRow(
                        icon = AppIconType.Info,
                        iconTint = Color(0xFF6C7BFF),
                        iconBg = Color(0xFFEDEFFF),
                        title = "关于我们",
                        // 信息卡浮层挂 MainPager 根(铁律 14:Tab 页页内浮层盖不住浮岛底栏)
                        onClick = onAboutClick,
                    )
                    AppMenuDivider()
                    AppMenuRow(
                        icon = AppIconType.Bell,
                        iconTint = Color(0xFFFF4D6A),
                        iconBg = Color(0xFFFFE9ED),
                        title = "权限管理",
                        // M22:权限状态页(通知权限查询/一键开启/跳系统设置)
                        onClick = { openPermissionPage(pager) },
                    )
                    AppMenuDivider()
                    // 组件列表:通用组件 demo 画廊
                    AppMenuRow(
                        icon = AppIconType.Components,
                        iconTint = Color(0xFFFF7A45),
                        iconBg = Color(0xFFFFF3ED),
                        title = "组件列表",
                        onClick = { pager.openLovePage("componentList") },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 退出登录:仅回调上报,确认浮层与 logout/clear 动作由 MainPager 根级收口(F6,§5.2)
                Box(
                    modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)
                        .fillMaxWidth()
                        .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                        .clickable { onLogoutClick() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("退出登录", color = AppTheme.Danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(AppTheme.BottomBarAvoidExtra + navBarSafePaddingDp())) // 底部导航避让(M18 统一常数,原 100dp 与首页 96dp 不一致)
            }
        }
    }
}

/** 头部:资料区(§7.7)读 profile 参数回填昵称与首字头像;右上角「详情」钮与资料区整块
 *  同跳资料详情页(profilePage);统计卡「心愿」列可点进心愿时间线页(需 pager 路由,未绑定时点击不响应) */
@Composable
private fun HeaderWithStats(pager: Pager, profile: UserProfile?) {
    val nickname = profile?.nickname?.trim().orEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        // 渐变头部
        AppGradientHeader(height = 138.dp, topSpacing = 20.dp) {
            // 头像(双层圆环):avatar 非空走远程图(AppAvatar 圆角裁切,加载中半透白底透出
            // 充当占位),空走首字(文件服务里程碑,上传链路在详情页 uploadAvatarAndUpdate)
            // 资料区整块可点进资料详情页(2026-10-18 profilePage 入口):头像+昵称包内层 Row,
            // 右上角「详情」钮保持兄弟独立热区(2026-10-18 用户裁决由「编辑」改跳详情页,
            // 编辑浮层收进详情页);副标题接真 bindDays(原写死「520 天」假数据,主界面必 BOUND bindDays>0)
            Row(
                modifier = Modifier.weight(1f)
                    .clickable { openProfilePage(pager, PROFILE_WHO_MINE) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(58.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(29.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppAvatar(
                        if (nickname.isEmpty()) "宝" else nickname.take(1),
                        size = 50.dp,
                        bgColor = Color.White.copy(alpha = 0.35f),
                        fontSize = 20.sp,
                        avatarUrl = profile?.avatar.orEmpty(),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                AppHeaderTitle(
                    if (nickname.isEmpty()) "宝贝 & 我" else nickname,
                    "在一起的第 ${StatsStore.bindDays} 天,依然心动",
                    titleSize = 19.sp,
                    subtitleSize = 12.sp,
                )
            }
            // 详情入口(2026-10-18 用户裁决:右上角「编辑」改「详情」跳资料详情页,详情页
            // QQ 式行内编辑各行点击直接改;MainPager 根级编辑浮层同轮拆除)
            Box(
                modifier = Modifier.background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .clickable { openProfilePage(pager, PROFILE_WHO_MINE) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("详情", color = Color.White, fontSize = 12.sp)
            }
        }
        // 悬浮统计卡(统计真数据化:三项全真,StatsStore 由 MainPager 拉取,数值变化写穿透后刷新)。
        // 「心愿」列可点进时间线页(messageCount 口径保留在接口,端上第一卡按产品口径改心愿);
        // 未绑定不响应(wish-list 需 couple 上下文,点了也只是失败态)
        AppStatBar(
            items = listOf(
                AppStatItem(
                    "${StatsStore.wishCount}",
                    "心愿",
                    onClick = {
                        if (CoupleStore.bindState == CoupleBindState.BOUND) pager.openLovePage("wishTimeline")
                    },
                ),
                AppStatItem("${StatsStore.todoCount}", "待办"),
                AppStatItem("${StatsStore.moodCount}", "心情"),
            ),
            valueSize = 18.sp,
            dividerHeight = 26.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = 22.dp)
                .padding(horizontal = AppTheme.PageHPadding),
        )
    }
}

/**
 * ThemePickerCard — 主题外观:5 个主题圆点,点击即时切换并持久化
 */
@Composable
private fun ThemePickerCard(pager: Pager) {
    val theme = ThemeStore.current
    Column(
        modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("主题外观", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text("· ${theme.name}", color = AppTheme.TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemeStore.all.forEach { t ->
                val selected = t.id == theme.id
                // 选中环:嵌套视图画环 + 背景常驻未选中切透明
                // (环/填充各占一个原生视图,同链双 background 互相覆盖;
                //  条件增删背景节点会残留上一次的方色块——原生属性无 detach 清理)
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(
                            if (selected) t.brandStart else Color.Transparent,
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(if (selected) 28.dp else 26.dp)
                            .background(
                                if (selected) AppTheme.CardBg else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(if (selected) 22.dp else 26.dp)
                                .background(t.brandBrush, RoundedCornerShape(if (selected) 11.dp else 13.dp))
                                .clickable {
                                    ThemeStore.switch(t)
                                    ThemeStore.persist(pager)
                                },
                        )
                    }
                }
            }
        }
    }
}

// ---------- 关于我们(M18 死入口接轻功能) ----------

/** 产品版本号(与 androidApp build.gradle versionName 同步维护;Kuikly commonMain 读不到 BuildConfig) */
private const val APP_VERSION_NAME = "1.0.0"

/**
 * MineAboutOverlay — 「关于我们」信息卡:纯展示零网络,版本号 + 产品说明 + 知道了收口
 * 用法:MainPager 根级组合(M18):if (showMineAbout) MineAboutOverlay(onDismiss = { showMineAbout = false })
 * 依赖:AppTheme(brandBrush/CardBg/TextSecondary/TextTertiary)/AppIconTile/AppIcon;
 *      骨架同全屏浮层惯例(黑幕 0.45 + 居中白卡 20dp 圆角 + 空 clickable 吞点击)
 * 事件:卡外点击/「知道了」→ onDismiss()
 * 功能:黑幕盖底栏由挂载位置保证(后于底栏组合,根级浮层惯例同 AppConfirmOverlay);卡片吞点击防误关
 */
@Composable
internal fun MineAboutOverlay(onDismiss: () -> Unit) {
    // 返回键关闭浮层(2026-10-22 浮层返回键接线,同 ChatImagePreviewOverlay)
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 48.dp)
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { } // 吞卡片内点击,不触发卡外 dismiss
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 品牌图标:主题渐变圆角底 + 心(brandBrush 挂主题实例,随 ThemeStore.current 切换)
            AppIconTile(AppIconType.Heart, size = 56.dp, bgBrush = ThemeStore.current.brandBrush, corner = 18.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text("LoveBaby 情侣互动", color = AppTheme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("版本 $APP_VERSION_NAME", color = AppTheme.TextTertiary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "和另一半在这里记待办、写心情、说悄悄话、攒照片、点恋爱清单——把每一天的小日子都好好存起来。",
                color = AppTheme.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(22.dp))
            // 知道了(铁律 14/15 胶囊三层写法:外层 Box 容器 + 内层 Box 载 background/padding + Text 子节点)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .background(ThemeStore.current.brandBrush, RoundedCornerShape(22.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 44.dp, vertical = 10.dp),
                ) {
                    Text("知道了", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
