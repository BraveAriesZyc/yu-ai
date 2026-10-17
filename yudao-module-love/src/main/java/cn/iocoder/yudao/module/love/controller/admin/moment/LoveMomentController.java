package cn.iocoder.yudao.module.love.controller.admin.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminMomentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 相册瞬间的 admin 端接口（M9）：供管理后台（admin-api），权限串 love:moment:query|delete。
 * 常规 page + delete 两件套（LoveCheckinController 同款）；无 create/update（瞬间由 App 端发布）。
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 相册瞬间")
@RestController
@RequestMapping("/love/moment")
@Validated
public class LoveMomentController {

    @Resource
    private LoveAdminMomentService momentService;

    @GetMapping("/page")
    @Operation(summary = "获得相册瞬间分页",
            description = "筛选：coupleId 精确 / userId 精确 / 创建时间段（三项冻结口径）")
    @PreAuthorize("@ss.hasPermission('love:moment:query')")
    public CommonResult<PageResult<LoveMomentRespVO>> getMomentPage(LoveMomentPageReqVO pageReqVO) {
        return success(momentService.getMomentPage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除相册瞬间",
            description = "逻辑删除；行不存在 1_003_008_000；图片本体留 infra_file 不级联删")
    @Parameter(name = "id", description = "瞬间编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:moment:delete')")
    public CommonResult<Boolean> deleteMoment(@RequestParam("id") Long id) {
        return success(momentService.deleteMoment(id));
    }

}
