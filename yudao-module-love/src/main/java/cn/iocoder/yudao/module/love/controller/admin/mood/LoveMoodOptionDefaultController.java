package cn.iocoder.yudao.module.love.controller.admin.mood;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultRespVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultSaveReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminMoodOptionDefaultService;
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
 * 心情默认选项模板的管理后台接口（INTEGRATION-DESIGN.md §5.3 / D-14，M3.5）
 *
 * 管理对象 = 绑定播种源（status=启用 行复制给新 couple 的私有字典）；
 * 模板改动只影响之后的新绑定，不回灌已绑定情侣（D-14 显式声明）；admin-api RESTful 不受限（§5.1）。
 */
@Tag(name = "管理后台 - 心情默认选项")
@RestController
@RequestMapping("/love/mood-option-default")
@Validated
public class LoveMoodOptionDefaultController {

    @Resource
    private LoveAdminMoodOptionDefaultService loveAdminMoodOptionDefaultService;

    @GetMapping("/page")
    @Operation(summary = "获得心情默认选项分页",
            description = "label 模糊 / status 精确；排序 sort asc, id asc（效果序即管理序）")
    @PreAuthorize("@ss.hasPermission('love:mood-option-default:query')")
    public CommonResult<PageResult<LoveMoodOptionDefaultRespVO>> getMoodOptionDefaultPage(
            @Valid LoveMoodOptionDefaultPageReqVO pageReqVO) {
        return success(loveAdminMoodOptionDefaultService.getMoodOptionDefaultPage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增心情默认选项",
            description = "status 未传落库默认启用；之后的新绑定将播种本行")
    @PreAuthorize("@ss.hasPermission('love:mood-option-default:create')")
    public CommonResult<LoveMoodOptionDefaultRespVO> createMoodOptionDefault(
            @Valid @RequestBody LoveMoodOptionDefaultSaveReqVO reqVO) {
        return success(loveAdminMoodOptionDefaultService.createMoodOptionDefault(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新心情默认选项",
            description = "按 id 全字段保存（null 字段保持原值）；只影响之后的新绑定，不回灌存量 couple（D-14）")
    @PreAuthorize("@ss.hasPermission('love:mood-option-default:update')")
    public CommonResult<Boolean> updateMoodOptionDefault(@Valid @RequestBody LoveMoodOptionDefaultSaveReqVO reqVO) {
        loveAdminMoodOptionDefaultService.updateMoodOptionDefault(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除心情默认选项",
            description = "逻辑删除；不影响任何已绑定 couple 的私有字典（播种即复制）；该档不再参与之后的新绑定播种")
    @Parameter(name = "id", description = "默认选项编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('love:mood-option-default:delete')")
    public CommonResult<Boolean> deleteMoodOptionDefault(@RequestParam("id") Long id) {
        loveAdminMoodOptionDefaultService.deleteMoodOptionDefault(id);
        return success(true);
    }

}
