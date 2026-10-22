package cn.iocoder.yudao.module.love.controller.app.todo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoRespVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoUpdateReqVO;
import cn.iocoder.yudao.module.love.service.todo.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 待办的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户/couple 由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId；
 * 按 id 写操作在 service 层强制 couple 归属校验（跨 couple 返回 1_003_001_001，防 IDOR，§5.2）。
 *
 * 写端点统一 POST 而非 RESTful 的 PUT/DELETE（M2 实施修正，§5.2 user 组注记）：
 * Kuikly core 2.29 NetworkModule 仅支持 GET/POST（反编译 AAR 核实），delete 的 body 传 id 同理。
 */
@Tag(name = "用户 App - 待办")
@RestController
@RequestMapping("/love/todo")
@Validated
public class AppTodoController {

    @Resource
    private TodoService todoService;

    @GetMapping("/list")
    @Operation(summary = "获得当前 couple 的全量待办列表",
            description = "按 create_time asc, id asc 排序，端上整体覆盖 TodoStore（§7.4）；未绑定 couple 返回 1_003_004_003")
    public CommonResult<List<AppTodoRespVO>> getTodoList() {
        return success(todoService.getTodoList());
    }

    @PostMapping("/create")
    @Operation(summary = "创建待办",
            description = "owner_id = 当前用户（双方均可见可操作），done 固定初始 false；tag/color 服务端只存字符串不校验取值（客户端字典，铁律⑥）；回包含服务端 id/ownerId/createTime，端上回填 TodoStore")
    public CommonResult<AppTodoRespVO> createTodo(@RequestBody @Valid AppTodoCreateReqVO reqVO) {
        return success(todoService.createTodo(reqVO));
    }

    @PostMapping("/update")
    @Operation(summary = "更新待办（含 toggle done，全字段保存式）",
            description = "端上整条提交当前状态；先查 todo 再校验 couple 归属，跨 couple 返回 1_003_001_001（防 IDOR）")
    public CommonResult<Boolean> updateTodo(@RequestBody @Valid AppTodoUpdateReqVO reqVO) {
        todoService.updateTodo(reqVO);
        return success(true);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除待办（body 传 id）",
            description = "LoveTodoDO 继承 BaseDO 走逻辑删除；admin 删除的端上传播靠 list 全量覆盖，天然生效（§5.3）；归属校验同 update（防 IDOR）")
    public CommonResult<Boolean> deleteTodo(@RequestBody @Valid AppTodoIdReqVO reqVO) {
        todoService.deleteTodo(reqVO);
        return success(true);
    }

}
