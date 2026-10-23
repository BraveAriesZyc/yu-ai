package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplatePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplateRespVO;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplateSaveReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.wishtemplate.LoveWishTemplateDO;
import cn.iocoder.yudao.module.love.dal.mysql.wishtemplate.LoveWishTemplateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.WISH_TEMPLATE_NOT_EXISTS;

/**
 * 心愿墙挑选模板 Service 实现类（admin 通道，M25）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在（1_003_014_000），
 * LoveAdminInspirationServiceImpl 同款口径；模板即时生效（App 只读，无缓存层）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminWishTemplateServiceImpl implements LoveAdminWishTemplateService {

    @Resource
    private LoveWishTemplateMapper loveWishTemplateMapper;

    @Override
    public PageResult<LoveWishTemplateRespVO> getWishTemplatePage(LoveWishTemplatePageReqVO pageReqVO) {
        PageResult<LoveWishTemplateDO> pageResult = loveWishTemplateMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveWishTemplateDO>()
                        .likeIfPresent(LoveWishTemplateDO::getCategory, pageReqVO.getCategory())
                        .likeIfPresent(LoveWishTemplateDO::getTitle, pageReqVO.getTitle())
                        .eqIfPresent(LoveWishTemplateDO::getStatus, pageReqVO.getStatus())
                        .betweenIfPresent(LoveWishTemplateDO::getCreateTime, pageReqVO.getCreateTime())
                        // @TableLogic 自动附加 deleted = 0，已删模板不出现在分页；id desc 新添加在前
                        .orderByDesc(LoveWishTemplateDO::getId));
        return BeanUtils.toBean(pageResult, LoveWishTemplateRespVO.class);
    }

    @Override
    public LoveWishTemplateRespVO createWishTemplate(LoveWishTemplateSaveReqVO reqVO) {
        // status 未传走列默认 0 启用（MP 插入忽略 null 字段）；id 忽略（create 语义，防端上误传）
        LoveWishTemplateDO entity = BeanUtils.toBean(reqVO, LoveWishTemplateDO.class).setId(null);
        loveWishTemplateMapper.insert(entity);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(entity, LoveWishTemplateRespVO.class);
    }

    @Override
    public void updateWishTemplate(LoveWishTemplateSaveReqVO reqVO) {
        // 校验存在（admin 按物理 id 定位）
        validateExists(reqVO.getId());
        // 全字段保存式：null 字段保持原值（MP updateById 忽略 null；B 端表单恒全量提交）
        loveWishTemplateMapper.updateById(BeanUtils.toBean(reqVO, LoveWishTemplateDO.class));
    }

    @Override
    public void deleteWishTemplate(Long id) {
        // 校验存在；快照式（kind=2 上送四字段），删模板不影响已发消息
        validateExists(id);
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）
        loveWishTemplateMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (loveWishTemplateMapper.selectById(id) == null) {
            throw exception(WISH_TEMPLATE_NOT_EXISTS);
        }
    }

}
