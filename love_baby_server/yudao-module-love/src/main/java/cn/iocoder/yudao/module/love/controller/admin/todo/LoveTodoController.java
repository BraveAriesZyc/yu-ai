package cn.iocoder.yudao.module.love.controller.admin.todo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.todo.vo.LoveTodoPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.todo.vo.LoveTodoRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminTodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 待办的管理后台接口（INTEGRATION-DESIGN.md §5.3 todo 组）
 *
 * admin 删除的端上传播走全量覆盖（app GET /love/todo/list 拉取即整体覆盖 TodoStore，天然生效，§5.3）。
 */
@Tag(name = "管理后台 - 待办")
@RestController
@RequestMapping("/love/todo")
@Validated
public class LoveTodoController {

    @Resource
    private LoveAdminTodoService loveAdminTodoService;

    @GetMapping("/page")
    @Operation(summary = "获得待办分页",
            description = "coupleId / ownerId 精确 + createTime 段（§5.3）")
    @PreAuthorize("@ss.hasPermission('love:todo:query')")
    public CommonResult<PageResult<LoveTodoRespVO>> getTodoPage(@Valid LoveTodoPageReqVO pageReqVO) {
        return success(loveAdminTodoService.getTodoPage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除待办",
            description = "逻辑删除（@TableLogic → UPDATE deleted = 1）；端上传播走全量覆盖（§5.3）")
    @Parameter(name = "id", description = "待办编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:todo:delete')")
    public CommonResult<Boolean> deleteTodo(@RequestParam("id") Long id) {
        loveAdminTodoService.deleteTodo(id);
        return success(true);
    }

}
