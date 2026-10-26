package cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 每日一问题目 Response VO（admin 分页元素，M32）
 */
@Schema(description = "管理后台 - 每日一问题目 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveDailyQuestionRespVO {

    @Schema(description = "题目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "题目内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "最近一次让你觉得「有 TA 真好」的瞬间是什么？")
    private String content;

    @Schema(description = "分类", example = "回忆")
    private String category;

    @Schema(description = "状态（0启用 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
