package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - TA 的位置 VO（partner 组装）；shareEnabled=false 时 point 为 null" +
        "（端上显示「TA 未开启位置共享」，不打错误码——开关关是正常态不是异常）；" +
        "开关开但 TA 从未上报时 point 同为 null（端上显示「还没有 TA 的位置」）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLocationPartnerVO {

    @Schema(description = "TA 是否开启位置共享", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean shareEnabled;

    @Schema(description = "TA 的最新位置点（开关关/从未上报时为 null）")
    private AppLocationPointVO point;

}
