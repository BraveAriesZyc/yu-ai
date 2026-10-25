package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 上报电量快照 Request VO；服务端值域收口：level -1~100（-1=未知），charging 0~2")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppBatteryReportReqVO {

    @Schema(description = "电量百分比（-1 = 未知）", requiredMode = Schema.RequiredMode.REQUIRED, example = "66")
    @NotNull(message = "电量不能为空")
    @Min(value = -1, message = "电量值不合法")
    @Max(value = 100, message = "电量值不合法")
    private Integer batteryLevel;

    @Schema(description = "充电状态（0=未充电 1=充电中 2=已充满）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "充电状态不能为空")
    @Min(value = 0, message = "充电状态不合法")
    @Max(value = 2, message = "充电状态不合法")
    private Integer chargingStatus;

}
