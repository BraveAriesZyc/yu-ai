package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 共享开关 Response VO；懒创建语义：无行按默认值返回（电量开/位置关），首次 update 才落行")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppShareSettingRespVO {

    @Schema(description = "电量共享开关（默认 true，低敏数据）", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean batteryShare;

    @Schema(description = "位置共享开关（默认 false，强隐私 §12.4）", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean locationShare;

}
