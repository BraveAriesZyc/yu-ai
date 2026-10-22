package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.wish.vo.LoveWishPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.wish.vo.LoveWishRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.wish.LoveWishDO;
import cn.iocoder.yudao.module.love.dal.mysql.wish.WishMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.WISH_NOT_EXISTS;

/**
 * 恋爱清单管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 wish 组，M14）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在；
 * 删除为逻辑删除（@TableLogic），端上传播走全量覆盖（app 拉取即整体覆盖，天然生效）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminWishServiceImpl implements LoveAdminWishService {

    @Resource
    private WishMapper wishMapper;

    @Override
    public PageResult<LoveWishRespVO> getWishPage(LoveWishPageReqVO pageReqVO) {
        PageResult<LoveWishDO> pageResult = wishMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveWishDO>()
                        .eqIfPresent(LoveWishDO::getCoupleId, pageReqVO.getCoupleId())
                        .eqIfPresent(LoveWishDO::getUserId, pageReqVO.getUserId())
                        // @TableLogic 自动附加 deleted = 0，已删心愿不出现在分页
                        .betweenIfPresent(LoveWishDO::getCreateTime, pageReqVO.getCreateTime())
                        .orderByDesc(LoveWishDO::getId));
        return BeanUtils.toBean(pageResult, LoveWishRespVO.class);
    }

    @Override
    public void deleteWish(Long id) {
        // 校验存在（跨 couple 的 id 传入也统一按不存在拒绝，不暴露他 couple 心愿的存在性）；
        // 不校验创建人（admin 兜底，不受 D-19e 的 1_003_009_001 限制）
        LoveWishDO wish = wishMapper.selectById(id);
        if (wish == null) {
            throw exception(WISH_NOT_EXISTS);
        }
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）
        wishMapper.deleteById(id);
    }

}
