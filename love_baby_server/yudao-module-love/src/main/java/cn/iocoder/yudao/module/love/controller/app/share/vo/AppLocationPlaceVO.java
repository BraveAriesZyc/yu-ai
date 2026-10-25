package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 地点记录 VO（place/list 回包通用结构）；坐标 GCJ-02，" +
        "id 为服务端 Long 经 NumberSerializer 超长转字符串，App 端按 String 解析（INTEGRATION-DESIGN.md §5.1）；" +
        "省/市/区/地址/POI 由端上 regeo 解析随点上报，解析失败为 null（端上降级显坐标）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLocationPlaceVO {

    @Schema(description = "地点记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long id;

    @Schema(description = "纬度（GCJ-02）", requiredMode = Schema.RequiredMode.REQUIRED, example = "31.230416")
    private Double lat;

    @Schema(description = "经度（GCJ-02）", requiredMode = Schema.RequiredMode.REQUIRED, example = "121.473701")
    private Double lng;

    @Schema(description = "省（直辖市可能为 null）", example = "上海市")
    private String province;

    @Schema(description = "市（直辖市可能为 null）", example = "上海市")
    private String city;

    @Schema(description = "区/县", example = "黄浦区")
    private String district;

    @Schema(description = "完整地址（regeo formatted_address）", example = "上海市黄浦区南京东路XX号")
    private String address;

    @Schema(description = "地名/POI（regeo pois[0].name）", example = "人民广场")
    private String poi;

    @Schema(description = "到达时刻（端上 fix 时间，非入库时间）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime reportTime;

}
