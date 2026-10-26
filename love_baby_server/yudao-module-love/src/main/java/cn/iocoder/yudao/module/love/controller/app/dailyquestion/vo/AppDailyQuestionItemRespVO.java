package cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 每日一问历史条目 Response VO（GET /love/daily-question/list，M32）
 *
 * 字段形态与今日题一致（过去某天的当日题 + 双方作答）；partnerAnswer 同样仅 revealed=true
 * 时非 null（错过没答的天，对方答案永不揭晓——只能答当天，D-21）。
 */
@Schema(description = "用户 App - 每日一问历史条目 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppDailyQuestionItemRespVO {

    @Schema(description = "题目日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-25")
    private LocalDate questionDate;

    @Schema(description = "题目内容（day 快照）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "如果明天一起去旅行，你想去哪？")
    private String content;

    @Schema(description = "分类（day 快照）", example = "未来")
    private String category;

    @Schema(description = "我的答案内容（null = 当天没答）", example = "想去海边的小城，什么都不干躺着就行")
    private String myAnswer;

    @Schema(description = "对方是否已答（答案内容揭晓前不下发）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean partnerAnswered;

    @Schema(description = "对方的答案内容（仅 revealed=true 时非 null）", example = "想去你念叨很久的那家湖边民宿")
    private String partnerAnswer;

    @Schema(description = "是否已揭晓（双方都提交过 = true）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean revealed;

}
