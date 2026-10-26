package cn.iocoder.yudao.module.love.service.report;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.report.vo.AppReportMonthlyRespVO;
import cn.iocoder.yudao.module.love.controller.app.report.vo.AppReportMonthlyRespVO.TopMood;
import cn.iocoder.yudao.module.love.dal.dataobject.checkin.LoveCheckinDO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.dataobject.wish.LoveWishDO;
import cn.iocoder.yudao.module.love.dal.mysql.checkin.CheckinMapper;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.message.MessageMapper;
import cn.iocoder.yudao.module.love.dal.mysql.moment.MomentMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodRecordMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import cn.iocoder.yudao.module.love.dal.mysql.wish.WishMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;

/**
 * 恋爱月报 Service 实现类（M30）
 *
 * 单月五域只读聚合，无自有 DO/表（零 DDL）：打卡/心情走既有 between-date 查询内存聚合，
 * 消息/相册/心愿走 LambdaQueryWrapper selectCount。全部以「当前登录用户」为出发点
 * （SecurityFrameworkUtils 反查），不接受客户端传入 userId/coupleId，天然防 IDOR。
 * 未绑定静默全 0（UserProfileServiceImpl.getStats 同款口径，不抛 COUPLE_NOT_EXISTS）。
 *
 * @author 芋道源码
 */
@Service
public class ReportServiceImpl implements ReportService {

    /** 心情分布最多返回条数（Top3） */
    private static final int TOP_MOOD_LIMIT = 3;

    @Resource
    private LoveUserMapper loveUserMapper;

    @Resource
    private LoveCoupleMapper loveCoupleMapper;

    @Resource
    private CheckinMapper checkinMapper;

    @Resource
    private MoodRecordMapper moodRecordMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private MomentMapper momentMapper;

    @Resource
    private WishMapper wishMapper;

    @Override
    public AppReportMonthlyRespVO getMonthlyReport(String month) {
        // §6.5 停用账号收口（月报非敏感，但沿用统一入口防停用号继续消费）
        LoveUserDO user = getValidatedCurrentUser();
        // month=yyyy-MM 解析当月起止；非法格式按全局参数错误 400（端上恒传当前展示月，缺省当月）
        YearMonth yearMonth = parseYearMonthOrBadRequest(month);
        // 全 0 兜底先落满：未绑定 / 某域查询前均保持 0，month 始终回填（端上不自行推导）
        AppReportMonthlyRespVO report = new AppReportMonthlyRespVO();
        report.setMonth(yearMonth.toString());
        report.setBindDays(0);
        report.setMyCheckinDays(0L);
        report.setPartnerCheckinDays(0L);
        report.setTogetherCheckinDays(0L);
        report.setMoodCount(0L);
        report.setTopMoods(new ArrayList<>());
        report.setMessageCount(0L);
        report.setMomentCount(0L);
        report.setWishDoneCount(0L);
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(user.getId());
        if (couple == null) {
            return report;
        }
        // 在一起天数：bind_time 当天记第 1 天（between=0 → 1），与 stats 端点逐字口径
        report.setBindDays((int) ChronoUnit.DAYS.between(
                couple.getBindTime().toLocalDate(), LocalDate.now()) + 1);
        fillCheckinDays(report, user, couple, yearMonth);
        fillMood(report, couple, yearMonth);
        // DATETIME 列月区间统一 [本月 1 日零点, 次月 1 日零点)——ge + lt 展开（M18 口径，
        // 直接比 atEndOfMonth().atTime(23:59:59) 更稳，不漏亚秒行）
        LocalDateTime monthStart = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEndExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        // 悄悄话：排除已撤回墓碑行（recalled 标记不删行），与端上列表口径一致（M18）
        report.setMessageCount(messageMapper.selectCount(new LambdaQueryWrapper<LoveMessageDO>()
                .eq(LoveMessageDO::getCoupleId, couple.getId())
                .eq(LoveMessageDO::getRecalled, false)
                .ge(LoveMessageDO::getCreateTime, monthStart)
                .lt(LoveMessageDO::getCreateTime, monthEndExclusive)));
        report.setMomentCount(momentMapper.selectCount(new LambdaQueryWrapper<LoveMomentDO>()
                .eq(LoveMomentDO::getCoupleId, couple.getId())
                .ge(LoveMomentDO::getCreateTime, monthStart)
                .lt(LoveMomentDO::getCreateTime, monthEndExclusive)));
        // 心愿完成数：done_time 落月计（该月内被标记完成的，即便后续撤销也属当月事实）
        report.setWishDoneCount(wishMapper.selectCount(new LambdaQueryWrapper<LoveWishDO>()
                .eq(LoveWishDO::getCoupleId, couple.getId())
                .eq(LoveWishDO::getDone, true)
                .ge(LoveWishDO::getDoneTime, monthStart)
                .lt(LoveWishDO::getDoneTime, monthEndExclusive)));
        return report;
    }

    /**
     * 打卡天数聚合（内存，照 CheckinServiceImpl.getCheckinSummary 口径）：
     * 只认当前双方（解绑重绑前挂进来的历史行 couple_id 已刷新但 userId 为旧成员，不计入）；
     * together = 同日双方都打卡的天数（两 Set 交集）
     */
    private void fillCheckinDays(AppReportMonthlyRespVO report, LoveUserDO user,
                                 LoveCoupleDO couple, YearMonth yearMonth) {
        List<LoveCheckinDO> records = checkinMapper.selectListByCoupleIdBetweenDate(
                couple.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());
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
        }
        report.setMyCheckinDays((long) mine.size());
        report.setPartnerCheckinDays((long) partnerDates.size());
        long together = mine.stream().filter(partnerDates::contains).count();
        report.setTogetherCheckinDays(together);
    }

    /**
     * 心情分布（内存聚合，无 GROUP BY 先例——打卡 summary 同范式）：
     * 按 mood_label 快照列分组（快照不受选项删除影响），取条数 Top3；
     * emoji/color 取组内首条快照（覆盖写口径下同 label 三列一致，取首条即可）
     */
    private void fillMood(AppReportMonthlyRespVO report, LoveCoupleDO couple, YearMonth yearMonth) {
        List<LoveMoodRecordDO> records = moodRecordMapper.selectListByCoupleIdBetweenDate(
                couple.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());
        report.setMoodCount((long) records.size());
        Map<String, List<LoveMoodRecordDO>> byLabel = new LinkedHashMap<>();
        for (LoveMoodRecordDO record : records) {
            byLabel.computeIfAbsent(record.getMoodLabel(), k -> new ArrayList<>()).add(record);
        }
        List<TopMood> topMoods = new ArrayList<>();
        byLabel.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(TOP_MOOD_LIMIT)
                .forEach(entry -> {
                    LoveMoodRecordDO first = entry.getValue().get(0);
                    TopMood topMood = new TopMood();
                    topMood.setLabel(entry.getKey());
                    topMood.setEmoji(first.getEmoji());
                    topMood.setColor(first.getColor());
                    topMood.setCount((long) entry.getValue().size());
                    topMoods.add(topMood);
                });
        report.setTopMoods(topMoods);
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

}
