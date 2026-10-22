package cn.iocoder.yudao.module.love.controller.admin.config.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 业务配置分页 Request VO（M23）")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoveConfigPageReqVO extends PageParam {

    @Schema(description = "配置名称（模糊匹配）", example = "客服微信号")
    private String name;

    @Schema(description = "配置键（模糊匹配）", example = "customer_service")
    private String configKey;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

    @Schema(description = "创建时间段")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
