package cn.iocoder.yudao.module.love.controller.app.inspiration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 约会灵感 Response VO（M17，app /list 列表元素）
 *
 * 只服务 kind=2（约会灵感）；Long id 序列化为字符串（App 端统一字符串 id 口径），
 * title/category 可能为空（运营未填），端上空串兜底。
 */
@Schema(description = "用户 App - 约会灵感 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppInspirationRespVO {

    @Schema(description = "灵感编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "类型（恒 2-约会灵感）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer kind;

    @Schema(description = "标题", example = "公园野餐日")
    private String title;

    @Schema(description = "内容描述", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "带上野餐垫、水果和小音箱，找一片树荫躺一下午")
    private String content;

    @Schema(description = "分类（室内/户外/美食/观影/手作）", example = "户外")
    private String category;

}
