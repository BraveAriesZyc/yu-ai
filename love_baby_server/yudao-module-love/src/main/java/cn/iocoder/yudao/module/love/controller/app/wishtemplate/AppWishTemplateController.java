package cn.iocoder.yudao.module.love.controller.app.wishtemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.wishtemplate.vo.AppWishTemplateRespVO;
import cn.iocoder.yudao.module.love.service.wishtemplate.WishTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 心愿墙挑选模板 App 接口（M25，心情页心愿墙挑选列表数据源）
 *
 * 全局共享运营内容：无 coupleId/userId 依赖（服务端不反查，无 §5.2 归属约束），
 * 只读端点零错误路径（池空回空数组，端上走内置兜底字典，UI 永不空白）。
 * ⚠ 本表不参与消息渲染校验：sendWishes 上送快照四字段，历史消息不查本表。
 */
@Tag(name = "用户 App - 心愿墙挑选模板")
@RestController
@RequestMapping("/love/wish-template")
@Validated
public class AppWishTemplateController {

    @Resource
    private WishTemplateService wishTemplateService;

    @GetMapping("/list")
    @Operation(summary = "获得心愿模板全量列表",
            description = "启用行，sort asc, id asc 稳定序全量返回；端上按 category 动态聚合 chips" +
                    "（几十条量级全量拉一次即可，无分页）")
    public CommonResult<List<AppWishTemplateRespVO>> getWishTemplateList() {
        return success(wishTemplateService.getWishTemplateList());
    }

}
