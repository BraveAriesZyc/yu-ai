package cn.iocoder.yudao.module.love.service.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileRespVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserStatsRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import cn.iocoder.yudao.module.love.dal.dataobject.todo.LoveTodoDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.message.MessageMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodRecordMapper;
import cn.iocoder.yudao.module.love.dal.mysql.todo.TodoMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;

/**
 * 用户资料 Service 实现类
 *
 * 全部以「当前登录用户」为出发点（SecurityFrameworkUtils 反查），不接受客户端传入 userId，天然防 IDOR。
 *
 * @author 芋道源码
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    @Resource
    private LoveUserMapper loveUserMapper;

    @Resource
    private LoveCoupleMapper loveCoupleMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private TodoMapper todoMapper;

    @Resource
    private MoodRecordMapper moodRecordMapper;

    @Override
    public AppUserProfileRespVO getUser() {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        return BeanUtils.toBean(user, AppUserProfileRespVO.class);
    }

    @Override
    public void updateUser(AppUserProfileUpdateReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        // 昵称/头像均为可选提交，仅更新非空字段（updateById 默认忽略 null 字段，天然部分更新）
        if (reqVO.getNickname() == null && reqVO.getAvatar() == null) {
            return;
        }
        loveUserMapper.updateById(new LoveUserDO().setId(user.getId())
                .setNickname(reqVO.getNickname())
                .setAvatar(reqVO.getAvatar()));
    }

    @Override
    public AppUserStatsRespVO getStats() {
        // §6.5 停用账号收口（统计非敏感，但沿用统一入口防停用号继续消费）
        LoveUserDO user = getValidatedCurrentUser();
        AppUserStatsRespVO stats = new AppUserStatsRespVO();
        stats.setBindDays(0);
        stats.setMessageCount(0L);
        stats.setWishCount(0L);
        stats.setTodoCount(0L);
        stats.setMoodCount(0L);
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(user.getId());
        if (couple == null) {
            return stats;
        }
        // 在一起天数：bind_time 当天记第 1 天（between=0 → 1），与纪念日惯例一致（第 520 天时 between=519）
        stats.setBindDays((int) ChronoUnit.DAYS.between(
                couple.getBindTime().toLocalDate(), LocalDate.now()) + 1);
        stats.setMessageCount(messageMapper.selectCount(LoveMessageDO::getCoupleId, couple.getId()));
        // 心愿口径 = kind=2 子集（我的页统计卡「心愿」消费；messageCount 仍为全部消息含文本）
        stats.setWishCount(messageMapper.selectCountWishes(couple.getId()));
        stats.setTodoCount(todoMapper.selectCount(LoveTodoDO::getCoupleId, couple.getId()));
        stats.setMoodCount(moodRecordMapper.selectCount(LoveMoodRecordDO::getCoupleId, couple.getId()));
        return stats;
    }

    /**
     * 获得当前登录用户编号
     *
     * 理论不可达为 null：controller 未标 @PermitAll，未登录请求已被 anyRequest().authenticated() 拦截；
     * 万一穿透（token 被并发移除等），按 401 让端上走刷新/回 Gate 通道
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
