package cn.iocoder.yudao.module.love.service.todo;

import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoRespVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoUpdateReqVO;

import java.util.List;

/**
 * 待办 Service 接口
 *
 * 归属 = couple 共享 + owner 仅记录创建人（INTEGRATION-DESIGN.md §4 love_todo）：
 * 列表对 couple 双方可见，所有按 id 写操作在 service 层强制 couple 归属校验（防 IDOR，§5.2 表头强约束）。
 * 全部方法入口做停用账号收口（§6.5），未绑定 couple 抛 COUPLE_NOT_EXISTS（1_003_004_003）。
 *
 * @author 芋道源码
 */
public interface TodoService {

    /**
     * 获得当前 couple 的全量待办列表（§5.2 GET /love/todo/list）
     *
     * 排序 create_time asc, id asc（mapper 收口），端上整体覆盖 TodoStore（§7.4），
     * admin 删除的端上传播同样走全量覆盖，天然生效（§5.3）
     *
     * @return 待办列表
     */
    List<AppTodoRespVO> getTodoList();

    /**
     * 创建待办：owner_id = 当前用户，done 固定初始 false，couple_id 取当前用户所在 couple
     *
     * tag/color 服务端只存字符串不校验取值（客户端字典，铁律⑥）
     *
     * @param reqVO 创建请求（title/tag/color）
     * @return 创建后的待办（含服务端 id / ownerId / createTime，端上回填 TodoStore）
     */
    AppTodoRespVO createTodo(AppTodoCreateReqVO reqVO);

    /**
     * 更新待办（含 toggle done，全字段保存式：端上整条提交当前状态）
     *
     * 先查 todo 再校验 todo.coupleId == 当前用户 coupleId，不等抛 TODO_NO_PERMISSION（1_003_001_001，防 IDOR）
     *
     * @param reqVO 更新请求（id + title/tag/color/done）
     */
    void updateTodo(AppTodoUpdateReqVO reqVO);

    /**
     * 删除待办（body 传 id）
     *
     * LoveTodoDO 继承 BaseDO 走逻辑删除（与 love_couple 物理删除路线相反）；
     * 归属校验同 update，跨 couple 抛 TODO_NO_PERMISSION（1_003_001_001，防 IDOR）
     *
     * @param reqVO 删除请求（id）
     */
    void deleteTodo(AppTodoIdReqVO reqVO);

}
