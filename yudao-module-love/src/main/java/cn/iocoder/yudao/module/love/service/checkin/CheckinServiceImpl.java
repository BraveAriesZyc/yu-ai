package cn.iocoder.yudao.module.love.service.checkin;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.checkin.vo.AppCheckinRespVO;
import cn.iocoder.yudao.module.love.controller.app.checkin.vo.AppCheckinSummaryRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.checkin.LoveCheckinDO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.checkin.CheckinMapper;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;

/**
 * 每日打卡 Service 实现类（M8）
 *
 * 与 todo/mood 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）。couple 由当前登录人反查
 * （SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，天然防 IDOR（§5.2 表头强约束）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class CheckinServiceImpl implements CheckinService {

    /**
     * summary 共同 streak 逐日回溯上限（天）：打卡数据逐日增长时防 O(n) 极端；
     * 正常生活节奏下双方共同连续罕超一年，到达上限按 365 处理
     */
    private static final int SUMMARY_STREAK_MAX_DAYS = 365;

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private CheckinMapper checkinMapper;

    @Override
    public AppCheckinRespVO checkCheckin() {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 服务端以服务端当天为准（无补打，不收日期参数，防端上时钟作弊）
        LocalDate today = LocalDate.now();
        // §5.2 uk_user_date 一人一天一条：命中即幂等返回（打卡重复点击不打错）
        LoveCheckinDO existing = checkinMapper.selectByUserIdAndDate(user.getId(), today);
        if (existing == null) {
            LoveCheckinDO checkin = new LoveCheckinDO()
                    .setCoupleId(couple.getId())
                    .setUserId(user.getId())
                    .setCheckinDate(today);
            try {
                checkinMapper.insert(checkin);
                // insert 后 id（MySQL AUTO 回填）与 createTime（BaseDO insertFill 回填）已在 DO 上，直接转 VO
                return BeanUtils.toBean(checkin, AppCheckinRespVO.class);
            } catch (DuplicateKeyException ex) {
                // 并发窗口内当天已被插入（同设备双击/两设备同打）：回退为再查返回，uk 是最终防线；
                // 本方法无 @Transactional（单条 insert 无多写不变量），再查询自带新快照，
                // RR 快照读陷阱不适用（MoodServiceImpl 在事务内才需要 UPDATE 当前读兜底，此处不需要）
                existing = checkinMapper.selectByUserIdAndDate(user.getId(), today);
                if (existing == null) {
                    // 再查未命中 = uk 位被【逻辑删除行】占用（admin 删了当天记录后用户重打）：
                    // 复活该行并挂当前 couple（CheckinMapper.reviveDeletedByUserIdAndDate，
                    // mood 组遗留在此根治——checkin 的 admin delete 是主操作，当日重打必须可恢复）
                    if (checkinMapper.reviveDeletedByUserIdAndDate(user.getId(), today, couple.getId()) > 0) {
                        existing = checkinMapper.selectByUserIdAndDate(user.getId(), today);
                    }
                }
                if (existing == null) {
                    log.warn("[checkCheckin][userId({}) date({}) uk 冲突后再查/复活均未命中，交由端上重试]",
                            user.getId(), today, ex);
                    throw exception(INTERNAL_SERVER_ERROR);
                }
            }
        }
        // 同日解绑重绑场景：旧行挂旧 couple_id，刷新到当前 couple——不刷新会让本次打卡在
        // 新 couple 的月历（idx_couple_date 按 couple_id 过滤）里不可见，端上表现为「打了但不显示」
        // （MoodServiceImpl recordMood 覆盖写同款口径）
        refreshCoupleIdIfNeeded(existing, couple.getId());
        return BeanUtils.toBean(existing, AppCheckinRespVO.class);
    }

    @Override
    public List<AppCheckinRespVO> getCheckinList(String month) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // month=yyyy-MM 解析当月起止日期；非法格式按全局参数错误 400（端上恒传当前展示月，
        // 缺省当月）；闭区间 [1 日, 月末日]，走 idx_couple_date(couple_id, checkin_date)
        YearMonth yearMonth = parseYearMonthOrBadRequest(month);
        List<LoveCheckinDO> records = checkinMapper.selectListByCoupleIdBetweenDate(
                couple.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return BeanUtils.toBean(records, AppCheckinRespVO.class);
    }

    @Override
    public AppCheckinSummaryRespVO getCheckinSummary() {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 服务端聚合（streak 必须跨月回溯，月历单月视图算不了；打卡表天然小——一人一天一条，
        // 全量拉取单条 SQL 即可，内存聚合不设分页）
        List<LoveCheckinDO> records = checkinMapper.selectListByCoupleId(couple.getId());
        Long partnerId = Objects.equals(couple.getUserAId(), user.getId())
                ? couple.getUserBId() : couple.getUserAId();
        Set<LocalDate> mine = new HashSet<>();
        Set<LocalDate> partnerDates = new HashSet<>();
        for (LoveCheckinDO record : records) {
            if (Objects.equals(record.getUserId(), user.getId())) {
                mine.add(record.getCheckinDate());
            } else if (Objects.equals(record.getUserId(), partnerId)) {
                partnerDates.add(record.getCheckinDate());
            }
            // 其余 userId 为解绑重绑前挂进来的历史行（couple_id 刷新前的旧成员），不计入 summary
        }
        return new AppCheckinSummaryRespVO()
                .setCommonStreak(calcCommonStreak(mine, partnerDates))
                .setMyTotal(mine.size())
                .setPartnerTotal(partnerDates.size());
    }

    /**
     * 同日解绑重绑的 couple_id 刷新：仅 couple_id 不一致时 UPDATE（正常路径零开销），
     * @TableLogic 自动附加 deleted = 0
     */
    private void refreshCoupleIdIfNeeded(LoveCheckinDO checkin, Long coupleId) {
        if (Objects.equals(checkin.getCoupleId(), coupleId)) {
            return;
        }
        checkinMapper.update(null, new LambdaUpdateWrapper<LoveCheckinDO>()
                .eq(LoveCheckinDO::getId, checkin.getId())
                .set(LoveCheckinDO::getCoupleId, coupleId));
        checkin.setCoupleId(coupleId);
    }

    /**
     * 共同连续 streak：同一天双方都打卡才算当天点亮，从今天往前逐日回溯；
     * 今天任一方未打则从昨天起算（今天还没过完，不打断既有连续）；上限 365 天防极端
     */
    private Integer calcCommonStreak(Set<LocalDate> mine, Set<LocalDate> partnerDates) {
        LocalDate day = LocalDate.now();
        if (!mine.contains(day) || !partnerDates.contains(day)) {
            day = day.minusDays(1);
        }
        int streak = 0;
        while (streak < SUMMARY_STREAK_MAX_DAYS && mine.contains(day) && partnerDates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /**
     * month 参数解析（§5.2 mood 组同款）：缺省/空白给当月，非法格式打全局参数错误 400
     */
    private YearMonth parseYearMonthOrBadRequest(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw exception(BAD_REQUEST);
        }
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
     *
     * @return 当前用户
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
     * couple 行物理删除、行存在即绑定态（D-11），查询口径与 couple 组一致
     *
     * @return 当前用户所在的 couple
     */
    private LoveCoupleDO getValidatedCurrentCouple(Long userId) {
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(userId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        return couple;
    }

}
