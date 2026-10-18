package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserRespVO;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserUpdateReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.anniversary.LoveAnniversaryDO;
import cn.iocoder.yudao.module.love.dal.dataobject.checkin.LoveCheckinDO;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import cn.iocoder.yudao.module.love.dal.dataobject.todo.LoveTodoDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.dataobject.wish.LoveWishDO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import cn.iocoder.yudao.module.love.dal.mysql.anniversary.AnniversaryMapper;
import cn.iocoder.yudao.module.love.dal.mysql.checkin.CheckinMapper;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.message.MessageMapper;
import cn.iocoder.yudao.module.love.dal.mysql.moment.LoveMomentLikeMapper;
import cn.iocoder.yudao.module.love.dal.mysql.moment.MomentMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodRecordMapper;
import cn.iocoder.yudao.module.love.dal.mysql.todo.TodoMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import cn.iocoder.yudao.module.love.dal.mysql.wish.WishMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_USER_NOT_EXISTS;

/**
 * C 端用户管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 user 组）
 *
 * 与 app 侧 CoupleServiceImpl 的私有方法结构同源（先读行、再校验、后写），
 * 但 admin 上下文没有 love_user 身份：不注入 SecurityFrameworkUtils、不走停用收口，
 * 一律按物理 id 定位 + 先校验存在（IDOR 由 admin 权限串拦截，数据域语义不变）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminUserServiceImpl implements LoveAdminUserService {

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private TodoMapper todoMapper;
    @Resource
    private MoodRecordMapper moodRecordMapper;
    @Resource
    private MessageMapper messageMapper;
    @Resource
    private AnniversaryMapper anniversaryMapper;
    @Resource
    private CheckinMapper checkinMapper;
    @Resource
    private MomentMapper momentMapper;
    @Resource
    private WishMapper wishMapper;
    @Resource
    private LoveMomentLikeMapper loveMomentLikeMapper;

    @Override
    public PageResult<LoveUserRespVO> getUserPage(LoveUserPageReqVO pageReqVO) {
        PageResult<LoveUserDO> pageResult = loveUserMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveUserDO>()
                        .likeIfPresent(LoveUserDO::getMobile, pageReqVO.getMobile())
                        .likeIfPresent(LoveUserDO::getNickname, pageReqVO.getNickname())
                        .eqIfPresent(LoveUserDO::getStatus, pageReqVO.getStatus())
                        // @TableLogic 自动附加 deleted = 0，已删用户不出现在分页
                        .betweenIfPresent(LoveUserDO::getCreateTime, pageReqVO.getCreateTime())
                        .orderByDesc(LoveUserDO::getId));
        return BeanUtils.toBean(pageResult, LoveUserRespVO.class);
    }

    @Override
    public void updateUser(LoveUserUpdateReqVO reqVO) {
        // 校验存在（含逻辑删除行：@TableLogic 下 selectById 查不到已删行）
        validateUserExists(reqVO.getId());
        // 昵称/头像/停启用均为可选，updateById 默认忽略 null 字段（仅更新提交的非空字段）
        LoveUserDO updateObj = BeanUtils.toBean(reqVO, LoveUserDO.class);
        loveUserMapper.updateById(updateObj);
        // 停用即时生效路径（§6.5）：app 各业务 service 入口的停用收口查 love_user.status，
        // 停用（或行不存在）返回 1_003_005_002，端上清会话回 Gate，无需额外处理已签发 token
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        LoveUserDO user = loveUserMapper.selectById(id);
        if (user == null) {
            throw exception(AUTH_USER_NOT_EXISTS);
        }
        // 1. mobile 改写 concat(mobile, '#', id) 释放 uk_mobile 唯一键（yudao 惯例手法，见 LoveUserDO 类注释
        //    / love.sql 头部约定）。必须先于逻辑删除：@TableLogic 下 update 只命中 deleted = 0 的行
        //    （置 NULL 不行——mobile NOT NULL，且改写保留了原值可追溯）
        if (user.getMobile() != null) {
            loveUserMapper.update(null, new LambdaUpdateWrapper<LoveUserDO>()
                    .eq(LoveUserDO::getId, id)
                    .set(LoveUserDO::getMobile, user.getMobile() + "#" + id));
        }
        // 2. 逻辑删除用户（@TableLogic → UPDATE deleted = 1）
        loveUserMapper.deleteById(id);
        // 3. 级联逻辑删除其名下数据（§5.3）：@TableLogic 下 mapper.delete(wrapper) 即 UPDATE deleted = 1，
        //    数据保留可追溯（与 D-11「解绑留数据」同一精神）
        todoMapper.delete(LoveTodoDO::getOwnerId, id); // 其创建的待办（owner_id = 该用户）
        moodRecordMapper.delete(LoveMoodRecordDO::getUserId, id); // 其心情记录（user_id = 该用户）
        messageMapper.delete(new LambdaQueryWrapperX<LoveMessageDO>()
                // 其收发的消息（sender_id 或 receiver_id = 该用户，or 语义收在 and 内）
                .and(w -> w.eq(LoveMessageDO::getSenderId, id)
                        .or()
                        .eq(LoveMessageDO::getReceiverId, id)));
        // M18 级联补全（原缺四域：删用户后其纪念日/打卡/瞬间/心愿悬空残留）：
        anniversaryMapper.delete(LoveAnniversaryDO::getOwnerId, id); // 其创建的纪念日（owner_id = 该用户）
        checkinMapper.delete(LoveCheckinDO::getUserId, id); // 其打卡记录（user_id = 该用户）
        momentMapper.delete(LoveMomentDO::getUserId, id); // 其相册瞬间（user_id = 该用户）
        wishMapper.delete(LoveWishDO::getUserId, id); // 其心愿（user_id = 该用户）
        // moment_like 显式 SQL 物理删（对抗审查 M18 修正：走 wrapper 的 delete 被 @TableLogic
        // 改写成逻辑删，墓碑行会占死 uk_moment_user；unlike 同款显式 SQL 口径）：清该用户全部
        // 点赞行为数据，防给伴侣瞬间的点赞残留致 likeCount 虚高（伴侣瞬间仍留对方点赞）
        loveMomentLikeMapper.deleteByUserId(id);
        // 4. 物理解绑 couple（物理 DELETE，LoveCoupleDO 无 @TableLogic，D-11）：
        //    对方端轮询 couple/get 得 1_003_004_003 → 端上切回绑定引导态；
        //    被删用户本人端上：业务收口查不到 love_user 行 → 1_003_005_002 → 清会话回 Gate
        loveCoupleMapper.delete(new LambdaQueryWrapperX<LoveCoupleDO>()
                .and(w -> w.eq(LoveCoupleDO::getUserAId, id)
                        .or()
                        .eq(LoveCoupleDO::getUserBId, id)));
    }

    private void validateUserExists(Long id) {
        if (loveUserMapper.selectById(id) == null) {
            throw exception(AUTH_USER_NOT_EXISTS);
        }
    }

}
