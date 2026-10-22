package cn.iocoder.yudao.module.love.service.wish;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishDeleteReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishDoneReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.dataobject.wish.LoveWishDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import cn.iocoder.yudao.module.love.dal.mysql.wish.WishMapper;
import cn.iocoder.yudao.module.love.service.message.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.WISH_DELETE_FORBIDDEN;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.WISH_NOT_EXISTS;

/**
 * 恋爱清单 Service 实现类（M14）
 *
 * 与 todo 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）+ validateWishBelongsToCouple（IDOR 防范）。
 * couple 由当前登录人反查（SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，
 * 按 id 写操作再校验 wish.coupleId == 当前 coupleId，双重收口防 IDOR（§5.2 表头强约束）。
 *
 * ⚠ 本类及 markWishDone 刻意不加 @Transactional（D-19d）：RR 事务内 rows=0 路径重读会命中
 * 旧快照回错态；各语句独立 autocommit 保证 CAS 提交后重读必见已提交真值。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class WishServiceImpl implements WishService {

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private WishMapper wishMapper;
    @Resource
    private MessageService messageService;

    @Override
    public List<AppWishRespVO> getWishList() {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        // 未绑定 couple → 1_003_004_003（wish 归属 = couple 共享，无 couple 无列表）
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 排序 create_time asc, id asc 由 mapper 收口（端上分层重排覆盖，D-19f）
        return BeanUtils.toBean(wishMapper.selectListByCoupleId(couple.getId()), AppWishRespVO.class);
    }

    @Override
    public AppWishRespVO createWish(AppWishCreateReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // userId 仅记录创建人（删除权限依据 D-19e，双方均可互见）；done 固定初始 false，
        // done_by / done_time 留 NULL（undo 路径同语义）；title trim 落库（@NotBlank 已拒纯空白，trim 恒非空）
        LoveWishDO wish = new LoveWishDO()
                .setCoupleId(couple.getId())
                .setUserId(user.getId())
                .setTitle(reqVO.getTitle().trim())
                .setDone(false);
        wishMapper.insert(wish);
        // insert 后 id（MySQL AUTO 回填）与 createTime（BaseDO insertFill 回填）已在 DO 上，直接转 VO
        return BeanUtils.toBean(wish, AppWishRespVO.class);
    }

    @Override
    public AppWishRespVO markWishDone(AppWishDoneReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 先查 wish 再校验 couple 归属（防 IDOR，§5.2 表头强约束）
        LoveWishDO wish = validateWishBelongsToCouple(reqVO.getId(), couple.getId());
        if (Boolean.TRUE.equals(reqVO.getDone())) {
            // CAS 置位（D-19b 单闸）：done = b'0' 才生效——并发双勾 / 重复请求恰一方 rows=1，
            // 恰该方发完成消息，另一方 rows=0 落入幂等路径（下方重读回真值）
            int rows = wishMapper.markDone(wish.getId(), couple.getId(), user.getId());
            if (rows > 0) {
                // 完成消息走 sendMessage 公共路径（落库 + WS 推送单一路径，D-19c）：
                // 字符串拼接禁 String.format（防标题 % 歧义）；标题 64 封顶总长 ≤75 字 < 500 无需截断；
                // senderId = 勾选者（sendMessage 内部取当前登录人）；勾选者本端该消息靠切聊天 Tab 增量拉自愈
                messageService.sendMessage(new AppMessageSendReqVO("我们一起完成了「" + wish.getTitle() + "」🎉"));
            }
        } else {
            // undo：显式置 NULL 三字段（updateById 忽略 null 置不上）；重复 undo rows=0 幂等；
            // 不清任何防重发标记（D-19b：无 celebrated 列，undo→re-done 重发为预期）
            wishMapper.markUndone(wish.getId(), couple.getId());
        }
        // 重读真值回包（D-19h）：rows=0 幂等路径与并发态统一取库真值（done_by / done_time 恒准确）；
        // 无事务（D-19d）保证重读必见已提交真值；null = 勾删 / admin 删竞态 → 按 000 拒绝（toast 语义自洽）
        LoveWishDO latest = wishMapper.selectById(wish.getId());
        if (latest == null) {
            throw exception(WISH_NOT_EXISTS);
        }
        return BeanUtils.toBean(latest, AppWishRespVO.class);
    }

    @Override
    public void deleteWish(AppWishDeleteReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 归属校验同 done（防 IDOR）
        LoveWishDO wish = validateWishBelongsToCouple(reqVO.getId(), couple.getId());
        // 仅创建者可删（D-19e，moment 用户维度口径）：伴侣不能删对方添加的心愿（防误删），任意状态可删
        if (!Objects.equals(wish.getUserId(), user.getId())) {
            throw exception(WISH_DELETE_FORBIDDEN);
        }
        // @TableLogic 逻辑删除（D-11：couple 解绑后数据保留）；端上传播走全量覆盖，天然生效
        wishMapper.deleteById(wish.getId());
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
     * 按 id 查 wish 并强制校验 couple 归属（§5.2 表头强约束，防 IDOR）
     *
     * 不存在与跨 couple 统一抛 WISH_NOT_EXISTS（1_003_009_000，D-19e 防探测，
     * MESSAGE_NOT_EXISTS recall 口径——不暴露他 couple 心愿的存在性）
     *
     * @return 归属校验通过的 wish
     */
    private LoveWishDO validateWishBelongsToCouple(Long wishId, Long coupleId) {
        LoveWishDO wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw exception(WISH_NOT_EXISTS);
        }
        if (!Objects.equals(wish.getCoupleId(), coupleId)) {
            throw exception(WISH_NOT_EXISTS);
        }
        return wish;
    }

}
