package cn.iocoder.yudao.module.love.controller.admin.inspiration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 恋爱灵感 Response VO（admin 分页元素，M17）
 */
@Schema(description = "管理后台 - 恋爱灵感 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveInspirationRespVO {

    @Schema(description = "灵感编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "类型（1-情话 2-约会灵感）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer kind;

    @Schema(description = "标题（约会灵感用，情话为 null）", example = "公园野餐日")
    private String title;

    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "带上野餐垫、水果和小音箱，找一片树荫躺一下午")
    private String content;

    @Schema(description = "分类", example = "户外")
    private String category;

    @Schema(description = "状态（0启用 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
