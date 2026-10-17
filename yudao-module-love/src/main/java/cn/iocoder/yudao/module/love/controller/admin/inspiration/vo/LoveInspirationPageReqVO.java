package cn.iocoder.yudao.module.love.controller.admin.inspiration.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 恋爱灵感分页 Request VO（M17）")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoveInspirationPageReqVO extends PageParam {

    @Schema(description = "类型（1-情话 2-约会灵感）", example = "1")
    private Integer kind;

    @Schema(description = "内容关键字（模糊匹配标题/描述）", example = "野餐")
    private String content;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

    @Schema(description = "创建时间段")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
