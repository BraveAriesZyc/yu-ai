package cn.iocoder.yudao.module.love.service.dailyquestion;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionAnswerReqVO;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionItemRespVO;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionTodayRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyAnswerDO;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyQuestionDayDO;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyQuestionDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.dailyquestion.DailyAnswerMapper;
import cn.iocoder.yudao.module.love.dal.mysql.dailyquestion.DailyQuestionDayMapper;
import cn.iocoder.yudao.module.love.dal.mysql.dailyquestion.DailyQuestionMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.DAILY_ANSWER_LOCKED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.DAILY_QUESTION_NOT_EXISTS;

/**
 * 每日一问 Service 实现类（app 通道，M32）
 *
 * 与 todo/mood/checkin 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）。couple 由当前登录人反查
 * （SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，天然防 IDOR（§5.2 表头强约束）。
 *
 * 当日题懒生成（D-21）：day 行由当天首个访问者触发 get-or-create（无定时任务），轮换取题
 * 按启用池 id asc 稳定序 + 日期取模（InspirationMapper 今日情话同口径）——同一天恒同题；
 * 题目内容以 day 快照列为准（D-19a），题库增删改不影响已出的天。作答覆盖写 + 揭晓锁
 * （双方都提交即锁定，mood record 覆盖写口径 + CheckinServiceImpl insert 冲突兜底先例）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class DailyQuestionServiceImpl implements DailyQuestionService {

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private DailyQuestionMapper dailyQuestionMapper;
    @Resource
    private DailyQuestionDayMapper dailyQuestionDayMapper;
    @Resource
    private DailyAnswerMapper dailyAnswerMapper;

    @Override
    public AppDailyQuestionTodayRespVO getTodayQuestion() {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        LoveDailyQuestionDayDO day = getOrCreateDay(LocalDate.now());
        return buildTodayResp(couple, user.getId(), day);
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // 揭晓锁判断（读）与覆盖写（写）为读后写不变量，事务收口（mood record 覆盖写同款）
    public AppDailyQuestionTodayRespVO answerQuestion(AppDailyQuestionAnswerReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 只答当天：服务端以服务端当天为准，不收日期参数（无补答，D-21，防端上时钟作弊）
        LocalDate today = LocalDate.now();
        LoveDailyQuestionDayDO day = getOrCreateDay(today);
        if (day == null) {
            // 题库无启用题：today 端点显「今日无题」占位不打错，answer 端点无法落题按本码拒绝
            throw exception(DAILY_QUESTION_NOT_EXISTS);
        }
        Long partnerId = resolvePartnerId(couple, user.getId());
        LoveDailyAnswerDO mine = dailyAnswerMapper.selectByUserIdAndQuestionId(user.getId(), day.getQuestionId());
        LoveDailyAnswerDO partner = dailyAnswerMapper.selectByUserIdAndQuestionId(partnerId, day.getQuestionId());
        // 揭晓锁（D-21）：双方都已提交 = 已揭晓 = 答案锁定，任何再写拒绝（防双设备旧页面/直连接口改答案）
        if (mine != null && partner != null) {
            throw exception(DAILY_ANSWER_LOCKED);
        }
        if (mine != null) {
            // 揭晓前覆盖写自己的答案（mood record 口径）；顺带刷新 couple_id
            // （解绑重绑后当日改答案场景，CheckinServiceImpl#refreshCoupleIdIfNeeded 同款口径）
            dailyAnswerMapper.update(null, new LambdaUpdateWrapper<LoveDailyAnswerDO>()
                    .eq(LoveDailyAnswerDO::getId, mine.getId())
                    .set(LoveDailyAnswerDO::getContent, reqVO.getContent())
                    .set(LoveDailyAnswerDO::getCoupleId, couple.getId()));
        } else {
            LoveDailyAnswerDO answer = new LoveDailyAnswerDO()
                    .setCoupleId(couple.getId())
                    .setUserId(user.getId())
                    .setQuestionId(day.getQuestionId())
                    .setAnswerDate(today)
                    .setContent(reqVO.getContent());
            try {
                dailyAnswerMapper.insert(answer);
            } catch (DuplicateKeyException ex) {
                // 并发窗口内同 (user_id, question_id) 已插入（双设备同时提交）：uk 最终防线，
                // 回退再查后按覆盖写语义收口（last-write-wins）；本表无删除路径，
                // 不存在「逻辑删行占 uk 位」复活问题（CheckinMapper 注释口径）
                mine = dailyAnswerMapper.selectByUserIdAndQuestionId(user.getId(), day.getQuestionId());
                if (mine == null) {
                    log.warn("[answerQuestion][userId({}) questionId({}) uk 冲突后再查未命中，交由端上重试]",
                            user.getId(), day.getQuestionId(), ex);
                    throw exception(INTERNAL_SERVER_ERROR);
                }
                dailyAnswerMapper.update(null, new LambdaUpdateWrapper<LoveDailyAnswerDO>()
                        .eq(LoveDailyAnswerDO::getId, mine.getId())
                        .set(LoveDailyAnswerDO::getContent, reqVO.getContent())
                        .set(LoveDailyAnswerDO::getCoupleId, couple.getId()));
            }
        }
        // 返回 latest 今日题态：本次提交可能正是揭晓的那一答（对方已答），端上即时展示互见
        return buildTodayResp(couple, user.getId(), day);
    }

    @Override
    public List<AppDailyQuestionItemRespVO> getDailyQuestionList(String month) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // month=yyyy-MM 解析当月起止日期；非法格式按全局参数错误 400（端上恒传当前展示月，
        // 缺省当月）；day 行按 question_date、answer 行按 answer_date（冗余恒 = question_date）
        // 两查内存合并（ReportServiceImpl 内存聚合同款口径），返回当月全部当日题（含没答的天）
        YearMonth yearMonth = parseYearMonthOrBadRequest(month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        List<LoveDailyQuestionDayDO> days = dailyQuestionDayMapper.selectListByDateRange(startDate, endDate);
        if (days.isEmpty()) {
            return new ArrayList<>();
        }
        List<LoveDailyAnswerDO> answers = dailyAnswerMapper.selectListByCoupleBetweenDate(
                couple.getId(), startDate, endDate);
        Long partnerId = resolvePartnerId(couple, user.getId());
        Map<Long, LoveDailyAnswerDO> mineMap = new HashMap<>();
        Map<Long, LoveDailyAnswerDO> partnerMap = new HashMap<>();
        for (LoveDailyAnswerDO answer : answers) {
            if (Objects.equals(answer.getUserId(), user.getId())) {
                mineMap.put(answer.getQuestionId(), answer);
            } else if (Objects.equals(answer.getUserId(), partnerId)) {
                partnerMap.put(answer.getQuestionId(), answer);
            }
            // 其余 userId 为解绑重绑前挂进来的历史行（couple_id 刷新前的旧成员），不计入
            // （CheckinServiceImpl#getCheckinSummary 同款口径）
        }
        List<AppDailyQuestionItemRespVO> result = new ArrayList<>(days.size());
        for (LoveDailyQuestionDayDO day : days) {
            result.add(buildItemResp(day, mineMap.get(day.getQuestionId()), partnerMap.get(day.getQuestionId())));
        }
        return result;
    }

    /**
     * 当日题 get-or-create（懒生成，D-21）
     *
     * uk_question_date(question_date) 一天一行：命中即返回；未命中按启用池 id asc 稳定序
     * 取 `date.toEpochDay() % size` 轮换取题插入（同一天同池同序 = 同题，今日情话同口径；
     * 无定时任务预生成，懒生成天然零空转且本地 Quartz 默认关）；并发 DuplicateKeyException
     * 回退再查（uk 最终防线，CheckinServiceImpl 先例）。day 行持有 content/category 快照
     * （D-19a），生成后与题库解耦。池空返回 null（端上「今日无题」占位，不开错误码）。
     */
    private LoveDailyQuestionDayDO getOrCreateDay(LocalDate date) {
        LoveDailyQuestionDayDO existing = dailyQuestionDayMapper.selectByDate(date);
        if (existing != null) {
            return existing;
        }
        List<LoveDailyQuestionDO> pool = dailyQuestionMapper.selectListEnabled();
        if (pool.isEmpty()) {
            return null;
        }
        LoveDailyQuestionDO picked = pool.get((int) (date.toEpochDay() % pool.size()));
        LoveDailyQuestionDayDO day = new LoveDailyQuestionDayDO()
                .setQuestionDate(date)
                .setQuestionId(picked.getId())
                .setContent(picked.getContent())
                .setCategory(picked.getCategory());
        try {
            dailyQuestionDayMapper.insert(day);
            return day;
        } catch (DuplicateKeyException ex) {
            // 并发窗口内当日题已被插入（双方同时进页）：回退再查；day 行无删除路径，
            // 不存在「逻辑删行占 uk 位需复活」问题（CheckinMapper 注释口径）
            return dailyQuestionDayMapper.selectByDate(date);
        }
    }

    /**
     * 今日题态组装：partnerAnswer 仅 revealed（双方都已提交）时下发——揭晓前对方答案不出接口，
     * 只给 partnerAnswered 布尔供「等 TA」状态文案（D-21 隐私口径，防接口层扒答案）；
     * day == null（今日无题）返回全字段 null/false VO
     */
    private AppDailyQuestionTodayRespVO buildTodayResp(LoveCoupleDO couple, Long userId, LoveDailyQuestionDayDO day) {
        if (day == null) {
            return new AppDailyQuestionTodayRespVO();
        }
        LoveDailyAnswerDO mine = dailyAnswerMapper.selectByUserIdAndQuestionId(userId, day.getQuestionId());
        LoveDailyAnswerDO partner = dailyAnswerMapper.selectByUserIdAndQuestionId(
                resolvePartnerId(couple, userId), day.getQuestionId());
        boolean revealed = mine != null && partner != null;
        return new AppDailyQuestionTodayRespVO()
                .setQuestionDate(day.getQuestionDate())
                .setContent(day.getContent())
                .setCategory(day.getCategory())
                // 未答/未揭晓下发改空串（不发 null）：端上 optString 空串 = 未答，恒定字符串契约
                .setMyAnswer(mine == null ? "" : mine.getContent())
                .setPartnerAnswered(partner != null)
                .setPartnerAnswer(revealed ? partner.getContent() : "")
                .setRevealed(revealed);
    }

    /**
     * 历史条目组装：与今日题同隐私口径（partnerAnswer 仅 revealed 时下发——错过没答的天，
     * 对方答案永不揭晓，D-21「只能答当天」的自然结果）
     */
    private AppDailyQuestionItemRespVO buildItemResp(LoveDailyQuestionDayDO day,
                                                     LoveDailyAnswerDO mine, LoveDailyAnswerDO partner) {
        boolean revealed = mine != null && partner != null;
        return new AppDailyQuestionItemRespVO()
                .setQuestionDate(day.getQuestionDate())
                .setContent(day.getContent())
                .setCategory(day.getCategory())
                // 未答/未揭晓下发改空串（不发 null）：端上 optString 空串 = 未答，恒定字符串契约
                .setMyAnswer(mine == null ? "" : mine.getContent())
                .setPartnerAnswered(partner != null)
                .setPartnerAnswer(revealed ? partner.getContent() : "")
                .setRevealed(revealed);
    }

    /**
     * 取 couple 中对方的用户编号（checkin summary 同款；couple 行恒双端非空，D-8 绑定即成对）
     */
    private Long resolvePartnerId(LoveCoupleDO couple, Long userId) {
        return Objects.equals(couple.getUserAId(), userId) ? couple.getUserBId() : couple.getUserAId();
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
