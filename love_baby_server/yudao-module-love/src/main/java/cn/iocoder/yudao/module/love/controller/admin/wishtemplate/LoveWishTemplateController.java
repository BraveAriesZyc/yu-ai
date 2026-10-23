package cn.iocoder.yudao.module.love.controller.admin.wishtemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplatePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplateRespVO;
import cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo.LoveWishTemplateSaveReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminWishTemplateService;
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
 * 心愿墙挑选模板的管理后台接口（M25）
 *
 * 管理对象 = 心情页心愿墙的挑选模板（全局共享，B 端配置 App 只读）；
 * admin-api RESTful 不受限（§5.1，inspiration 先例：create POST / update PUT）。
 */
@Tag(name = "管理后台 - 心愿墙挑选模板")
@RestController
@RequestMapping("/love/wish-template")
@Validated
public class LoveWishTemplateController {

    @Resource
    private LoveAdminWishTemplateService loveAdminWishTemplateService;

    @GetMapping("/page")
    @Operation(summary = "获得心愿模板分页",
            description = "分类/标题模糊 / status 精确 / 创建时间段；排序 id desc（新添加在前）")
    @PreAuthorize("@ss.hasPermission('love:wish-template:query')")
    public CommonResult<PageResult<LoveWishTemplateRespVO>> getWishTemplatePage(
            @Valid LoveWishTemplatePageReqVO pageReqVO) {
        return success(loveAdminWishTemplateService.getWishTemplatePage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增模板",
            description = "status 未传落库默认启用；即时生效（App 只读，下次请求可见）")
    @PreAuthorize("@ss.hasPermission('love:wish-template:create')")
    public CommonResult<LoveWishTemplateRespVO> createWishTemplate(
            @Valid @RequestBody LoveWishTemplateSaveReqVO reqVO) {
        return success(loveAdminWishTemplateService.createWishTemplate(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新模板",
            description = "按 id 全字段保存（null 字段保持原值）；停用行立即从 App 端点消失")
    @PreAuthorize("@ss.hasPermission('love:wish-template:update')")
    public CommonResult<Boolean> updateWishTemplate(@Valid @RequestBody LoveWishTemplateSaveReqVO reqVO) {
        loveAdminWishTemplateService.updateWishTemplate(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模板",
            description = "逻辑删除；kind=2 心愿消息为快照式上送，删模板不影响历史消息")
    @Parameter(name = "id", description = "模板编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('love:wish-template:delete')")
    public CommonResult<Boolean> deleteWishTemplate(@RequestParam("id") Long id) {
        loveAdminWishTemplateService.deleteWishTemplate(id);
        return success(true);
    }

}
