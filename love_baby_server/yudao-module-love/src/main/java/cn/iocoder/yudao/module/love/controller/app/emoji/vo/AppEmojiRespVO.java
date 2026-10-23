package cn.iocoder.yudao.module.love.controller.app.emoji.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天表情包 Response VO（M25，app /list 列表元素）
 *
 * Long id 序列化为字符串（App 端统一字符串 id 口径）；category 可能为空（运营未填），
 * 端上空串归「常用」桶；不含 sort/审计列（端上按回包顺序渲染不重排）。
 */
@Schema(description = "用户 App - 聊天表情包 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppEmojiRespVO {

    @Schema(description = "表情编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "分类（App 端动态聚合 tab，空串归「常用」）", example = "爱意")
    private String category;

    @Schema(description = "emoji 字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "🤗")
    private String emoji;

}
