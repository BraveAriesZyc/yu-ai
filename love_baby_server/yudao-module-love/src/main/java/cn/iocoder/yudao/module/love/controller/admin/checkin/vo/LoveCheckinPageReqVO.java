package cn.iocoder.yudao.module.love.controller.admin.checkin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 每日打卡分页 Request VO（M8；冻结：仅 userId 精确 + checkinDate 段，" +
        "无 coupleId 精确项——web 搜索表单与 VO 同口径）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoveCheckinPageReqVO extends PageParam {

    @Schema(description = "用户编号，精确匹配", example = "1")
    private Long userId;

    @Schema(description = "打卡日期", example = "[2026-10-01, 2026-10-17]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] checkinDate;

}
