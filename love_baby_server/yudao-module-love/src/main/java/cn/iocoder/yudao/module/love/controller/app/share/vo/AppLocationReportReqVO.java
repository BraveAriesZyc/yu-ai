package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 上报位置点 Request VO；坐标为 GCJ-02（端上 WGS-84→GCJ-02 纠偏后上传）；" +
        "(0,0) 空坐标由 service 拒（1_003_015_001）；30s 内重复上报服务端静默吞点（限频，回操作后最新点）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLocationReportReqVO {

    @Schema(description = "纬度（GCJ-02）", requiredMode = Schema.RequiredMode.REQUIRED, example = "31.230416")
    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度不合法")
    @DecimalMax(value = "90", message = "纬度不合法")
    private Double lat;

    @Schema(description = "经度（GCJ-02）", requiredMode = Schema.RequiredMode.REQUIRED, example = "121.473701")
    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180", message = "经度不合法")
    @DecimalMax(value = "180", message = "经度不合法")
    private Double lng;

    @Schema(description = "定位精度（米，0=未知）", example = "35")
    @Min(value = 0, message = "精度不合法")
    @Max(value = 100000, message = "精度不合法")
    private Integer accuracy;

    @Schema(description = "定位时刻（端上 fix 时间的 epoch 毫秒；null = 服务端 now() 兜底；" +
            "未来时间截断为 now，防端上时钟漂移污染轨迹排序）", example = "1761355200000")
    private Long reportTime;

    // ========== 地址五字段（M28 行动轨迹地点记录，全部可空，仅落 love_location_place） ==========
    // 端上 regeo(extensions=all) 解析随点上报；服务端无高德 key 不做逆地理，全空也落地点行
    // （端上降级显坐标）；长度由 service 落库前按列宽截断（不设 @Size 校验——校验失败会连
    // 坐标上报一起拒收，违背「地址失败不影响上报」的边界）

    @Schema(description = "完整地址（regeo formatted_address，可空）", example = "上海市黄浦区南京东路XX号")
    private String addrText;

    @Schema(description = "省（可空，直辖市可能空）", example = "上海市")
    private String addrProv;

    @Schema(description = "市（可空，直辖市可能空）", example = "上海市")
    private String addrCity;

    @Schema(description = "区/县（可空）", example = "黄浦区")
    private String addrDist;

    @Schema(description = "地名/POI（regeo pois[0].name，可空）", example = "人民广场")
    private String addrPoi;

}
