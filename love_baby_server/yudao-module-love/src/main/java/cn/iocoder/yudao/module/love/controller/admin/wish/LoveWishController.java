package cn.iocoder.yudao.module.love.controller.admin.wish;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.wish.vo.LoveWishPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.wish.vo.LoveWishRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminWishService;
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
 * 恋爱清单的管理后台接口（INTEGRATION-DESIGN.md §5.3 wish 组，M14）
 *
 * admin 删除的端上传播走全量覆盖（app GET /love/wish/list 拉取即整体覆盖，天然生效，§5.3）。
 */
@Tag(name = "管理后台 - 恋爱清单")
@RestController
@RequestMapping("/love/wish")
@Validated
public class LoveWishController {

    @Resource
    private LoveAdminWishService loveAdminWishService;

    @GetMapping("/page")
    @Operation(summary = "获得心愿分页",
            description = "coupleId / userId 精确 + createTime 段（§5.3）")
    @PreAuthorize("@ss.hasPermission('love:wish:query')")
    public CommonResult<PageResult<LoveWishRespVO>> getWishPage(@Valid LoveWishPageReqVO pageReqVO) {
        return success(loveAdminWishService.getWishPage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除心愿",
            description = "逻辑删除（@TableLogic → UPDATE deleted = 1）；admin 兜底不受「仅创建者可删」限制；" +
                    "端上传播走全量覆盖（§5.3）")
    @Parameter(name = "id", description = "心愿编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:wish:delete')")
    public CommonResult<Boolean> deleteWish(@RequestParam("id") Long id) {
        loveAdminWishService.deleteWish(id);
        return success(true);
    }

}
