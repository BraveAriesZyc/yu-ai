package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplatePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplateRespVO;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplateSaveReqVO;

/**
 * 心愿墙挑选模板 Service 接口（admin 通道，M25）
 *
 * @author 芋道源码
 */
public interface LoveAdminWishTemplateService {

    /**
     * 获得心愿模板分页
     */
    PageResult<LoveWishTemplateRespVO> getWishTemplatePage(LoveWishTemplatePageReqVO pageReqVO);

    /**
     * 新增模板，返回带自增 id 的 VO
     */
    LoveWishTemplateRespVO createWishTemplate(LoveWishTemplateSaveReqVO reqVO);

    /**
     * 更新模板（全字段保存式，null 字段保持原值）
     */
    void updateWishTemplate(LoveWishTemplateSaveReqVO reqVO);

    /**
     * 删除模板（逻辑删除；快照式，历史消息渲染零影响）
     */
    void deleteWishTemplate(Long id);

}
