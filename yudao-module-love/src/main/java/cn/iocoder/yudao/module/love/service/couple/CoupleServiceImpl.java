package cn.iocoder.yudao.module.love.service.couple;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManager;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleBindReqVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleCreateInviteRespVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleOnlineRespVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import cn.iocoder.yudao.module.love.service.mood.MoodOptionService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_INVITE_CODE_EXPIRED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_INVITE_CODE_INVALID;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_SELF_BIND;

/**
 * 情侣关系 Service 实现类
 *
 * 邀请码生成/绑定/查询/解绑，全部以「当前登录用户」为出发点（SecurityFrameworkUtils 反查），
 * 不接受客户端传入的 coupleId / userId，天然防 IDOR（跨 couple 访问无从构造）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class CoupleServiceImpl implements CoupleService {

    /**
     * 邀请码有效期（天），见 INTEGRATION-DESIGN.md §5.2 couple 组
     */
    private static final int INVITE_CODE_EXPIRE_DAYS = 7;

    /**
     * 邀请码长度
     */
    private static final int INVITE_CODE_LENGTH = 8;

    /**
     * 邀请码字符池：大写字母 + 数字，排除易混淆的 0/O/1/I（32 个字符）
     */
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * 生成邀请码撞码重试次数上限：32^8 ≈ 1.1 万亿组合，正常一次命中
     */
    private static final int INVITE_CODE_MAX_RETRY = 10;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private MoodOptionService moodOptionService;

    /**
     * WS 内存会话表（M13 在线判定，D-18）：required=false——yudao.websocket.enable=false 时 bean
     * 不注册，null 即恒离线（渐进降级，与 MessageServiceImpl 的 sender 弱注同款）；
     * sender-type=local 单实例内存直查，多实例部署前须先落 redis sender 扩展（D-18 部署红线）
     */
    @Autowired(required = false)
    private WebSocketSessionManager webSocketSessionManager;

    @Override
    public AppCoupleCreateInviteRespVO createInvite() {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        // 1. 已有未过期邀请码 → 直接复用（不续期，见 INTEGRATION-DESIGN.md §5.2）
        if (hasValidInviteCode(user)) {
            return buildCreateInviteRespVO(user.getInviteCode(), user.getInviteExpireTime());
        }
        // 2. 生成新邀请码 + 有效期 7 天，写 love_user.invite_code / invite_expire_time；
        //    idx_invite_code 为普通索引（非唯一），撞码兜底 = 应用层查重 + 重试
        //    （覆盖写使旧邀请码自然失效：按码查属主只能命中最新一条）
        LocalDateTime expireTime = LocalDateTime.now().plusDays(INVITE_CODE_EXPIRE_DAYS);
        for (int i = 0; i < INVITE_CODE_MAX_RETRY; i++) {
            String code = generateInviteCode();
            if (loveUserMapper.selectByInviteCode(code) != null) {
                continue; // 撞码，重新生成
            }
            loveUserMapper.updateById(new LoveUserDO().setId(user.getId())
                    .setInviteCode(code)
                    .setInviteExpireTime(expireTime));
            return buildCreateInviteRespVO(code, expireTime);
        }
        // 理论不可达：连续 10 次撞码概率约 0；兜底走系统异常
        log.error("[createInvite][userId({}) 连续 {} 次生成邀请码撞码，请排查]",
                user.getId(), INVITE_CODE_MAX_RETRY);
        throw exception(INTERNAL_SERVER_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCoupleRespVO bind(AppCoupleBindReqVO reqVO) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口（校验发起绑定的当前用户）
        getValidatedCurrentUser(currentUserId);
        // 邀请码归一化（trim + 大写）：码池全大写（INVITE_CODE_CHARACTERS），App 端仅 trim 不
        // uppercase，手机键盘小写输入视觉相同的码会误判无效 1_003_004_000；生成端本就是大写，
        // 查重（createInvite）/绑定统一此口径。锁内复核用同一变量，防小写输入过 ① 后被复核打回
        String inviteCode = reqVO.getInviteCode().trim().toUpperCase(Locale.ROOT);
        // ① 按邀请码查发起方（idx_invite_code）→ 无效 1_003_004_000（D-8 步骤 ①）
        LoveUserDO inviter = loveUserMapper.selectByInviteCode(inviteCode);
        if (inviter == null) {
            throw exception(COUPLE_INVITE_CODE_INVALID);
        }
        // ② 惰性判过期（invite_expire_time）→ 1_003_004_004（D-8 步骤 ②）
        if (!hasValidInviteCode(inviter)) {
            throw exception(COUPLE_INVITE_CODE_EXPIRED);
        }
        // ③ 校验非自绑 → 1_003_004_002（D-8 步骤 ③）
        if (Objects.equals(inviter.getId(), currentUserId)) {
            throw exception(COUPLE_SELF_BIND);
        }
        // ④ 按 userId 升序锁双方 love_user 行（FOR UPDATE 防死锁），锁内复核 + 校验双方均无有效 couple
        //    （D-8 步骤 ④：couple 校验用 FOR UPDATE 当前读（selectByUserIdForUpdate）——互绑产出
        //    (user_a=B,user_b=A) 与 (user_a=A,user_b=B) 两行、撞不到 uk_user_a/uk_user_b 任何一个，
        //    此当前读是互绑竞态的唯一防线；唯一键仅终审同邀请方多受邀方/同一受邀方多邀请方两类）
        long minId = Math.min(inviter.getId(), currentUserId);
        long maxId = Math.max(inviter.getId(), currentUserId);
        LoveUserDO rowMin = loveUserMapper.selectOneForUpdate(LoveUserDO::getId, minId);
        LoveUserDO rowMax = loveUserMapper.selectOneForUpdate(LoveUserDO::getId, maxId);
        LoveUserDO inviterLocked = inviter.getId() == minId ? rowMin : rowMax;
        LoveUserDO selfLocked = currentUserId == minId ? rowMin : rowMax;
        // 锁内复核：防 ①②③ 校验后、加锁前的并发窗口内，邀请码被属主刷新/清除或发起方行被 admin 删除
        if (inviterLocked == null
                || !Objects.equals(inviterLocked.getInviteCode(), inviteCode)) {
            throw exception(COUPLE_INVITE_CODE_INVALID);
        }
        if (!hasValidInviteCode(inviterLocked)) {
            throw exception(COUPLE_INVITE_CODE_EXPIRED);
        }
        // 极端并发（当前用户行被 admin 删除）：按停用处理，端上收到 1_003_005_002 清会话回 Gate，行为一致
        if (selfLocked == null) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        // 锁内校验双方均无有效 couple（couple 行物理删除，行存在即处于绑定态）。
        // 必须 FOR UPDATE 当前读：RR 快照在事务首个 selectById（getValidatedCurrentUser）处建立、
        // 早于加锁，普通一致性读看不到加锁前并发事务已提交的 couple 行（详见 selectByUserIdForUpdate 注释）
        if (loveCoupleMapper.selectByUserIdForUpdate(inviterLocked.getId()) != null
                || loveCoupleMapper.selectByUserIdForUpdate(currentUserId) != null) {
            throw exception(COUPLE_EXISTS);
        }
        // ⑤ 插入 love_couple：user_a = 邀请码属主、user_b = 当前用户（D-8 步骤 ⑤）
        LocalDateTime now = LocalDateTime.now();
        LoveCoupleDO couple = new LoveCoupleDO()
                .setUserAId(inviterLocked.getId())
                .setUserBId(currentUserId)
                .setStatus(LoveCoupleDO.STATUS_NORMAL)
                .setBindTime(now)
                // 不继承 BaseDO，insertFill（DefaultDBFieldHandler）不生效，公共字段显式赋值
                .setCreateTime(now)
                .setUpdateTime(now)
                .setCreator(String.valueOf(currentUserId))
                .setUpdater(String.valueOf(currentUserId));
        try {
            loveCoupleMapper.insert(couple);
        } catch (DuplicateKeyException ex) {
            throw exception(COUPLE_EXISTS);
        }
        // ⑤′ 绑定播种基础心情选项（M3.5，D-14）：复制 love_mood_option_default 启用行到新 couple 私有
        //    字典（播种即复制，admin 后续改模板不回灌）；本事务内执行——播种失败随绑定整体回滚（D-8 语义，
        //    不单独吞错）；模板空 → 字典空，端上空态自愈（「点 + 添加心情」），不报错
        moodOptionService.seedDefaults(couple.getId());
        // 6. 绑定成功后清双方邀请码字段：绑定后邀请即已消费，不清除的话，A/B 解绑后旧邀请码
        //    在有效期内仍可被第三方凭码绑定（createInvite 重新生成即可获得新码）；
        //    置 NULL 必须走 UpdateWrapper —— updateById 默认忽略 null 字段，写不进去
        clearInviteCode(inviterLocked.getId());
        clearInviteCode(currentUserId);
        return buildCoupleRespVO(couple, inviterLocked);
    }

    @Override
    public AppCoupleRespVO getCouple() {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        // couple 由当前登录人反查（无客户端传入 id），防 IDOR
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        Long partnerId = Objects.equals(couple.getUserAId(), currentUserId)
                ? couple.getUserBId() : couple.getUserAId();
        // 伴侣信息回查 love_user（昵称/头像）；行不存在的极端场景（对方被 admin 删除）信息留空，不阻断
        LoveUserDO partner = loveUserMapper.selectById(partnerId);
        return buildCoupleRespVO(couple, partner);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind() {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        // 双方任一可发起；couple 由当前登录人反查（无客户端传参），天然防 IDOR
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        // 物理删除 couple 行（DO 无 @TableLogic，deleteById 即物理 DELETE，D-11）；
        // todo/mood/message 数据保留（couple_id 留历史值），重绑产生新 couple_id，双方端上回未绑定态
        loveCoupleMapper.deleteById(couple.getId());
    }

    @Override
    public AppCoupleOnlineRespVO getCoupleOnline() {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        // couple 由当前登录人反查（无客户端传参），防 IDOR；端上仅 BOUND 态轮询，
        // 此分支多为解绑竞态，端上按 BizError 静默
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        // 在线判定 = WS 内存会话表（D-18）：对方名下任一在线连接即在线（多端并存天然覆盖）；
        // 回包禁改 CommonResult<Boolean> 标量（端上 toOk 标量落空对象，M11 冻结决策）
        boolean partnerOnline = webSocketSessionManager != null && !webSocketSessionManager
                .getSessionList(UserTypeEnum.MEMBER.getValue(), getPartnerId(couple, currentUserId))
                .isEmpty();
        AppCoupleOnlineRespVO resp = new AppCoupleOnlineRespVO();
        resp.setPartnerOnline(partnerOnline);
        return resp;
    }

    /**
     * 取 couple 对方 userId（userA/userB 二选一；与 MessageServiceImpl.getPartnerId 同构，
     * 两 service 无继承关系从简各自持有）
     */
    private Long getPartnerId(LoveCoupleDO couple, Long currentUserId) {
        return Objects.equals(couple.getUserAId(), currentUserId)
                ? couple.getUserBId() : couple.getUserAId();
    }

    /**
     * 清空指定用户的邀请码字段（置 NULL）
     */
    private void clearInviteCode(Long userId) {
        loveUserMapper.update(null, new LambdaUpdateWrapper<LoveUserDO>()
                .eq(LoveUserDO::getId, userId)
                .set(LoveUserDO::getInviteCode, null)
                .set(LoveUserDO::getInviteExpireTime, null));
    }

    /**
     * 生成 8 位邀请码：大写字母 + 数字，排除易混淆的 0/O/1/I
     */
    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            builder.append(INVITE_CODE_CHARACTERS.charAt(
                    SECURE_RANDOM.nextInt(INVITE_CODE_CHARACTERS.length())));
        }
        return builder.toString();
    }

    /**
     * 邀请码是否有效：非空且未过期（惰性校验，无定时任务）
     */
    private boolean hasValidInviteCode(LoveUserDO user) {
        return user.getInviteCode() != null
                && user.getInviteExpireTime() != null
                && user.getInviteExpireTime().isAfter(LocalDateTime.now());
    }

    private AppCoupleCreateInviteRespVO buildCreateInviteRespVO(String inviteCode, LocalDateTime expireTime) {
        return AppCoupleCreateInviteRespVO.builder()
                .inviteCode(inviteCode)
                .expireTime(expireTime)
                .build();
    }

    private AppCoupleRespVO buildCoupleRespVO(LoveCoupleDO couple, LoveUserDO partner) {
        AppCoupleRespVO.Partner partnerVO = AppCoupleRespVO.Partner.builder()
                .id(partner != null ? partner.getId() : null)
                .nickname(partner != null ? partner.getNickname() : null)
                .avatar(partner != null ? partner.getAvatar() : null)
                .build();
        return AppCoupleRespVO.builder()
                .id(couple.getId())
                .userIdA(couple.getUserAId())
                .userIdB(couple.getUserBId())
                .status(couple.getStatus())
                .bindTime(couple.getBindTime())
                .partner(partnerVO)
                .build();
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
        return getValidatedCurrentUser(getCurrentUserId());
    }

    private LoveUserDO getValidatedCurrentUser(Long userId) {
        LoveUserDO user = loveUserMapper.selectById(userId);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return user;
    }

}
