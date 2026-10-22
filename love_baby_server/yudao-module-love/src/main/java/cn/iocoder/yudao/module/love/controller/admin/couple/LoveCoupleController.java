package cn.iocoder.yudao.module.love.controller.admin.couple;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCouplePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCoupleRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminCoupleService;
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
 * 情侣关系的管理后台接口（INTEGRATION-DESIGN.md §5.3 couple 组）
 *
 * admin 上下文没有 love_user 身份：解绑按 couple id 定位（不走当前登录人反查），
 * 语义与 app unbind 一致 = 物理删除关系行（D-11）。
 */
@Tag(name = "管理后台 - 情侣关系")
@RestController
@RequestMapping("/love/couple")
@Validated
public class LoveCoupleController {

    @Resource
    private LoveAdminCoupleService loveAdminCoupleService;

    @GetMapping("/page")
    @Operation(summary = "获得情侣关系分页",
            description = "status 精确 + 双方昵称模糊（join love_user，SQL 内生效）；邀请码存于 love_user 不作筛选字段（§5.3）")
    @PreAuthorize("@ss.hasPermission('love:couple:query')")
    public CommonResult<PageResult<LoveCoupleRespVO>> getCouplePage(@Valid LoveCouplePageReqVO pageReqVO) {
        return success(loveAdminCoupleService.getCouplePage(pageReqVO));
    }

    @DeleteMapping("/unbind")
    @Operation(summary = "强制解绑情侣关系",
            description = "按 couple id 物理删除关系行（与 app unbind 同语义、按 id 定位）；todo/mood/message 数据保留（D-11）；"
                    + "双方端靠轮询 couple/get 得 1_003_004_003 切回绑定引导态；注意权限串是 love:couple:update（§5.3）")
    @Parameter(name = "id", description = "情侣关系编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:couple:update')")
    public CommonResult<Boolean> unbindCouple(@RequestParam("id") Long id) {
        loveAdminCoupleService.unbindCouple(id);
        return success(true);
    }

}
