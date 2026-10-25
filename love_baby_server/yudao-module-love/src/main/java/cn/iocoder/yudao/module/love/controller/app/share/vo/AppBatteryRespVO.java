package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 电量查询/上报 Response VO（battery/report 与 battery/get 统一回包）；" +
        "对象形态契约（禁 CommonResult<Boolean> 标量，M11 定案）：report 一次带回双方状态，端上免二次拉取")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppBatteryRespVO {

    @Schema(description = "我的电量快照（从未上报时为 null）")
    private AppBatterySnapshotVO mine;

    @Schema(description = "TA 的电量（未绑定不打错、开关关只回 shareEnabled=false）", requiredMode = Schema.RequiredMode.REQUIRED)
    private AppBatteryPartnerVO partner;

}
