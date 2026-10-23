package cn.iocoder.yudao.module.love.controller.admin.emoji.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天表情包 Response VO（admin 分页元素，M25）
 */
@Schema(description = "管理后台 - 聊天表情包 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveEmojiRespVO {

    @Schema(description = "表情编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "分类（App 端动态聚合 tab）", example = "爱意")
    private String category;

    @Schema(description = "emoji 字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "🤗")
    private String emoji;

    @Schema(description = "显示顺序（同分类内 sort asc, id asc）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "状态（0启用 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
