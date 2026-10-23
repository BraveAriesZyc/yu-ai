package cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 心愿墙挑选模板 Response VO（admin 分页元素，M25）
 */
@Schema(description = "管理后台 - 心愿墙挑选模板 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveWishTemplateRespVO {

    @Schema(description = "模板编号（= App 送出 wishKey）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "分类（App 端动态聚合 chips）", example = "想亲亲")
    private String category;

    @Schema(description = "标题（气泡主文案）", requiredMode = Schema.RequiredMode.REQUIRED, example = "求亲亲")
    private String title;

    @Schema(description = "副标题（卡片第二行）", example = "mua 一口就跑")
    private String subtitle;

    @Schema(description = "卡片渐变起始色 hex", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FF9AC4")
    private String colorStart;

    @Schema(description = "卡片渐变结束色 hex", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FF5A8C")
    private String colorEnd;

    @Schema(description = "图标（端上 AppIconType 枚举 name）", requiredMode = Schema.RequiredMode.REQUIRED, example = "Heart")
    private String icon;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "状态（0启用 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
