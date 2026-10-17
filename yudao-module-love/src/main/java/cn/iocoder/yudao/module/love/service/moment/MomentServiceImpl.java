package cn.iocoder.yudao.module.love.service.moment;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.moment.MomentMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOMENT_DELETE_FORBIDDEN;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOMENT_NOT_EXISTS;

/**
 * 相册瞬间 Service 实现类（M9）
 *
 * 与 checkin/anniversary 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）。couple 由当前登录人反查
 * （SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，天然防 IDOR（§5.2 表头强约束）。
 *
 * 删除权限 = 仅上传者本人（用户维度，1_003_008_001）：伴侣不能删对方的瞬间（防误删），
 * 与 todo 的 couple 维度 IDOR 口径不同，属 M9 冻结决策。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class MomentServiceImpl implements MomentService {

    /** 列表每页条数上限（防一次性拉全表，端上进页 30 条 + 加载更多递增） */
    private static final int LIST_LIMIT_MAX = 100;
    /** 列表每页条数缺省 */
    private static final int LIST_LIMIT_DEFAULT = 30;

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private MomentMapper momentMapper;

    @Override
    public AppMomentRespVO createMoment(AppMomentCreateReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        LoveMomentDO moment = new LoveMomentDO()
                .setCoupleId(couple.getId())
                .setUserId(user.getId())
                // text null/空白统一落空串（列 NOT NULL DEFAULT ''，端上空串判「无配文」不判 null）
                .setText(reqVO.getText() == null ? "" : reqVO.getText().trim())
                .setImageUrl(reqVO.getImageUrl());
        momentMapper.insert(moment);
        // insert 后 id（MySQL AUTO 回填）与 createTime（BaseDO insertFill 回填）已在 DO 上，直接转 VO
        return BeanUtils.toBean(moment, AppMomentRespVO.class);
    }

    @Override
    public List<AppMomentRespVO> getMomentList(Long beforeId, Integer limit) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // limit 钳制：null/非正数给默认，超上限截到 100（防一次性拉全表）
        int safeLimit = limit == null || limit <= 0 ? LIST_LIMIT_DEFAULT : Math.min(limit, LIST_LIMIT_MAX);
        // 游标分页：id 倒序（新→旧），beforeId 空/0 = 最新一页（走 idx_couple_id(couple_id, id)）
        List<LoveMomentDO> moments = momentMapper.selectListByCoupleIdBefore(
                couple.getId(), beforeId == null ? null : beforeId, safeLimit);
        return BeanUtils.toBean(moments, AppMomentRespVO.class);
    }

    @Override
    public Boolean deleteMoment(Long id) {
        LoveUserDO user = getValidatedCurrentUser();
        // 删除按用户维度校验（非 couple 维度）：行不存在 1_003_008_000，非上传者 1_003_008_001
        LoveMomentDO moment = momentMapper.selectById(id);
        if (moment == null) {
            throw exception(MOMENT_NOT_EXISTS);
        }
        if (!Objects.equals(moment.getUserId(), user.getId())) {
            throw exception(MOMENT_DELETE_FORBIDDEN);
        }
        momentMapper.deleteById(id);
        return true;
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
