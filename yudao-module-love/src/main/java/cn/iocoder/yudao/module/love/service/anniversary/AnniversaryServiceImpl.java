package cn.iocoder.yudao.module.love.service.anniversary;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.anniversary.LoveAnniversaryDO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.anniversary.AnniversaryMapper;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.ANNIVERSARY_DATE_INVALID;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.ANNIVERSARY_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.ANNIVERSARY_NO_PERMISSION;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;

/**
 * 纪念日 Service 实现类（M5）
 *
 * 与 todo 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）+ validateAnniversaryBelongsToCouple（IDOR 防范）。
 * couple 由当前登录人反查（SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，
 * 按 id 写操作再校验 anniversary.coupleId == 当前 coupleId，双重收口防 IDOR（§5.2 表头强约束）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class AnniversaryServiceImpl implements AnniversaryService {

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private AnniversaryMapper anniversaryMapper;

    @Override
    public List<AppAnniversaryRespVO> getAnniversaryList() {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        // 未绑定 couple → 1_003_004_003（anniversary 归属 = couple 共享，无 couple 无列表）
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 排序 anniv_date asc, id asc 由 mapper 收口（端上覆盖 AnniversaryStore，§7.4）
        return BeanUtils.toBean(anniversaryMapper.selectListByCoupleId(couple.getId()), AppAnniversaryRespVO.class);
    }

    @Override
    public AppAnniversaryRespVO createAnniversary(AppAnniversaryCreateReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // repeat_type 值域收口（1=每年 2=一次性）：仓内无 @Range 使用先例，service 侧打回
        // 全局参数错误（MoodServiceImpl 月份解析同款）
        if (!Objects.equals(reqVO.getRepeatType(), 1) && !Objects.equals(reqVO.getRepeatType(), 2)) {
            throw exception(BAD_REQUEST);
        }
        // 日期二次校验：@Pattern 只挡形状，LocalDate.parse 挡「形状合法但日历不存在」（2026-02-30），
        // 专属错误码 1_003_006_002，语义比全局反序列化失败准
        LocalDate annivDate;
        try {
            annivDate = LocalDate.parse(reqVO.getAnnivDate());
        } catch (DateTimeParseException e) {
            throw exception(ANNIVERSARY_DATE_INVALID);
        }
        // owner_id = 当前用户（仅记录归属，权限依据 = couple 归属校验）；
        // emoji 只存字符串不校验取值（客户端字典，铁律⑥），VO 可空对 null 归一空串
        // （love_anniversary 列 NOT NULL DEFAULT ''，TodoServiceImpl M3 实施修正同款）
        String emoji = reqVO.getEmoji() == null ? "" : reqVO.getEmoji();
        LoveAnniversaryDO anniversary = new LoveAnniversaryDO()
                .setCoupleId(couple.getId())
                .setOwnerId(user.getId())
                .setTitle(reqVO.getTitle())
                .setAnnivDate(annivDate)
                .setRepeatType(reqVO.getRepeatType())
                .setEmoji(emoji);
        anniversaryMapper.insert(anniversary);
        // insert 后 id（MySQL AUTO 回填）与 createTime（BaseDO insertFill 回填）已在 DO 上，直接转 VO
        return BeanUtils.toBean(anniversary, AppAnniversaryRespVO.class);
    }

    @Override
    public void deleteAnniversary(AppAnniversaryIdReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 归属校验（防 IDOR）
        LoveAnniversaryDO anniversary = validateAnniversaryBelongsToCouple(reqVO.getId(), couple.getId());
        // LoveAnniversaryDO 继承 BaseDO 启用 @TableLogic，deleteById 即逻辑删除（D-11：couple 解绑后数据保留）
        anniversaryMapper.deleteById(anniversary.getId());
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

    /**
     * 按 id 查 anniversary 并强制校验 couple 归属（§5.2 表头强约束，防 IDOR）
     *
     * 不存在抛 ANNIVERSARY_NOT_EXISTS（1_003_006_000）；存在但不属于当前 couple 抛
     * ANNIVERSARY_NO_PERMISSION（1_003_006_001，§11 IDOR 用例对 anniversary 组的指定错误码）
     *
     * @return 归属校验通过的纪念日
     */
    private LoveAnniversaryDO validateAnniversaryBelongsToCouple(Long anniversaryId, Long coupleId) {
        LoveAnniversaryDO anniversary = anniversaryMapper.selectById(anniversaryId);
        if (anniversary == null) {
            throw exception(ANNIVERSARY_NOT_EXISTS);
        }
        if (!Objects.equals(anniversary.getCoupleId(), coupleId)) {
            throw exception(ANNIVERSARY_NO_PERMISSION);
        }
        return anniversary;
    }

}
