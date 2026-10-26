package cn.iocoder.yudao.module.love.controller.app.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 恋爱月报 Response VO（GET /love/report/monthly 消费方 = App「恋爱月报」页，M30）
 *
 * 五域单月只读聚合：打卡/心情/悄悄话/相册/清单 + 在一起天数。全部真数据（逻辑删不计、
 * 撤回墓碑不计、解绑重绑历史行不计），未绑定全 0（照 AppUserStatsRespVO 口径，不抛异常）。
 * 必须是对象 VO——端上 HttpClient toOk 只分派对象/数组，标量落空对象（M11 冻结决策）。
 */
@Schema(description = "用户 App - 恋爱月报 Response VO")
@Data
public class AppReportMonthlyRespVO {

    @Schema(description = "月份 yyyy-MM（服务端解析后回填，端上不自行推导）", example = "2026-10")
    private String month;

    @Schema(description = "在一起天数（含绑定当天，未绑定为 0）", example = "520")
    private Integer bindDays;

    @Schema(description = "我本月打卡天数", example = "21")
    private Long myCheckinDays;

    @Schema(description = "TA 本月打卡天数", example = "19")
    private Long partnerCheckinDays;

    @Schema(description = "本月双方同日打卡天数（共同点亮）", example = "17")
    private Long togetherCheckinDays;

    @Schema(description = "本月心情记录条数（双方合计）", example = "34")
    private Long moodCount;

    @Schema(description = "本月心情分布 Top3（按 mood_label 快照聚合，不受选项删除影响；可为空数组）")
    private List<TopMood> topMoods;

    @Schema(description = "本月悄悄话条数（排除已撤回墓碑行，M18 口径）", example = "356")
    private Long messageCount;

    @Schema(description = "本月相册瞬间张数", example = "12")
    private Long momentCount;

    @Schema(description = "本月完成心愿数（按 done_time 落月计）", example = "5")
    private Long wishDoneCount;

    @Schema(description = "心情分布单项")
    @Data
    public static class TopMood {

        @Schema(description = "心情文案（记录时快照）", example = "开心")
        private String label;

        @Schema(description = "emoji（记录时快照）", example = "😄")
        private String emoji;

        @Schema(description = "色值（记录时快照，#RRGGBB）", example = "#34C77B")
        private String color;

        @Schema(description = "该心情条数", example = "14")
        private Long count;

    }

}
