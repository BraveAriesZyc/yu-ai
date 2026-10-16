package cn.iocoder.yudao.module.love.controller.app.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单日心情 Response VO（GET /love/mood/record/list?month= 数组元素）
 *
 * id/userId 服务端 Long，经 NumberSerializer 序列化（超 2^53-1 转字符串，§5.1），端上一律按 String 解析；
 * moodDate 为 LocalDate，走仓内 jsr310 LocalDateSerializer（Jackson 3 默认 ISO，无时间戳数组），
 * 回包形如 "2026-10-16"，端上按原样字符串存取、月份过滤由 Store 层字符串比对（MoodModels 同款约定）。
 *
 * M3.5 只加字段（§12.5.1）：optionId + 快照三列（moodLabel/emoji/color）——端上月历渲染改吃快照，
 * 删除选项后历史不受影响（D-14）；moodType 保留回存量旧值（1-5，M3.5 起新记录恒 NULL，端上兜底用）。
 */
@Schema(description = "用户 App - 单日心情 Response VO，某日无记录 = 回包无该日条目，端上按空缺渲染")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMoodRespVO {

    @Schema(description = "心情记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "记录用户编号（端上据以区分本人/伴侣）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "心情日期（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-16")
    private LocalDate moodDate;

    @Schema(description = "（废弃）旧 1-5 心情枚举，仅存量行有值（M3.5 起新记录恒 NULL，端上兜底渲染用）", example = "1")
    private Integer moodType;

    @Schema(description = "心情选项编号（记录时点快照；选项可能已被删除，渲染勿依赖字典回查）", example = "1024")
    private Long optionId;

    @Schema(description = "心情名快照（记录时点）", example = "元气满满")
    private String moodLabel;

    @Schema(description = "表情快照（记录时点，utf8mb4 文本原样透传）", example = "😄")
    private String emoji;

    @Schema(description = "颜色 hex 快照（记录时点，原样透传）", example = "#FFB93C")
    private String color;

    /**
     * 记录时刻（epoch 毫秒，端上时段徽标用）：LocalDateTime 走仓内 TimestampLocalDateTimeSerializer
     * （yudao 全局惯例，AppMessageRespVO.createTime 同机制）。语义注意：uk_user_date 重复记录走覆盖写
     * （UpdateWrapper 只更快照列，不碰 create_time）——本字段 = 当日【首次】记录时刻，非最后修改时刻
     */
    @Schema(description = "记录时刻（epoch 毫秒；同日重复记录为覆盖写，本值 = 当日首次记录时刻）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1729045200000")
    private LocalDateTime createTime;

}
