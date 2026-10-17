package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationRespVO;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationSaveReqVO;

/**
 * 恋爱灵感 Service 接口（admin 通道，M17）
 *
 * @author 芋道源码
 */
public interface LoveAdminInspirationService {

    /**
     * 获得恋爱灵感分页
     */
    PageResult<LoveInspirationRespVO> getInspirationPage(LoveInspirationPageReqVO pageReqVO);

    /**
     * 新增灵感，返回带自增 id 的 VO
     */
    LoveInspirationRespVO createInspiration(LoveInspirationSaveReqVO reqVO);

    /**
     * 更新灵感（全字段保存式，null 字段保持原值）
     */
    void updateInspiration(LoveInspirationSaveReqVO reqVO);

    /**
     * 删除灵感（逻辑删除）
     */
    void deleteInspiration(Long id);

}
