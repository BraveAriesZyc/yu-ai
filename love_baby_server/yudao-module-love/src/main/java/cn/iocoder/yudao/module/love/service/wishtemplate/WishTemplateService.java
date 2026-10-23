package cn.iocoder.yudao.module.love.service.wishtemplate;

import cn.iocoder.yudao.module.love.controller.app.wishtemplate.vo.AppWishTemplateRespVO;

import java.util.List;

/**
 * 心愿墙挑选模板 Service 接口（app 通道，M25）
 *
 * @author 芋道源码
 */
public interface WishTemplateService {

    /**
     * 获得心愿模板全量列表（启用行，sort asc, id asc 稳定序；端上分类聚合）
     */
    List<AppWishTemplateRespVO> getWishTemplateList();

}
