package cn.iocoder.yudao.module.love.controller.admin.config;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigRespVO;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigSaveReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminConfigService;
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
 * 业务配置的管理后台接口（M23）
 *
 * 管理对象 = love 业务专属 KV 配置（全局共享，B 端配置 App 只读）；首个配置项 =
 * 客服微信号（customer_service_wechat，App 我的页「帮助与客服」行复制引导），
 * 后续运营配置直接加行（configKey 即 App 读取契约）。configKey 唯一性由
 * service 层校验（1_003_012_001），本表无 DB uk（逻辑删除行残留 + uk 挡同键重建）。
 * admin-api RESTful 不受限（§5.1，inspiration 先例：create POST / update PUT）。
 */
@Tag(name = "管理后台 - 业务配置")
@RestController
@RequestMapping("/love/config")
@Validated
public class LoveConfigController {

    @Resource
    private LoveAdminConfigService loveAdminConfigService;

    @GetMapping("/page")
    @Operation(summary = "获得业务配置分页",
            description = "名称/配置键模糊 / status 精确 / 创建时间段；排序 id desc（新添加在前）")
    @PreAuthorize("@ss.hasPermission('love:config:query')")
    public CommonResult<PageResult<LoveConfigRespVO>> getConfigPage(
            @Valid LoveConfigPageReqVO pageReqVO) {
        return success(loveAdminConfigService.getConfigPage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增配置",
            description = "config_key 唯一（1_003_012_001 拦截）；status 未传落库默认启用；即时生效（App 下一轮拉取可见）")
    @PreAuthorize("@ss.hasPermission('love:config:create')")
    public CommonResult<LoveConfigRespVO> createConfig(
            @Valid @RequestBody LoveConfigSaveReqVO reqVO) {
        return success(loveAdminConfigService.createConfig(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新配置",
            description = "按 id 全字段保存（null 字段保持原值）；⚠ configKey 是 App 读取契约，改动等于换配置项")
    @PreAuthorize("@ss.hasPermission('love:config:update')")
    public CommonResult<Boolean> updateConfig(@Valid @RequestBody LoveConfigSaveReqVO reqVO) {
        loveAdminConfigService.updateConfig(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除配置",
            description = "逻辑删除；App 下一轮拉取即少该键，端上走内置兜底值")
    @Parameter(name = "id", description = "配置编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('love:config:delete')")
    public CommonResult<Boolean> deleteConfig(@RequestParam("id") Long id) {
        loveAdminConfigService.deleteConfig(id);
        return success(true);
    }

}
