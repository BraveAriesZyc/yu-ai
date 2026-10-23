package cn.iocoder.yudao.module.love.controller.admin.wishtemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 心愿墙挑选模板分页 Request VO（M25）")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoveWishTemplatePageReqVO extends PageParam {

    @Schema(description = "分类关键字（模糊匹配）", example = "想亲亲")
    private String category;

    @Schema(description = "标题关键字（模糊匹配）", example = "亲亲")
    private String title;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

    @Schema(description = "创建时间段")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
