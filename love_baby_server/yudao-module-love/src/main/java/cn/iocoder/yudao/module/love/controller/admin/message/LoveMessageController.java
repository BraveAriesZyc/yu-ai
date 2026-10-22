package cn.iocoder.yudao.module.love.controller.admin.message;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.message.vo.LoveMessagePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.message.vo.LoveMessageRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminMessageService;
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
 * 悄悄话消息的管理后台接口（INTEGRATION-DESIGN.md §5.3 message 组）
 *
 * admin 删除已拉取消息在端上【残留】（lastId 只增不减），显式接受该限制（D-12）；
 * 后续如需消除，引入数据版本号触发全量重拉（二期评估，§12.4）。
 */
@Tag(name = "管理后台 - 悄悄话")
@RestController
@RequestMapping("/love/message")
@Validated
public class LoveMessageController {

    @Resource
    private LoveAdminMessageService loveAdminMessageService;

    @GetMapping("/page")
    @Operation(summary = "获得悄悄话消息分页",
            description = "coupleId / kind 精确 + content 关键字模糊 + createTime 段（审计四件套，§5.3）")
    @PreAuthorize("@ss.hasPermission('love:message:query')")
    public CommonResult<PageResult<LoveMessageRespVO>> getMessagePage(@Valid LoveMessagePageReqVO pageReqVO) {
        return success(loveAdminMessageService.getMessagePage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除悄悄话消息",
            description = "逻辑删除（@TableLogic → UPDATE deleted = 1）；端上传播策略见 D-12（已拉取消息残留，显式接受）")
    @Parameter(name = "id", description = "消息编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:message:delete')")
    public CommonResult<Boolean> deleteMessage(@RequestParam("id") Long id) {
        loveAdminMessageService.deleteMessage(id);
        return success(true);
    }

}
