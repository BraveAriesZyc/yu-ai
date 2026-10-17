package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.wish.vo.LoveWishPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.wish.vo.LoveWishRespVO;

/**
 * 恋爱清单管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 wish 组，M14）
 *
 * @author 芋道源码
 */
public interface LoveAdminWishService {

    /**
     * 获得心愿分页
     *
     * @param pageReqVO 分页查询（coupleId / userId 精确 + createTime 段）
     * @return 心愿分页
     */
    PageResult<LoveWishRespVO> getWishPage(LoveWishPageReqVO pageReqVO);

    /**
     * 删除心愿（admin 兜底，不受「仅创建者可删」1_003_009_001 限制）
     *
     * @param id 心愿编号
     */
    void deleteWish(Long id);

}
