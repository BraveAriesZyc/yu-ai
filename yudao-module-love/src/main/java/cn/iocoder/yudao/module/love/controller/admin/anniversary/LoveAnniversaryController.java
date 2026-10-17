package cn.iocoder.yudao.module.love.controller.admin.anniversary;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.anniversary.vo.LoveAnniversaryPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.anniversary.vo.LoveAnniversaryRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminAnniversaryService;
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
 * 纪念日的管理后台接口（INTEGRATION-DESIGN.md §5.3 anniversary 组，M5）
 *
 * admin 侧只有「分页 + 删除」，无新增/编辑（与 todo 域同口径）；admin 删除的端上传播走
 * 全量覆盖（app GET /love/anniversary/list 拉取即整体覆盖 AnniversaryStore，天然生效，§5.3）。
 */
@Tag(name = "管理后台 - 纪念日")
@RestController
@RequestMapping("/love/anniversary")
@Validated
public class LoveAnniversaryController {

    @Resource
    private LoveAdminAnniversaryService loveAdminAnniversaryService;

    @GetMapping("/page")
    @Operation(summary = "获得纪念日分页",
            description = "title 模糊 + createTime 段（M5 冻结口径）")
    @PreAuthorize("@ss.hasPermission('love:anniversary:query')")
    public CommonResult<PageResult<LoveAnniversaryRespVO>> getAnniversaryPage(@Valid LoveAnniversaryPageReqVO pageReqVO) {
        return success(loveAdminAnniversaryService.getAnniversaryPage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除纪念日",
            description = "逻辑删除（@TableLogic → UPDATE deleted = 1）；端上传播走全量覆盖（§5.3）")
    @Parameter(name = "id", description = "纪念日编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:anniversary:delete')")
    public CommonResult<Boolean> deleteAnniversary(@RequestParam("id") Long id) {
        loveAdminAnniversaryService.deleteAnniversary(id);
        return success(true);
    }

}
