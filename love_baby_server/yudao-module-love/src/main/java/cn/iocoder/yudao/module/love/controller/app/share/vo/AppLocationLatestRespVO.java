package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 最新位置查询 Response VO（location/latest 回包）；" +
        "对象形态契约（禁 CommonResult<Boolean> 标量，M11 定案）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLocationLatestRespVO {

    @Schema(description = "我的最新位置点（从未上报时为 null）")
    private AppLocationPointVO mine;

    @Schema(description = "TA 的位置（未绑定不打错、开关关只回 shareEnabled=false）", requiredMode = Schema.RequiredMode.REQUIRED)
    private AppLocationPartnerVO partner;

}
