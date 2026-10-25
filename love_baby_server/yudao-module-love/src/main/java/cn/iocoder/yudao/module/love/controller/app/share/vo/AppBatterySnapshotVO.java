package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 电量快照 VO（battery/report 与 battery/get 回包的 mine 结构）；" +
        "reportTime = 行 update_time（upsert 每次刷新，表无独立上报时间列）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppBatterySnapshotVO {

    @Schema(description = "电量百分比（-1 = 未知）", requiredMode = Schema.RequiredMode.REQUIRED, example = "66")
    private Integer batteryLevel;

    @Schema(description = "充电状态（0=未充电 1=充电中 2=已充满）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer chargingStatus;

    @Schema(description = "上报时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime reportTime;

}
