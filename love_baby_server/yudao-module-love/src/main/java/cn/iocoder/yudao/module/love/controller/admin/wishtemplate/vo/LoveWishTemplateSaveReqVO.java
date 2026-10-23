package cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 心愿墙挑选模板新增/更新 Request VO（M25）
 *
 * create：id 为空；update：id 必填（save 复用一 VO，B 端表单弹窗统一提交）。
 * status 不传时落库默认 0 启用；subtitle/icon/color 未传走列默认
 * （icon 默认 Heart、color 默认主题粉系，App 端未知名/非法 hex 另有兜底）。
 */
@Schema(description = "管理后台 - 心愿墙挑选模板新增/更新 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveWishTemplateSaveReqVO {

    @Schema(description = "模板编号（update 必填，create 不传）", example = "1")
    private Long id;

    @Schema(description = "分类（App 端按此聚合 chips，建议：想亲亲/想抱抱/想吃点/想念/撒个娇/和好）", example = "想亲亲")
    @Length(max = 20, message = "分类长度不能超过 20 位")
    private String category;

    @Schema(description = "标题（气泡主文案 + 卡片首行）", requiredMode = Schema.RequiredMode.REQUIRED, example = "求亲亲")
    @NotBlank(message = "标题不能为空")
    @Length(max = 32, message = "标题长度不能超过 32 位")
    private String title;

    @Schema(description = "副标题（卡片第二行，可不传）", example = "mua 一口就跑")
    @Length(max = 64, message = "副标题长度不能超过 64 位")
    private String subtitle;

    @Schema(description = "卡片渐变起始色 hex #RRGGBB", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FF9AC4")
    @NotBlank(message = "渐变起始色不能为空")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "渐变起始色格式须为 #RRGGBB")
    private String colorStart;

    @Schema(description = "卡片渐变结束色 hex #RRGGBB", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FF5A8C")
    @NotBlank(message = "渐变结束色不能为空")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "渐变结束色格式须为 #RRGGBB")
    private String colorEnd;

    @Schema(description = "图标（端上 AppIconType 枚举 name，未知名端上兜底 Heart）", requiredMode = Schema.RequiredMode.REQUIRED, example = "Heart")
    @NotBlank(message = "图标不能为空")
    @Length(max = 32, message = "图标长度不能超过 32 位")
    private String icon;

    @Schema(description = "显示顺序（越小越靠前）", example = "1")
    private Integer sort;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

}
