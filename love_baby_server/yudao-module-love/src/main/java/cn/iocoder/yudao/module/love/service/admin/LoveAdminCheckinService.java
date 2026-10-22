package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.checkin.vo.LoveCheckinPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.checkin.vo.LoveCheckinRespVO;

/**
 * 每日打卡管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 checkin 组，M8）
 *
 * admin 侧只有「分页 + 删除」，不做 couple 归属校验（app 侧无按 id 写端点，IDOR 面不存在，
 * admin 由权限串 love:checkin:* 拦截）。
 *
 * @author 芋道源码
 */
public interface LoveAdminCheckinService {

    /**
     * 获得打卡分页
     *
     * @param pageReqVO 分页查询（userId 精确 + checkinDate 段）
     * @return 打卡分页
     */
    PageResult<LoveCheckinRespVO> getCheckinPage(LoveCheckinPageReqVO pageReqVO);

    /**
     * 删除打卡记录（逻辑删除；端上传播走整月覆盖/汇总重拉，§5.3）
     *
     * @param id 打卡编号
     */
    void deleteCheckin(Long id);

}
