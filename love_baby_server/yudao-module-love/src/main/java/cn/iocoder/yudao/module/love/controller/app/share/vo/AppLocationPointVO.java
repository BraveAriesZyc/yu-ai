package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 位置点 VO（latest/trail 回包通用结构）；坐标 GCJ-02，" +
        "id 为服务端 Long 经 NumberSerializer 超长转字符串，App 端按 String 解析（INTEGRATION-DESIGN.md §5.1）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLocationPointVO {

    @Schema(description = "位置点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long id;

    @Schema(description = "纬度（GCJ-02）", requiredMode = Schema.RequiredMode.REQUIRED, example = "31.230416")
    private Double lat;

    @Schema(description = "经度（GCJ-02）", requiredMode = Schema.RequiredMode.REQUIRED, example = "121.473701")
    private Double lng;

    @Schema(description = "定位精度（米，0=未知）", example = "35")
    private Integer accuracy;

    @Schema(description = "定位时刻（端上 fix 时间，非入库时间）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime reportTime;

}
