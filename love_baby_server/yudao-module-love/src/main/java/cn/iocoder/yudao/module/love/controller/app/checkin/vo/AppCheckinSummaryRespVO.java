package cn.iocoder.yudao.module.love.controller.app.checkin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 打卡汇总 Response VO（GET /love/checkin/summary，首页「今日打卡」条与打卡页页头共用）；" +
        "共同连续口径：同一天双方都打卡才算当天点亮，连续天数 = 从今天往前逐日回溯，" +
        "今天任一方未打则从昨天起算（今天还没过完，不打断既有连续）；服务端聚合，与月历单月视图解耦")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppCheckinSummaryRespVO {

    @Schema(description = "共同连续打卡天数（双方同日都打才累计；0 = 还没有共同连续）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    private Integer commonStreak;

    @Schema(description = "我的累计打卡天数（全量，跨月）", requiredMode = Schema.RequiredMode.REQUIRED, example = "23")
    private Integer myTotal;

    @Schema(description = "TA 的累计打卡天数（全量，跨月）", requiredMode = Schema.RequiredMode.REQUIRED, example = "19")
    private Integer partnerTotal;

}
