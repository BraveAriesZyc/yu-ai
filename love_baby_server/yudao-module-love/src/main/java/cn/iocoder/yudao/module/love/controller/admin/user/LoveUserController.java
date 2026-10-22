package cn.iocoder.yudao.module.love.controller.admin.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserRespVO;
import cn.iocoder.yudao.module.love.controller.admin.user.vo.LoveUserUpdateReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * C 端用户的管理后台接口（INTEGRATION-DESIGN.md §5.3 user 组）
 *
 * admin 侧不走 app 的停用收口（admin 身份体系不同，无 love_user 上下文），
 * 但对 app 数据域的 id 定位语义不变：按物理 id 定位 + 先校验存在。
 */
@Tag(name = "管理后台 - C 端用户")
@RestController
@RequestMapping("/love/user")
@Validated
public class LoveUserController {

    @Resource
    private LoveAdminUserService loveAdminUserService;

    @GetMapping("/page")
    @Operation(summary = "获得 C 端用户分页",
            description = "mobile / nickname 模糊、status 精确、createTime 段（§5.3）")
    @PreAuthorize("@ss.hasPermission('love:user:query')")
    public CommonResult<PageResult<LoveUserRespVO>> getUserPage(@Valid LoveUserPageReqVO pageReqVO) {
        return success(loveAdminUserService.getUserPage(pageReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新 C 端用户",
            description = "昵称/头像/停启用；停用即时生效路径 = app 各业务 service 入口的停用收口返回 1_003_005_002，端上清会话回 Gate（§6.5）")
    @PreAuthorize("@ss.hasPermission('love:user:update')")
    public CommonResult<Boolean> updateUser(@Valid @RequestBody LoveUserUpdateReqVO reqVO) {
        loveAdminUserService.updateUser(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 C 端用户",
            description = "逻辑删除 + mobile 改写 concat(mobile,'#',id) 释放 uk_mobile；级联：逻辑删除其 todo/mood/message、物理解绑 couple（D-11，对方端靠轮询 couple/get 回未绑定态）")
    @Parameter(name = "id", description = "用户编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:user:delete')")
    public CommonResult<Boolean> deleteUser(@RequestParam("id") Long id) {
        loveAdminUserService.deleteUser(id);
        return success(true);
    }

}
