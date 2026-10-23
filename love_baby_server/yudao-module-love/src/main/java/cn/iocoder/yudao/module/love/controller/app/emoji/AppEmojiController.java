package cn.iocoder.yudao.module.love.controller.app.emoji;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.emoji.vo.AppEmojiRespVO;
import cn.iocoder.yudao.module.love.service.emoji.EmojiService;
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
 * 聊天表情包 App 接口（M25，表情面板数据源）
 *
 * 全局共享运营内容：无 coupleId/userId 依赖（服务端不反查，无 §5.2 归属约束），
 * 只读端点零错误路径（池空回空数组，端上走内置兜底字典，UI 永不空白）。
 */
@Tag(name = "用户 App - 聊天表情包")
@RestController
@RequestMapping("/love/emoji")
@Validated
public class AppEmojiController {

    @Resource
    private EmojiService emojiService;

    @GetMapping("/list")
    @Operation(summary = "获得表情包全量列表",
            description = "启用行，sort asc, id asc 稳定序全量返回；端上按 category 动态聚合 tab" +
                    "（几十条量级全量拉一次即可，无分页）")
    public CommonResult<List<AppEmojiRespVO>> getEmojiList() {
        return success(emojiService.getEmojiList());
    }

}
