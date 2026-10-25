package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - TA 的电量 VO（partner 组装）；shareEnabled=false 时电量字段为 null" +
        "（端上显示「TA 未开启电量共享」，不打错误码——开关关是正常态不是异常）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppBatteryPartnerVO {

    @Schema(description = "TA 是否开启电量共享", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean shareEnabled;

    @Schema(description = "电量百分比（shareEnabled=false 时为 null）", example = "88")
    private Integer batteryLevel;

    @Schema(description = "充电状态（0=未充电 1=充电中 2=已充满；shareEnabled=false 时为 null）", example = "0")
    private Integer chargingStatus;

    @Schema(description = "上报时间（shareEnabled=false 时为 null；TA 从未上报时整个 partner 电量字段为 null）")
    private LocalDateTime reportTime;

}
