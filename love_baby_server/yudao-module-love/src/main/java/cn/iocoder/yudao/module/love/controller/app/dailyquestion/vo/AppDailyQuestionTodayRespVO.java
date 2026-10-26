package cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 每日一问今日题 Response VO（GET /love/daily-question/today 与 POST /love/daily-question/answer 共用，
 * M32；答题后即时反映最新互见态）
 *
 * 隐私口径（D-21 自动互见）：partnerAnswer 仅 revealed=true（双方都已提交）时非 null——
 * 揭晓前对方答案永不下发（只给 partnerAnswered 布尔供「等 TA」状态文案），防接口层扒答案。
 * 今日无题（题库空且当日题未生成）= 全字段 null/false，端上显「今日无题」占位。
 */
@Schema(description = "用户 App - 每日一问今日题 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppDailyQuestionTodayRespVO {

    @Schema(description = "题目日期（=今天；今日无题时为 null）", example = "2026-10-26")
    private LocalDate questionDate;

    @Schema(description = "题目内容（day 快照；今日无题时为 null）",
            example = "最近一次让你觉得「有 TA 真好」的瞬间是什么？")
    private String content;

    @Schema(description = "分类（day 快照，自由文本）", example = "回忆")
    private String category;

    @Schema(description = "我的答案内容（null = 未答）", example = "上周发烧到 39 度，TA 半夜爬起来给我物理降温")
    private String myAnswer;

    @Schema(description = "对方是否已答（答案内容揭晓前不下发，供「等 TA」状态文案）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean partnerAnswered;

    @Schema(description = "对方的答案内容（仅 revealed=true 时非 null，揭晓前恒 null）", example = "每次加班回家桌上都有热着的饭")
    private String partnerAnswer;

    @Schema(description = "是否已揭晓（双方都已提交 = true，揭晓即锁定不可改）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean revealed;

}
