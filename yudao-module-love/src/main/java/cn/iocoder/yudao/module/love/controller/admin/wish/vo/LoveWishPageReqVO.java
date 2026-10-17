package cn.iocoder.yudao.module.love.controller.admin.wish.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 心愿分页 Request VO（INTEGRATION-DESIGN.md §5.3 wish 组，M14）")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoveWishPageReqVO extends PageParam {

    @Schema(description = "情侣关系编号", example = "1")
    private Long coupleId;

    @Schema(description = "创建人用户编号（删除权限依据）", example = "1")
    private Long userId;

    @Schema(description = "创建时间段")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
