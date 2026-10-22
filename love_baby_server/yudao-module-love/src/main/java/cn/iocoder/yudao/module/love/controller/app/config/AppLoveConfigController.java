package cn.iocoder.yudao.module.love.controller.app.config;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.service.config.LoveConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 业务配置 App 接口（M23，love 业务专属 KV 配置读取面）
 *
 * 全局共享运营配置：无 coupleId/userId 依赖（服务端不反查，无 §5.2 归属约束），
 * 只读端点零错误路径（空表/全停用回空 Map，端上 LoveConfigStore 对缺键走内置
 * 兜底值）；回包恒对象形态（configKey→configValue 映射，Map 序列化为 JSON 对象
 * ——端上 HttpClient toOk 只分派对象/数组，标量落空对象——M11 冻结口径）。
 * 首个消费方：我的页「帮助与客服」行复制的客服微信号（customer_service_wechat）。
 */
@Tag(name = "用户 App - 业务配置")
@RestController
@RequestMapping("/love/config")
@Validated
public class AppLoveConfigController {

    @Resource
    private LoveConfigService loveConfigService;

    @GetMapping("/list")
    @Operation(summary = "获得全部启用配置",
            description = "configKey → configValue 映射，仅启用行（停用/已删不下发，端上走内置兜底）；" +
                    "十几行量级 KV 全量返回无分页；端上进页/回前台全量拉取，改配置下一前台周期可见")
    public CommonResult<Map<String, String>> getConfigMap() {
        return success(loveConfigService.getConfigMap());
    }

}
