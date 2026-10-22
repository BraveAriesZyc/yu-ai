package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.anniversary.vo.LoveAnniversaryPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.anniversary.vo.LoveAnniversaryRespVO;

/**
 * 纪念日管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 anniversary 组，M5）
 *
 * admin 侧只有「分页 + 删除」，不做 couple 归属校验（app 侧按 id 写操作的
 * IDOR 防范在 app service 收口，admin 由权限串 love:anniversary:* 拦截）。
 *
 * @author 芋道源码
 */
public interface LoveAdminAnniversaryService {

    /**
     * 获得纪念日分页
     *
     * @param pageReqVO 分页查询（title 模糊 + createTime 段）
     * @return 纪念日分页
     */
    PageResult<LoveAnniversaryRespVO> getAnniversaryPage(LoveAnniversaryPageReqVO pageReqVO);

    /**
     * 删除纪念日（逻辑删除；端上传播走全量覆盖，§5.3）
     *
     * @param id 纪念日编号
     */
    void deleteAnniversary(Long id);

}
