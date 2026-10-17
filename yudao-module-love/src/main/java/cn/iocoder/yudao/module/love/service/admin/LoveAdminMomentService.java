package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentRespVO;

/**
 * 相册瞬间的 admin Service 接口（M9）
 *
 * @author 芋道源码
 */
public interface LoveAdminMomentService {

    /**
     * 获得相册瞬间分页
     *
     * @param pageReqVO 分页请求（coupleId/userId 精确 + createTime 时间段）
     * @return 瞬间分页结果
     */
    PageResult<LoveMomentRespVO> getMomentPage(LoveMomentPageReqVO pageReqVO);

    /**
     * 删除相册瞬间（逻辑删除）
     *
     * @param id 瞬间编号
     * @return 是否成功
     */
    Boolean deleteMoment(Long id);

}
