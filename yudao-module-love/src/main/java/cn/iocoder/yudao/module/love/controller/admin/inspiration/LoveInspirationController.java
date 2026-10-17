package cn.iocoder.yudao.module.love.controller.admin.inspiration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationRespVO;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationSaveReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminInspirationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 恋爱灵感的管理后台接口（M17）
 *
 * 管理对象 = 首页「今日情话」「约会灵感」两卡的内容池（全局共享，B 端配置 App 只读）；
 * admin-api RESTful 不受限（§5.1，mood-option-default 先例：create POST / update PUT）。
 */
@Tag(name = "管理后台 - 恋爱灵感")
@RestController
@RequestMapping("/love/inspiration")
@Validated
public class LoveInspirationController {

    @Resource
    private LoveAdminInspirationService loveAdminInspirationService;

    @GetMapping("/page")
    @Operation(summary = "获得恋爱灵感分页",
            description = "内容模糊 / kind 精确 / status 精确 / 创建时间段；排序 id desc（新添加在前）")
    @PreAuthorize("@ss.hasPermission('love:inspiration:query')")
    public CommonResult<PageResult<LoveInspirationRespVO>> getInspirationPage(
            @Valid LoveInspirationPageReqVO pageReqVO) {
        return success(loveAdminInspirationService.getInspirationPage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增灵感",
            description = "status 未传落库默认启用；即时生效（App 只读，下次请求可见）")
    @PreAuthorize("@ss.hasPermission('love:inspiration:create')")
    public CommonResult<LoveInspirationRespVO> createInspiration(
            @Valid @RequestBody LoveInspirationSaveReqVO reqVO) {
        return success(loveAdminInspirationService.createInspiration(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新灵感",
            description = "按 id 全字段保存（null 字段保持原值）；停用行立即从 App 两端点消失")
    @PreAuthorize("@ss.hasPermission('love:inspiration:update')")
    public CommonResult<Boolean> updateInspiration(@Valid @RequestBody LoveInspirationSaveReqVO reqVO) {
        loveAdminInspirationService.updateInspiration(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除灵感",
            description = "逻辑删除；情话池删除后「今日情话」自动顺延到下一条（取模口径）")
    @Parameter(name = "id", description = "灵感编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('love:inspiration:delete')")
    public CommonResult<Boolean> deleteInspiration(@RequestParam("id") Long id) {
        loveAdminInspirationService.deleteInspiration(id);
        return success(true);
    }

}
