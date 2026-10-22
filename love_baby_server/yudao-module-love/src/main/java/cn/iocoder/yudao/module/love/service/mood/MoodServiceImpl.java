package cn.iocoder.yudao.module.love.service.mood;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodRecordReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodOptionDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodOptionMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodRecordMapper;
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
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_NO_PERMISSION;

/**
 * 每日心情 Service 实现类
 *
 * 以「当前登录用户」为出发点（SecurityFrameworkUtils 反查），不接受客户端传入的 coupleId / userId，
 * 天然防 IDOR（跨 couple 访问无从构造）；写入与查询均挂当前 couple（INTEGRATION-DESIGN.md §5.2 mood 组）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class MoodServiceImpl implements MoodService {

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private MoodRecordMapper moodRecordMapper;
    @Resource
    private MoodOptionMapper moodOptionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean recordMood(AppMoodRecordReqVO reqVO) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        // couple 由当前登录人反查（无客户端传参），防 IDOR；未绑定抛 1_003_004_003（与 todo 组同口径）
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        // 心情选项归属校验（M3.5，D-14）：不存在 1_003_002_000、跨 couple 1_003_002_001
        // （不接受他 couple 选项 id 记心情，防 IDOR 同 todo 组口径）；快照字段取自选项行——
        // 客户端不上送 label/emoji/color（服务端字典为准，不信任端上内容）
        LoveMoodOptionDO option = moodOptionMapper.selectById(reqVO.getOptionId());
        if (option == null) {
            throw exception(MOOD_OPTION_NOT_EXISTS);
        }
        if (!Objects.equals(option.getCoupleId(), couple.getId())) {
            throw exception(MOOD_OPTION_NO_PERMISSION);
        }
        LocalDate today = LocalDate.now();
        // §5.2 uk_user_date 一人一天一条 + 重复写覆盖：命中覆盖、未命中插入，uk 冲突兜底终审并发
        LoveMoodRecordDO existing = moodRecordMapper.selectByUserIdAndDate(currentUserId, today);
        if (existing != null) {
            // 覆盖写：optionId + 快照三列必更；couple_id 一并刷新——同日解绑重绑场景下旧行挂旧 couple_id
            // （解绑仅物删 couple 行，mood 行保留），不刷新会让本次记录在新 couple 的月历
            // （idx_couple_month 按 couple_id 过滤）里不可见，对端上表现为「记了但不显示」；
            // mood_type 废弃列一并清空（updateById 忽略 null 字段写不进 NULL，置空必须走 UpdateWrapper，
            // clearInviteCode 同款）——存量行重记后不再残留旧 1-5 枚举
            moodRecordMapper.update(null, new LambdaUpdateWrapper<LoveMoodRecordDO>()
                    .eq(LoveMoodRecordDO::getId, existing.getId())
                    .set(LoveMoodRecordDO::getCoupleId, couple.getId())
                    .set(LoveMoodRecordDO::getOptionId, option.getId())
                    .set(LoveMoodRecordDO::getMoodLabel, option.getLabel())
                    .set(LoveMoodRecordDO::getEmoji, option.getEmoji())
                    .set(LoveMoodRecordDO::getColor, option.getColor())
                    .set(LoveMoodRecordDO::getMoodType, null));
            return true;
        }
        // 新插入：mood_type 不赋值（列允许 NULL，M3.5 起恒 NULL，D-14）
        LoveMoodRecordDO record = new LoveMoodRecordDO()
                .setCoupleId(couple.getId())
                .setUserId(currentUserId)
                .setMoodDate(today)
                .setOptionId(option.getId())
                .setMoodLabel(option.getLabel())
                .setEmoji(option.getEmoji())
                .setColor(option.getColor());
        try {
            moodRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // 并发窗口内当日记录已被插入（同设备双击 / 两设备同时记）：回退为再查再 update（§5.2 覆盖写语义，
            // 不打错；record 无独立错误码，心情段 1_003_002_xxx 归选项字典），uk 是最终防线
            overwriteRecordWithRetry(couple.getId(), currentUserId, today, option);
        }
        return true;
    }

    @Override
    public List<AppMoodRespVO> getMoodRecordList(String month) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        // month=yyyy-MM 解析当月起止日期；非法格式按全局参数错误 400（端上恒传 MoodPage 当前展示月）
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw exception(BAD_REQUEST);
        }
        // 闭区间 [1 日, 月末日]，走 idx_couple_month(couple_id, mood_date)，返回 couple 双方记录
        List<LoveMoodRecordDO> records = moodRecordMapper.selectListByCoupleIdBetweenDate(
                couple.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return BeanUtils.toBean(records, AppMoodRespVO.class);
    }

    /**
     * uk 冲突后的覆盖回退：再查再 update（§5.2 重复写覆盖语义，一律收敛到「更新当日记录」）
     *
     * 覆盖字段 = recordMood 主路径完全同口径：optionId + 快照三列 + couple_id 刷新 + mood_type 清空
     * （M3.5，D-14；option 由主路径校验归属后整行传入，此处不再重复查字典）。
     *
     * ① 快照读再查：READ COMMITTED 隔离或并发事务先于本事务快照提交时可命中；
     * ② REPEATABLE READ 下本事务快照建立于首个 select（getValidatedCurrentUser），早于并发事务提交，
     *    快照读看不到其已提交行（LoveCoupleMapper.selectByUserIdForUpdate 同款 RR 坑位注释）——
     *    补一道 UPDATE 当前读（UPDATE 恒见最新已提交版本，uk_user_date 走索引）直接覆盖；
     * ③ 仍未命中：当日行在并发窗口内被逻辑删除（admin 级联，极罕见）——重试插入终审；
     *    再撞 uk（uk_user_date 不含 deleted 列，逻辑删除行仍占位，属 love.sql 的 DDL 级遗留）抛系统异常，
     *    端上重试即可恢复，根治需 uk 变更、留待后续里程碑跟进
     */
    private void overwriteRecordWithRetry(Long coupleId, Long userId, LocalDate date, LoveMoodOptionDO option) {
        // ① 快照读再查：命中即按 id 覆盖（与主路径覆盖口径完全同字段，同日解绑重绑场景不残留旧 couple_id）
        LoveMoodRecordDO latest = moodRecordMapper.selectByUserIdAndDate(userId, date);
        if (latest != null) {
            moodRecordMapper.update(null, buildOverwriteWrapper(latest.getId(), coupleId, option));
            return;
        }
        // ② 快照读未命中 ≠ 行不存在：UPDATE 当前读兜底覆盖（@TableLogic 自动附加 deleted = 0，
        //    已逻辑删除行不会被误更新复活）；couple_id 同步刷新（同主路径的同日重绑口径）
        if (moodRecordMapper.update(null, buildOverwriteWrapper(null, coupleId, option)
                .eq(LoveMoodRecordDO::getUserId, userId)
                .eq(LoveMoodRecordDO::getMoodDate, date)) > 0) {
            return;
        }
        // ③ 行已被逻辑删除：重试插入终审（couple_id NOT NULL，挂当前 couple），再撞 uk 抛系统异常（理论极难达）
        try {
            moodRecordMapper.insert(new LoveMoodRecordDO()
                    .setCoupleId(coupleId)
                    .setUserId(userId)
                    .setMoodDate(date)
                    .setOptionId(option.getId())
                    .setMoodLabel(option.getLabel())
                    .setEmoji(option.getEmoji())
                    .setColor(option.getColor()));
        } catch (DuplicateKeyException ex) {
            log.warn("[overwriteRecordWithRetry][userId({}) date({}) uk 二次冲突（疑当日记录已被逻辑删除占位），交由端上重试]",
                    userId, date, ex);
            throw exception(INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 覆盖写字段集（recordId 为 null 时不限定 id，供 ② 的 userId+date 当前读兜底复用）：
     * optionId + 快照三列 + couple_id 刷新 + mood_type 废弃列清空（置 NULL 必须 UpdateWrapper）
     */
    private LambdaUpdateWrapper<LoveMoodRecordDO> buildOverwriteWrapper(Long recordId, Long coupleId,
                                                                        LoveMoodOptionDO option) {
        LambdaUpdateWrapper<LoveMoodRecordDO> wrapper = new LambdaUpdateWrapper<LoveMoodRecordDO>();
        if (recordId != null) {
            wrapper.eq(LoveMoodRecordDO::getId, recordId);
        }
        return wrapper
                .set(LoveMoodRecordDO::getCoupleId, coupleId)
                .set(LoveMoodRecordDO::getOptionId, option.getId())
                .set(LoveMoodRecordDO::getMoodLabel, option.getLabel())
                .set(LoveMoodRecordDO::getEmoji, option.getEmoji())
                .set(LoveMoodRecordDO::getColor, option.getColor())
                .set(LoveMoodRecordDO::getMoodType, null);
    }

    /**
     * 获得当前登录用户编号
     *
     * 理论不可达为 null：controller 未标 @PermitAll，未登录请求已被 anyRequest().authenticated() 拦截；
     * 万一穿透（token 被并发移除等），按 401 让端上走刷新/回 Gate 通道（CoupleServiceImpl 同款）
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
     * （私有方法结构照抄 CoupleServiceImpl 范式，todo/mood/message 各 service 统一口径）
     */
    private void getValidatedCurrentUser(Long userId) {
        LoveUserDO user = loveUserMapper.selectById(userId);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

}
