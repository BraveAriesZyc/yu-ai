package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 更新共享开关 Request VO；两开关均可空，null = 不改动该开关（端上单开关独立切换）；" +
        "回包统一走 setting/get 的 AppShareSettingRespVO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppShareSettingUpdateReqVO {

    @Schema(description = "电量共享开关，null = 不改动", example = "true")
    private Boolean batteryShare;

    @Schema(description = "位置共享开关，null = 不改动", example = "false")
    private Boolean locationShare;

}
