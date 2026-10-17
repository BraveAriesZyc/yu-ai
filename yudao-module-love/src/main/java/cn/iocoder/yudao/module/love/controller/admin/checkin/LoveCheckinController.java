package cn.iocoder.yudao.module.love.controller.admin.checkin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.checkin.vo.LoveCheckinPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.checkin.vo.LoveCheckinRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminCheckinService;
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
 * 每日打卡的管理后台接口（INTEGRATION-DESIGN.md §5.3 checkin 组，M8）
 *
 * admin 侧只有「分页 + 删除」，无新增/编辑（app 侧只打今天，admin 也不能代打/补打）；
 * admin 删除的端上传播：app 月历走 loadMonth 整月覆盖、summary 走重新拉取，天然生效（§5.3）。
 */
@Tag(name = "管理后台 - 每日打卡")
@RestController
@RequestMapping("/love/checkin")
@Validated
public class LoveCheckinController {

    @Resource
    private LoveAdminCheckinService loveAdminCheckinService;

    @GetMapping("/page")
    @Operation(summary = "获得打卡分页",
            description = "userId 精确 + checkinDate 段（M8 冻结口径）")
    @PreAuthorize("@ss.hasPermission('love:checkin:query')")
    public CommonResult<PageResult<LoveCheckinRespVO>> getCheckinPage(@Valid LoveCheckinPageReqVO pageReqVO) {
        return success(loveAdminCheckinService.getCheckinPage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除打卡记录",
            description = "逻辑删除（@TableLogic → UPDATE deleted = 1）；端上传播走整月覆盖/汇总重拉（§5.3）")
    @Parameter(name = "id", description = "打卡编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:checkin:delete')")
    public CommonResult<Boolean> deleteCheckin(@RequestParam("id") Long id) {
        loveAdminCheckinService.deleteCheckin(id);
        return success(true);
    }

}
