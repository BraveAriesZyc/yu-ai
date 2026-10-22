package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCouplePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCoupleRespVO;

/**
 * 情侣关系管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 couple 组）
 *
 * 与 app 侧 {@link cn.iocoder.yudao.module.love.service.couple.CoupleService} 分离：
 * admin 上下文没有 love_user 身份，解绑按 couple id 定位（不走当前登录人反查）。
 *
 * @author 芋道源码
 */
public interface LoveAdminCoupleService {

    /**
     * 获得情侣关系分页（join love_user 带出双方昵称）
     *
     * @param pageReqVO 分页查询（status 精确 + 双方昵称模糊 + createTime 段）
     * @return 情侣关系分页（含 userANickname / userBNickname）
     */
    PageResult<LoveCoupleRespVO> getCouplePage(LoveCouplePageReqVO pageReqVO);

    /**
     * 强制解绑情侣关系（按 couple id 物理删除，语义与 app unbind 一致，D-11）
     *
     * @param id 情侣关系编号
     */
    void unbindCouple(Long id);

}
