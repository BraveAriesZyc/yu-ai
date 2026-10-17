package cn.iocoder.yudao.module.love.controller.admin.anniversary.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 纪念日分页 Request VO（M5；冻结：仅 title 模糊 + createTime 段，" +
        "无 coupleId/ownerId 精确项——web 搜索表单与 VO 同口径）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoveAnniversaryPageReqVO extends PageParam {

    @Schema(description = "纪念日名称，模糊匹配", example = "领证")
    private String title;

    @Schema(description = "创建时间", example = "[2026-10-01 00:00:00, 2026-10-17 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
