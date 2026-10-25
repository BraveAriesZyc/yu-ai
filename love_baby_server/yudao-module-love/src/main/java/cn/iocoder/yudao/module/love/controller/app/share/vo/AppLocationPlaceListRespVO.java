package cn.iocoder.yudao.module.love.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "用户 App - 地点记录查询 Response VO（location/place/list 回包）；places 按 report_time 升序" +
        "（mapper DESC LIMIT 100 截最新后 service 翻转，保证是「最近的 100 条」）；" +
        "truncated=true 时端上注记「仅展示最近 100 条地点」")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppLocationPlaceListRespVO {

    @Schema(description = "目标是否开启了位置共享（target=self 恒 true；target=partner 开关关时 places 为空列表" +
            "且本字段 false——端上区分「没共享」与「没数据」，不靠错误码）", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean shareEnabled;

    @Schema(description = "地点记录列表（report_time 升序，最多 100 条）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AppLocationPlaceVO> places;

    @Schema(description = "是否被 100 条上限截断（端上据此展示注记）", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean truncated;

}
