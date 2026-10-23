package cn.iocoder.yudao.module.love.controller.admin.emoji;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiRespVO;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiSaveReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminEmojiService;
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
 * 聊天表情包的管理后台接口（M25）
 *
 * 管理对象 = 聊天表情面板的表情字典（全局共享，B 端配置 App 只读）；
 * admin-api RESTful 不受限（§5.1，inspiration 先例：create POST / update PUT）。
 */
@Tag(name = "管理后台 - 聊天表情包")
@RestController
@RequestMapping("/love/emoji")
@Validated
public class LoveEmojiController {

    @Resource
    private LoveAdminEmojiService loveAdminEmojiService;

    @GetMapping("/page")
    @Operation(summary = "获得表情包分页",
            description = "分类模糊 / status 精确 / 创建时间段；排序 id desc（新添加在前）")
    @PreAuthorize("@ss.hasPermission('love:emoji:query')")
    public CommonResult<PageResult<LoveEmojiRespVO>> getEmojiPage(
            @Valid LoveEmojiPageReqVO pageReqVO) {
        return success(loveAdminEmojiService.getEmojiPage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增表情",
            description = "status 未传落库默认启用；即时生效（App 只读，下次请求可见）")
    @PreAuthorize("@ss.hasPermission('love:emoji:create')")
    public CommonResult<LoveEmojiRespVO> createEmoji(
            @Valid @RequestBody LoveEmojiSaveReqVO reqVO) {
        return success(loveAdminEmojiService.createEmoji(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新表情",
            description = "按 id 全字段保存（null 字段保持原值）；停用行立即从 App 端点消失")
    @PreAuthorize("@ss.hasPermission('love:emoji:update')")
    public CommonResult<Boolean> updateEmoji(@Valid @RequestBody LoveEmojiSaveReqVO reqVO) {
        loveAdminEmojiService.updateEmoji(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除表情",
            description = "逻辑删除；表情为 TEXT 链快照，历史消息渲染零影响")
    @Parameter(name = "id", description = "表情编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('love:emoji:delete')")
    public CommonResult<Boolean> deleteEmoji(@RequestParam("id") Long id) {
        loveAdminEmojiService.deleteEmoji(id);
        return success(true);
    }

}
