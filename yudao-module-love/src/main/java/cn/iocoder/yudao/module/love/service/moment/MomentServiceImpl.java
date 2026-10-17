package cn.iocoder.yudao.module.love.service.moment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentLikeReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentLikeRespVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentLikeDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.moment.LoveMomentLikeMapper;
import cn.iocoder.yudao.module.love.dal.mysql.moment.MomentMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOMENT_DELETE_FORBIDDEN;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOMENT_NOT_EXISTS;

/**
 * 相册瞬间 Service 实现类（M9；M15 多图 + 点赞扩展）
 *
 * 与 checkin/anniversary 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）。couple 由当前登录人反查
 * （SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，天然防 IDOR（§5.2 表头强约束）。
 *
 * 删除权限 = 仅上传者本人（用户维度，1_003_008_001）：伴侣不能删对方的瞬间（防误删），
 * 与 todo 的 couple 维度 IDOR 口径不同，属 M9 冻结决策。
 *
 * M15（D-20 组）：① create 归一 imageUrls（老版 App 只传 imageUrl → [imageUrl]），image_url
 * 恒落首图冗余（admin/web 零改动根基）；② list/create 回包组装 imageUrls/likeCount/liked
 * （接口只加不改 D-20g）；③ like 幂等 toggle（INSERT IGNORE / 物理 DELETE），不发悄悄话消息、
 * 无 WS 信号（D-20c），对端靠 App 可见期静默轮询感知；不存在/跨 couple 统一 MOMENT_NOT_EXISTS
 * （D-20d 防探测，零新增错误码）。
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
    /** 单条瞬间图片上限（M15，@Size(max=9) 已拦，此处防御性收口） */
    private static final int MOMENT_IMAGE_MAX = 9;

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private MomentMapper momentMapper;
    @Resource
    private LoveMomentLikeMapper loveMomentLikeMapper;

    @Override
    public AppMomentRespVO createMoment(AppMomentCreateReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // M15 归一（D-20a）：imageUrls 过空白 trim；空且 imageUrl 非空 → [imageUrl]（老版 App 兼容出口）
        List<String> urls = reqVO.getImageUrls() == null ? Collections.emptyList()
                : reqVO.getImageUrls().stream().filter(StrUtil::isNotBlank).map(String::trim).collect(Collectors.toList());
        if (urls.isEmpty()) {
            urls = List.of(reqVO.getImageUrl().trim());
        }
        if (urls.size() > MOMENT_IMAGE_MAX) {
            urls = urls.subList(0, MOMENT_IMAGE_MAX);
        }
        LoveMomentDO moment = new LoveMomentDO()
                .setCoupleId(couple.getId())
                .setUserId(user.getId())
                // text null/空白统一落空串（列 NOT NULL DEFAULT ''，端上空串判「无配文」不判 null）
                .setText(reqVO.getText() == null ? "" : reqVO.getText().trim())
                // image_url 恒 = 首图冗余（admin LoveMomentRespVO/web 管理页只读此列，零改动根基）
                .setImageUrl(urls.get(0))
                .setImageUrls(JsonUtils.toJsonString(urls));
        momentMapper.insert(moment);
        // insert 后 id（MySQL AUTO 回填）与 createTime（BaseDO insertFill 回填）已在 DO 上，直接转 VO
        AppMomentRespVO vo = BeanUtils.toBean(moment, AppMomentRespVO.class);
        // ⚠ hutool BeanUtils 遇 DO imageUrls(String)→VO imageUrls(List) 走 CollectionConverter
        // 按逗号切分产生垃圾列表——同名拷贝一律弃用，无条件覆写（list 组装同此口径）
        vo.setImageUrls(urls);
        // 新发布瞬间不可能有赞（伴侣在本端点返回前无途径看到该 id），恒 0/false
        vo.setLikeCount(0);
        vo.setLiked(false);
        return vo;
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
        List<AppMomentRespVO> vos = BeanUtils.toBean(moments, AppMomentRespVO.class);
        if (moments.isEmpty()) {
            return vos;
        }
        // M15 组装（D-20g）：一页仅 2 条 like SQL——全赞行 Java 侧 group 计数 + 我赞过的 id 集
        // （couple 恒 2 人，行数 ≤ 2×页大小，无需聚合 SQL）；逐条覆写 imageUrls
        // （hutool String→List 同名拷贝坑，见 createMoment 注）
        List<Long> ids = moments.stream().map(LoveMomentDO::getId).collect(Collectors.toList());
        Map<Long, Long> countMap = loveMomentLikeMapper.selectListByMomentIds(ids).stream()
                .collect(Collectors.groupingBy(LoveMomentLikeDO::getMomentId, Collectors.counting()));
        Set<Long> likedIds = loveMomentLikeMapper.selectListByMomentIdsAndUserId(ids, user.getId()).stream()
                .map(LoveMomentLikeDO::getMomentId).collect(Collectors.toSet());
        for (int i = 0; i < moments.size(); i++) {
            AppMomentRespVO vo = vos.get(i);
            vo.setImageUrls(parseImageUrls(moments.get(i)));
            vo.setLikeCount(countMap.getOrDefault(moments.get(i).getId(), 0L).intValue());
            vo.setLiked(likedIds.contains(moments.get(i).getId()));
        }
        return vos;
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
        // 不级联清赞（D-20h）：残留赞行永不被查询（list 只按存活瞬间 id 批量取），无害
        momentMapper.deleteById(id);
        return true;
    }

    @Override
    public AppMomentLikeRespVO likeMoment(AppMomentLikeReqVO reqVO) {
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        LoveMomentDO moment = momentMapper.selectById(reqVO.getId());
        // 不存在/跨 couple 统一 MOMENT_NOT_EXISTS（D-20d 防探测，零新增错误码）
        if (moment == null || !Objects.equals(moment.getCoupleId(), couple.getId())) {
            throw exception(MOMENT_NOT_EXISTS);
        }
        // 幂等 toggle（D-20b）：like = INSERT IGNORE 走 uk 吸收并发重复（rows=0 已赞，不打错）；
        // unlike = 物理 DELETE（uk 与逻辑删除冲突唯一解，LoveMomentLikeMapper javadoc 口径）
        if (Boolean.TRUE.equals(reqVO.getLiked())) {
            loveMomentLikeMapper.insertIgnore(couple.getId(), moment.getId(), user.getId());
        } else {
            loveMomentLikeMapper.deleteByMomentIdAndUserId(moment.getId(), user.getId(), couple.getId());
        }
        // 回包恒操作后服务端真值（D-20e，端上按 id 原位替换）；操作后 liked 恒 = 请求值（幂等收敛）
        AppMomentLikeRespVO resp = new AppMomentLikeRespVO();
        resp.setLikeCount(loveMomentLikeMapper.selectListByMomentIds(List.of(moment.getId())).size());
        resp.setLiked(reqVO.getLiked());
        return resp;
    }

    /**
     * image_urls JSON → URL 列表（恒非空保证）
     *
     * NULL/空/损坏一律兜底 [imageUrl]（存量单图与脏数据同一出口，老版 App 读写双向兼容）；
     * JsonUtils.parseArray 空白入参返空列表、损坏 JSON 抛 RuntimeException，均在此收口
     */
    private List<String> parseImageUrls(LoveMomentDO moment) {
        try {
            List<String> parsed = JsonUtils.parseArray(moment.getImageUrls(), String.class);
            if (parsed != null) {
                parsed = parsed.stream().filter(StrUtil::isNotBlank).map(String::trim).collect(Collectors.toList());
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        } catch (Exception ignore) {
            // 损坏 JSON 兜底首图（读侧防御；写侧恒写合法 JSON，理论不可达）
        }
        return List.of(moment.getImageUrl());
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
