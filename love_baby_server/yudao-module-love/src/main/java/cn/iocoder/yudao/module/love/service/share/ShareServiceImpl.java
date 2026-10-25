package cn.iocoder.yudao.module.love.service.share;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryPartnerVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryReportReqVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatterySnapshotVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationLatestRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPartnerVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPlaceListRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPlaceVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPointVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationReportReqVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationTrailRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppShareSettingRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppShareSettingUpdateReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveBatterySnapshotDO;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveLocationPlaceDO;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveLocationSnapshotDO;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveShareSettingDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.share.LoveBatterySnapshotMapper;
import cn.iocoder.yudao.module.love.dal.mysql.share.LoveLocationPlaceMapper;
import cn.iocoder.yudao.module.love.dal.mysql.share.LoveLocationSnapshotMapper;
import cn.iocoder.yudao.module.love.dal.mysql.share.LoveShareSettingMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.BATTERY_SHARE_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.LOCATION_PARAM_INVALID;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.LOCATION_SHARE_DISABLED;

/**
 * 共享域（电量互看 + 位置/轨迹 + 共享开关）Service 实现类
 *
 * 与 todo 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）；couple 由当前登录人反查
 * （SecurityFrameworkUtils），不接受客户端传入 coupleId / userId。
 *
 * partner 组装口径（电量/位置同构）：partner userId 由 couple 双列反解（userA/userB 二选一），
 * 对方开关关 → shareEnabled=false + 数据字段 null（开关关是正常态，不打错误码）。
 *
 * 3s 限频（M27 由 30 收紧）：以服务端 create_time 为准（不信任端上 reportTime），窗口内
 * 静默吞点回 ok——端上已在客户端节流，此处防重放/多端并发（§12.4「上报频率受限」服务端兜底）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class ShareServiceImpl implements ShareService {

    /**
     * 上报限频窗口（秒）：同用户两次落库最小间隔。M27 由 30 收紧到 3——位置页会话期改为
     * 5s 高频上报（对标微信共享实时位置观感），端上其余节奏（电量 60s / 主页 ticker 5min）
     * 均远大于该值不触发吞点；窗口内静默吞点回最新点不变（防重放/多端并发兜底仍在）
     */
    private static final int REPORT_MIN_INTERVAL_SECONDS = 3;

    /**
     * WS 信号类型：位置有新点（M27，端上 ChatPushStore.TYPE_LOVE_LOCATION_NEW 同串契约）。
     * content = 新落库点 VO JSON（id/lat/lng/accuracy/reportTime，坐标 GCJ-02）——与聊天
     * 帧不同，本帧 content 即数据本身（点小、无隐私增量，端上免二次拉取即时落 marker）；
     * 对方离线静默丢帧，latest 轮询兜底（与聊天信号通道同构）
     */
    private static final String LOCATION_TYPE_LOVE_NEW = "love-location-new";

    /**
     * WS 信号类型：提醒对方更新位置（M28 行动轨迹，端上 ChatPushStore.TYPE_LOVE_LOCATION_PULL
     * 同串契约）。content = "{}" 占位（端上不解析，收到即触发一次 doReport——对方位置共享开
     * + 前台才实际出点，双闸静默；pull 不触发 pull，无回路风暴）
     */
    private static final String LOCATION_TYPE_LOVE_PULL = "love-location-pull";

    /**
     * 轨迹点保留天数（30 天物理清除，idx_user_time 支撑，report 时顺带清理）
     */
    private static final int LOCATION_RETENTION_DAYS = 30;

    /**
     * 轨迹单次返回上限（mapper DESC 截「最近的 N 个」后 service 翻转回升序）
     */
    private static final int TRAIL_LIMIT = 500;

    /**
     * 地点记录最小间隔（分钟，M28）：距本人最新 place 不足该间隔且位移不足换地阈值则不记，
     * 即「同地约 30min 记一条」（半小时到一小时区间）
     */
    private static final int PLACE_MIN_INTERVAL_MINUTES = 30;

    /**
     * 地点记录换地位移阈值（米，M28）：距最新 place 达到该值视为换了地方，立即记新条目
     */
    private static final double PLACE_MOVE_METERS = 300.0;

    /**
     * 地点列表单次返回上限（mapper DESC 截「最近的 N 条」后 service 翻转回升序）
     */
    private static final int PLACE_LIST_LIMIT = 100;

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private LoveShareSettingMapper shareSettingMapper;
    @Resource
    private LoveBatterySnapshotMapper batterySnapshotMapper;
    @Resource
    private LoveLocationSnapshotMapper locationSnapshotMapper;
    @Resource
    private LoveLocationPlaceMapper locationPlaceMapper;

    /**
     * WS 推送器（M27 位置实时，可选注入：websocket starter 未装配时为 null 静默跳过，
     * 端上走 latest 轮询兜底；MessageServiceImpl 同款收口）
     */
    @Autowired(required = false)
    private WebSocketMessageSender webSocketMessageSender;

    @Override
    public AppShareSettingRespVO getShareSetting() {
        LoveUserDO user = getValidatedCurrentUser();
        return buildSettingRespVO(user.getId());
    }

    @Override
    public AppShareSettingRespVO updateShareSetting(AppShareSettingUpdateReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveShareSettingDO setting = shareSettingMapper.selectByUserId(user.getId());
        if (setting == null) {
            // 懒创建：默认电量开/位置关，再应用非 null 字段；并发撞 uk 捕获后走 update 分支
            try {
                shareSettingMapper.insert(new LoveShareSettingDO()
                        .setUserId(user.getId())
                        .setBatteryShare(reqVO.getBatteryShare() != null ? reqVO.getBatteryShare() : Boolean.TRUE)
                        .setLocationShare(reqVO.getLocationShare() != null ? reqVO.getLocationShare() : Boolean.FALSE));
            } catch (DuplicateKeyException e) {
                setting = shareSettingMapper.selectByUserId(user.getId());
                applySettingUpdate(setting, reqVO);
            }
        } else {
            applySettingUpdate(setting, reqVO);
        }
        return buildSettingRespVO(user.getId());
    }

    private void applySettingUpdate(LoveShareSettingDO setting, AppShareSettingUpdateReqVO reqVO) {
        // null = 不改动该开关（端上单开关独立切换）
        LoveShareSettingDO update = new LoveShareSettingDO().setId(setting.getId());
        if (reqVO.getBatteryShare() != null) {
            update.setBatteryShare(reqVO.getBatteryShare());
        }
        if (reqVO.getLocationShare() != null) {
            update.setLocationShare(reqVO.getLocationShare());
        }
        shareSettingMapper.updateById(update);
    }

    private AppShareSettingRespVO buildSettingRespVO(Long userId) {
        LoveShareSettingDO setting = shareSettingMapper.selectByUserId(userId);
        if (setting == null) {
            // 懒创建默认值（与表 DDL default 一致：电量开/位置关）
            return AppShareSettingRespVO.builder().batteryShare(true).locationShare(false).build();
        }
        return AppShareSettingRespVO.builder()
                .batteryShare(Boolean.TRUE.equals(setting.getBatteryShare()))
                .locationShare(Boolean.TRUE.equals(setting.getLocationShare()))
                .build();
    }

    @Override
    public AppBatteryRespVO reportBattery(AppBatteryReportReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 我方开关关 → 拒收上报（双端同断：端上本就不会报，此处防绕过客户端直调）
        requireBatteryShareEnabled(user.getId());
        upsertBatterySnapshot(user.getId(), couple.getId(), reqVO.getBatteryLevel(), reqVO.getChargingStatus());
        return buildBatteryRespVO(couple, user.getId());
    }

    @Override
    public AppBatteryRespVO getBattery() {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        return buildBatteryRespVO(couple, user.getId());
    }

    private void upsertBatterySnapshot(Long userId, Long coupleId, Integer batteryLevel, Integer chargingStatus) {
        LoveBatterySnapshotDO row = batterySnapshotMapper.selectByUserId(userId);
        if (row == null) {
            try {
                batterySnapshotMapper.insert(new LoveBatterySnapshotDO()
                        .setUserId(userId)
                        .setCoupleId(coupleId)
                        .setBatteryLevel(batteryLevel)
                        .setChargingStatus(chargingStatus));
                return;
            } catch (DuplicateKeyException e) {
                // 并发首报撞 uk_user_id：重查后落 update 分支（DO 类注释）
                row = batterySnapshotMapper.selectByUserId(userId);
            }
        }
        // update_time 随 ON UPDATE CURRENT_TIMESTAMP 刷新 = 新 reportTime；
        // couple_id 同步刷新到当前 couple（解绑重绑后口径，checkin 同款）
        batterySnapshotMapper.updateById(new LoveBatterySnapshotDO()
                .setId(row.getId())
                .setCoupleId(coupleId)
                .setBatteryLevel(batteryLevel)
                .setChargingStatus(chargingStatus));
    }

    private AppBatteryRespVO buildBatteryRespVO(LoveCoupleDO couple, Long myUserId) {
        LoveBatterySnapshotDO mine = batterySnapshotMapper.selectByUserId(myUserId);
        return AppBatteryRespVO.builder()
                .mine(mine == null ? null : AppBatterySnapshotVO.builder()
                        .batteryLevel(mine.getBatteryLevel())
                        .chargingStatus(mine.getChargingStatus())
                        .reportTime(mine.getUpdateTime())
                        .build())
                .partner(buildBatteryPartner(couple, myUserId))
                .build();
    }

    private AppBatteryPartnerVO buildBatteryPartner(LoveCoupleDO couple, Long myUserId) {
        Long partnerUserId = resolvePartnerUserId(couple, myUserId);
        boolean shareEnabled = isShareEnabled(partnerUserId, true);
        if (!shareEnabled) {
            return AppBatteryPartnerVO.builder().shareEnabled(false).build();
        }
        LoveBatterySnapshotDO row = batterySnapshotMapper.selectByUserId(partnerUserId);
        return AppBatteryPartnerVO.builder()
                .shareEnabled(true)
                .batteryLevel(row == null ? null : row.getBatteryLevel())
                .chargingStatus(row == null ? null : row.getChargingStatus())
                .reportTime(row == null ? null : row.getUpdateTime())
                .build();
    }

    @Override
    public AppLocationPointVO reportLocation(AppLocationReportReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 我方开关关 → 拒收上报（强隐私 §12.4：不采就是真不采，服务端兜底防绕过）
        requireLocationShareEnabled(user.getId());
        LocalDateTime reportTime = resolveReportTime(reqVO.getReportTime());
        validateCoordinate(reqVO.getLat(), reqVO.getLng());
        // 3s 限频（服务端 create_time 口径，M27 会话期 5s 上报）：窗口内静默吞点，仍回最新点（端上无需区分）
        LoveLocationSnapshotDO latest = locationSnapshotMapper.selectLatestByUserId(user.getId());
        if (latest == null || latest.getCreateTime().isBefore(LocalDateTime.now().minusSeconds(REPORT_MIN_INTERVAL_SECONDS))) {
            locationSnapshotMapper.insert(new LoveLocationSnapshotDO()
                    .setCoupleId(couple.getId())
                    .setUserId(user.getId())
                    .setLat(reqVO.getLat())
                    .setLng(reqVO.getLng())
                    .setAccuracy(reqVO.getAccuracy() == null ? 0 : reqVO.getAccuracy())
                    .setReportTime(reportTime));
            // 顺带物理清除 30 天前旧点（保留策略；idx_user_time 支撑，多数情况 0 行）
            locationSnapshotMapper.purgeBefore(user.getId(), LocalDateTime.now().minusDays(LOCATION_RETENTION_DAYS));
            // M28 地点记录（30min 粒度 + 换地 ≥300m 即记）：节流判定走服务端时钟与库内数据，
            // 防多端/绕过客户端；吞点分支（3s 限频）不落 place，3s << 30min 无漏记影响
            recordPlaceIfDue(couple.getId(), user.getId(), reqVO, reportTime);
            AppLocationPointVO point = buildPointVO(locationSnapshotMapper.selectLatestByUserId(user.getId()));
            // WS 触发器（M27 位置实时）：落库后给对方在线连接推「有新点」帧，content 即点数据
            // 本身（VO JSON，reportTime 经 TimestampLocalDateTimeSerializer 已是 epoch 毫秒，
            // 端上 LocationPointInfo.from 直解落 marker）；对方离线静默丢帧（sender 内部吞
            // IOException 不影响回包），对方不开位置页时点仍入库，latest 轮询兜底可见
            if (point != null && webSocketMessageSender != null) {
                webSocketMessageSender.send(UserTypeEnum.MEMBER.getValue(), resolvePartnerUserId(couple, user.getId()),
                        LOCATION_TYPE_LOVE_NEW, JsonUtils.toJsonString(point));
            }
            return point;
        }
        return buildPointVO(latest);
    }

    @Override
    public AppLocationLatestRespVO getLatestLocation() {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        return AppLocationLatestRespVO.builder()
                .mine(buildPointVO(locationSnapshotMapper.selectLatestByUserId(user.getId())))
                .partner(buildLocationPartner(couple, user.getId()))
                .build();
    }

    private AppLocationPartnerVO buildLocationPartner(LoveCoupleDO couple, Long myUserId) {
        Long partnerUserId = resolvePartnerUserId(couple, myUserId);
        boolean shareEnabled = isShareEnabled(partnerUserId, false);
        if (!shareEnabled) {
            return AppLocationPartnerVO.builder().shareEnabled(false).build();
        }
        LoveLocationSnapshotDO latest = locationSnapshotMapper.selectLatestByUserId(partnerUserId);
        return AppLocationPartnerVO.builder().shareEnabled(true).point(buildPointVO(latest)).build();
    }

    @Override
    public AppLocationTrailRespVO getTrail(String target, Long startTs, Long endTs) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 参数收口：target 枚举 + 时间区间合法性（本域唯二参数错误面，1_003_015_001）
        boolean isPartner;
        if ("self".equals(target)) {
            isPartner = false;
        } else if ("partner".equals(target)) {
            isPartner = true;
        } else {
            throw exception(LOCATION_PARAM_INVALID);
        }
        if (startTs == null || endTs == null || startTs > endTs) {
            throw exception(LOCATION_PARAM_INVALID);
        }
        LocalDateTime startTime = toLocalDateTime(startTs);
        LocalDateTime endTime = toLocalDateTime(endTs);
        Long targetUserId;
        boolean shareEnabled;
        if (isPartner) {
            targetUserId = resolvePartnerUserId(couple, user.getId());
            shareEnabled = isShareEnabled(targetUserId, false);
            if (!shareEnabled) {
                // 开关关回空列表 + shareEnabled=false（不打错误码，端上区分「没共享」与「没数据」）
                return AppLocationTrailRespVO.builder().shareEnabled(false)
                        .points(Collections.emptyList()).truncated(false).build();
            }
        } else {
            targetUserId = user.getId();
            shareEnabled = true;
        }
        // DESC LIMIT 500 截「最近的 N 个」，翻转回升序（升序 LIMIT 会反取最老的点）
        List<LoveLocationSnapshotDO> rows =
                locationSnapshotMapper.selectRecentByCoupleUser(couple.getId(), targetUserId, startTime, endTime);
        List<AppLocationPointVO> points = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            points.add(buildPointVO(rows.get(i)));
        }
        return AppLocationTrailRespVO.builder()
                .shareEnabled(shareEnabled)
                .points(points)
                .truncated(rows.size() >= TRAIL_LIMIT)
                .build();
    }

    @Override
    public AppLocationPlaceListRespVO getPlaceList(String target, Long startTs, Long endTs) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 参数收口与 getTrail 完全同构（本域唯二参数错误面，1_003_015_001）
        boolean isPartner;
        if ("self".equals(target)) {
            isPartner = false;
        } else if ("partner".equals(target)) {
            isPartner = true;
        } else {
            throw exception(LOCATION_PARAM_INVALID);
        }
        if (startTs == null || endTs == null || startTs > endTs) {
            throw exception(LOCATION_PARAM_INVALID);
        }
        LocalDateTime startTime = toLocalDateTime(startTs);
        LocalDateTime endTime = toLocalDateTime(endTs);
        Long targetUserId;
        boolean shareEnabled;
        if (isPartner) {
            targetUserId = resolvePartnerUserId(couple, user.getId());
            shareEnabled = isShareEnabled(targetUserId, false);
            if (!shareEnabled) {
                // 开关关回空列表 + shareEnabled=false（不打错误码，端上区分「没共享」与「没数据」）
                return AppLocationPlaceListRespVO.builder().shareEnabled(false)
                        .places(Collections.emptyList()).truncated(false).build();
            }
        } else {
            targetUserId = user.getId();
            shareEnabled = true;
        }
        // DESC LIMIT 100 截「最近的 N 条」，翻转回升序（升序 LIMIT 会反取最老的记录）
        List<LoveLocationPlaceDO> rows =
                locationPlaceMapper.selectRecentByCoupleUser(couple.getId(), targetUserId, startTime, endTime);
        List<AppLocationPlaceVO> places = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            places.add(buildPlaceVO(rows.get(i)));
        }
        return AppLocationPlaceListRespVO.builder()
                .shareEnabled(shareEnabled)
                .places(places)
                .truncated(rows.size() >= PLACE_LIST_LIMIT)
                .build();
    }

    @Override
    public void pullPartnerLocation() {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 只发提醒帧，不代查对方数据（对方回点是独立的一次 report，开关/前台双闸仍在对方侧）
        if (webSocketMessageSender != null) {
            webSocketMessageSender.send(UserTypeEnum.MEMBER.getValue(), resolvePartnerUserId(couple, user.getId()),
                    LOCATION_TYPE_LOVE_PULL, "{}");
        }
        // 不回投递结果（sender 返回 void 拿不到），恒 success——端上 toast 用「已提醒」语义
    }

    // ========== 私有工具 ==========

    private AppLocationPointVO buildPointVO(LoveLocationSnapshotDO row) {
        if (row == null) {
            return null;
        }
        return AppLocationPointVO.builder()
                .id(row.getId())
                .lat(row.getLat())
                .lng(row.getLng())
                .accuracy(row.getAccuracy())
                .reportTime(row.getReportTime())
                .build();
    }

    /**
     * M28 地点记录节流落库：无记录 / 距最新 place ≥30min / 位移 ≥300m 任一成立才插
     * （服务端时钟与库内数据判定，防多端/绕过客户端；「≥30min 或换地即记」的产品定案）。
     * try/catch 全吞——地点是 report 的附属产物，落库失败只 log.warn，不影响上报回包与
     * WS 推送；地址字段按列宽截断（VO 不设 @Size——校验失败会连坐标上报一起拒收）
     */
    private void recordPlaceIfDue(Long coupleId, Long userId, AppLocationReportReqVO reqVO, LocalDateTime reportTime) {
        try {
            LoveLocationPlaceDO latest = locationPlaceMapper.selectLatestByUserId(userId);
            boolean due = latest == null
                    || latest.getReportTime().isBefore(LocalDateTime.now().minusMinutes(PLACE_MIN_INTERVAL_MINUTES))
                    || distanceMeters(latest.getLat(), latest.getLng(), reqVO.getLat(), reqVO.getLng()) >= PLACE_MOVE_METERS;
            if (!due) {
                return;
            }
            locationPlaceMapper.insert(new LoveLocationPlaceDO()
                    .setCoupleId(coupleId)
                    .setUserId(userId)
                    .setLat(reqVO.getLat())
                    .setLng(reqVO.getLng())
                    .setProvince(truncate(reqVO.getAddrProv(), 32))
                    .setCity(truncate(reqVO.getAddrCity(), 32))
                    .setDistrict(truncate(reqVO.getAddrDist(), 32))
                    .setAddress(truncate(reqVO.getAddrText(), 128))
                    .setPoi(truncate(reqVO.getAddrPoi(), 64))
                    .setReportTime(reportTime));
            // 顺带物理清除 30 天前旧行（保留策略，与 snapshot 同款口径）
            locationPlaceMapper.purgeBefore(userId, LocalDateTime.now().minusDays(LOCATION_RETENTION_DAYS));
        } catch (Exception e) {
            log.warn("[recordPlaceIfDue] 地点记录落库失败（不影响位置上报） userId={}", userId, e);
        }
    }

    private AppLocationPlaceVO buildPlaceVO(LoveLocationPlaceDO row) {
        if (row == null) {
            return null;
        }
        return AppLocationPlaceVO.builder()
                .id(row.getId())
                .lat(row.getLat())
                .lng(row.getLng())
                .province(row.getProvince())
                .city(row.getCity())
                .district(row.getDistrict())
                .address(row.getAddress())
                .poi(row.getPoi())
                .reportTime(row.getReportTime())
                .build();
    }

    /**
     * 球面距离（米，haversine）：换地阈值（300m）判定用；place 坐标 decimal(10,6) 精度已足够，
     * 6371000 为地球平均半径
     */
    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * 地址字段清洗：null 原样回 null、trim 后空串也归 null（端上 "" 表示没解析到，
     * 落库统一 null 口径）、超列宽截断（address=128 / 其余 32|64，DDL 列宽对齐）
     */
    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /**
     * 校验坐标非 (0,0) 空点（null-island：定位失败时部分机型回 0/0，入库会污染轨迹与地图）
     */
    private void validateCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null || (lat == 0d && lng == 0d)) {
            throw exception(LOCATION_PARAM_INVALID);
        }
    }

    /**
     * 端上 fix 时间 → 落库时间：null 兜底服务端 now；未来时间截断为 now（防端上时钟漂移
     * 污染轨迹排序）；不加过去窗校验（补报语义接受，30 天 purge 兜底）
     */
    private LocalDateTime resolveReportTime(Long reportTimeTs) {
        LocalDateTime now = LocalDateTime.now();
        if (reportTimeTs == null) {
            return now;
        }
        LocalDateTime time = toLocalDateTime(reportTimeTs);
        return time.isAfter(now) ? now : time;
    }

    private LocalDateTime toLocalDateTime(Long epochMilli) {
        return Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private void requireBatteryShareEnabled(Long userId) {
        if (!isShareEnabled(userId, true)) {
            throw exception(BATTERY_SHARE_DISABLED);
        }
    }

    private void requireLocationShareEnabled(Long userId) {
        if (!isShareEnabled(userId, false)) {
            throw exception(LOCATION_SHARE_DISABLED);
        }
    }

    /**
     * 查某用户的共享开关（懒创建默认值口径与 buildSettingRespVO 一致）
     *
     * @param battery true=电量开关 false=位置开关
     */
    private boolean isShareEnabled(Long userId, boolean battery) {
        LoveShareSettingDO setting = shareSettingMapper.selectByUserId(userId);
        if (setting == null) {
            return battery; // 默认：电量开 / 位置关
        }
        return battery ? Boolean.TRUE.equals(setting.getBatteryShare())
                : Boolean.TRUE.equals(setting.getLocationShare());
    }

    /**
     * 由 couple 双列反解 partner userId（userA/userB 二选一）
     */
    private Long resolvePartnerUserId(LoveCoupleDO couple, Long myUserId) {
        if (Objects.equals(couple.getUserAId(), myUserId)) {
            return couple.getUserBId();
        }
        return couple.getUserAId();
    }

    /**
     * 获得当前登录用户编号
     *
     * 理论不可达为 null：controller 未标 @PermitAll，未登录请求已被 anyRequest().authenticated() 拦截；
     * 万一穿透（token 被并发移除等），按 401 让端上走刷新/回 Gate 通道（TodoServiceImpl 同款）
     */
    private Long getCurrentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 校验当前用户可用（§6.5 停用账号收口）：行不存在（被 admin 删除）与停用统一按
     * 「账号已停用」（1_003_005_002）处理，端上清会话回 Gate，行为一致
     */
    private LoveUserDO getValidatedCurrentUser() {
        Long userId = getCurrentUserId();
        LoveUserDO user = loveUserMapper.selectById(userId);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return user;
    }

    /**
     * 校验当前用户存在有效 couple 关系（未绑定抛 1_003_004_003）
     *
     * couple 由当前登录人反查（LoveCoupleMapper.selectByUserId，无客户端传参），天然防 IDOR；
     * couple 行物理删除、行存在即绑定态（D-11），查询口径与 todo 组一致
     */
    private LoveCoupleDO getValidatedCurrentCouple(Long userId) {
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(userId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        return couple;
    }

}
