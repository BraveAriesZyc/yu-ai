package cn.iocoder.yudao.module.love.service.mood;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodOptionDO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodOptionDefaultDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodOptionDefaultMapper;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodOptionMapper;
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
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_LABEL_DUPLICATED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_LIMIT_EXCEEDED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_NO_PERMISSION;

/**
 * 心情选项字典 Service 实现类（couple 私有，M3.5 / D-14）
 *
 * 与 todo 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝）+ 归属校验（IDOR 防范，1_003_002_001）。
 *
 * seedDefaults 无 @Transactional：参与调用方 CoupleServiceImpl.bind 的事务（播种失败随绑定
 * 整体回滚，D-8 语义）；本类其余方法单写操作，无需事务包裹。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class MoodOptionServiceImpl implements MoodOptionService {

    /**
     * 用户新增选项的固定 sort 值（落尾；播种行继承模板 sort 1..5，排序 sort asc, id asc）
     */
    private static final int USER_OPTION_SORT = 999;
    /**
     * couple 选项数上限（选择器 UI 消费上限，防字典无限膨胀）
     */
    private static final int MOOD_OPTION_MAX_COUNT = 20;

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private MoodOptionMapper moodOptionMapper;
    @Resource
    private MoodOptionDefaultMapper moodOptionDefaultMapper;

    @Override
    public List<AppMoodOptionRespVO> getMoodOptionList() {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 排序 sort asc, id asc 由 mapper 收口（端上按回包顺序渲染，不重排）
        return BeanUtils.toBean(moodOptionMapper.selectListByCoupleIdOrderBySort(couple.getId()),
                AppMoodOptionRespVO.class);
    }

    @Override
    public AppMoodOptionRespVO createMoodOption(AppMoodOptionCreateReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // couple 内重名拒绝（@TableLogic 自动过滤已删行——删了再加回同名合法，1_003_002_003）；
        // 极端并发双设备同时加同名可能双插（无 uk，可删可再加），UI 层面可接受
        if (moodOptionMapper.selectByCoupleIdAndLabel(couple.getId(), reqVO.getLabel()) != null) {
            throw exception(MOOD_OPTION_LABEL_DUPLICATED);
        }
        // 上限校（1_003_002_002）：选择器 UI 的消费上限
        if (moodOptionMapper.selectCountByCoupleId(couple.getId()) >= MOOD_OPTION_MAX_COUNT) {
            throw exception(MOOD_OPTION_LIMIT_EXCEEDED);
        }
        // emoji/color 只存字符串不校验取值（端上预设集选定后原样上送，客户端字典，铁律⑥同款约定）；
        // sort 恒 999 落尾（不提供重排，M3.5 显式不做）
        LoveMoodOptionDO option = new LoveMoodOptionDO()
                .setCoupleId(couple.getId())
                .setLabel(reqVO.getLabel())
                .setEmoji(reqVO.getEmoji())
                .setColor(reqVO.getColor())
                .setSort(USER_OPTION_SORT);
        moodOptionMapper.insert(option);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(option, AppMoodOptionRespVO.class);
    }

    @Override
    public void deleteMoodOption(AppMoodOptionIdReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 先查再校验 couple 归属（防 IDOR，同 todo 组 1_003_001_001 口径 → 1_003_002_001）
        LoveMoodOptionDO option = moodOptionMapper.selectById(reqVO.getId());
        if (option == null) {
            throw exception(MOOD_OPTION_NOT_EXISTS);
        }
        if (!Objects.equals(option.getCoupleId(), couple.getId())) {
            throw exception(MOOD_OPTION_NO_PERMISSION);
        }
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）；历史月历渲染走 love_mood_record 快照列，
        // 不受本删除影响（D-14：删选项不 gray-out 历史）
        moodOptionMapper.deleteById(option.getId());
    }

    @Override
    public void seedDefaults(Long coupleId) {
        // 复制启用模板行（status=启用；模板空 → 字典空，端上空态自愈，不报错）。
        // 参与调用方 bind 事务（不另标 @Transactional）：播种失败随绑定整体回滚；
        // admin 改模板与本调用并发时读到旧/新模板均可接受（播种是复制语义，D-14）
        List<LoveMoodOptionDefaultDO> defaults = moodOptionDefaultMapper.selectListEnabledForSeed();
        for (LoveMoodOptionDefaultDO def : defaults) {
            moodOptionMapper.insert(new LoveMoodOptionDO()
                    .setCoupleId(coupleId)
                    .setLabel(def.getLabel())
                    .setEmoji(def.getEmoji())
                    .setColor(def.getColor())
                    .setSort(def.getSort()));
        }
        if (defaults.isEmpty()) {
            // 非异常：模板被 admin 清空/全停用时新 couple 字典为空，端上走「点 + 添加心情」空态
            log.warn("[seedDefaults][coupleId({}) 心情默认模板为空，新情侣字典为空（端上空态自愈）]", coupleId);
        }
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
     *
     * @return 当前用户
     */
    private LoveUserDO getValidatedCurrentUser() {
        return getValidatedCurrentUser(getCurrentUserId());
    }

    private LoveUserDO getValidatedCurrentUser(Long userId) {
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
     * couple 行物理删除、行存在即绑定态（D-11），查询口径与 couple/todo 组一致
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
