package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.todo.vo.LoveTodoPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.todo.vo.LoveTodoRespVO;

/**
 * 待办管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 todo 组）
 *
 * admin 侧只有「分页 + 删除」，不做 couple 归属校验（app 侧按 id 写操作的
 * IDOR 防范在 app service 收口，admin 由权限串 love:todo:* 拦截）。
 *
 * @author 芋道源码
 */
public interface LoveAdminTodoService {

    /**
     * 获得待办分页
     *
     * @param pageReqVO 分页查询（coupleId/ownerId 精确 + createTime 段）
     * @return 待办分页
     */
    PageResult<LoveTodoRespVO> getTodoPage(LoveTodoPageReqVO pageReqVO);

    /**
     * 删除待办（逻辑删除；端上传播走全量覆盖，§5.3）
     *
     * @param id 待办编号
     */
    void deleteTodo(Long id);

}
