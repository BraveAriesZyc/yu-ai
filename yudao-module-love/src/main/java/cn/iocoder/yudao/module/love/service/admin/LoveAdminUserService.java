package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserRespVO;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserUpdateReqVO;

/**
 * C 端用户管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 user 组）
 *
 * 与 app 侧 {@link cn.iocoder.yudao.module.love.service.user.UserProfileService} 分离：
 * admin 不走停用收口（admin 身份体系不同），按物理 id 定位 app 数据域。
 *
 * @author 芋道源码
 */
public interface LoveAdminUserService {

    /**
     * 获得 C 端用户分页
     *
     * @param pageReqVO 分页查询（mobile/nickname 模糊、status 精确、createTime 段）
     * @return C 端用户分页
     */
    PageResult<LoveUserRespVO> getUserPage(LoveUserPageReqVO pageReqVO);

    /**
     * 更新 C 端用户（昵称/头像/停启用）
     *
     * @param reqVO 更新信息（id 必填，其余可选仅更新非空字段）
     */
    void updateUser(LoveUserUpdateReqVO reqVO);

    /**
     * 删除 C 端用户（§5.3 级联策略）
     *
     * 逻辑删除 + mobile 改写 concat(mobile,'#',id) 释放 uk_mobile；
     * 级联：逻辑删除其 todo（owner_id）/ mood（user_id）/ message（sender_id 或 receiver_id），
     * 物理解绑 couple（对方端靠轮询 couple/get 回未绑定态，D-11）
     *
     * @param id 用户编号
     */
    void deleteUser(Long id);

}
