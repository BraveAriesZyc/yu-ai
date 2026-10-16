package cn.iocoder.yudao.module.love.controller.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 恋爱统计 Response VO（GET /love/user/stats 消费方 = 首页「在一起(天)」与我的页统计卡）
 *
 * 全部真数据：天数按 love_couple.bind_time 自然日含当天；三项计数按当前 couple 归属
 * （love_message / love_todo / love_mood_record，逻辑删不计）。未绑定四项全 0。
 */
@Schema(description = "用户 App - 恋爱统计 Response VO")
@Data
public class AppUserStatsRespVO {

    @Schema(description = "在一起天数（含绑定当天，未绑定为 0）", example = "520")
    private Integer bindDays;

    @Schema(description = "悄悄话消息总数（当前 couple，含文本与心愿）", example = "128")
    private Long messageCount;

    @Schema(description = "待办总数（当前 couple）", example = "36")
    private Long todoCount;

    @Schema(description = "心情记录总数（当前 couple）", example = "20")
    private Long moodCount;

}
