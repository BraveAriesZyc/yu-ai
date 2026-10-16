package cn.iocoder.yudao.module.love.controller.admin.user.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - C 端用户分页 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoveUserPageReqVO extends PageParam {

    @Schema(description = "手机号，模糊匹配", example = "13800138000")
    private String mobile;

    @Schema(description = "用户昵称，模糊匹配", example = "小爱")
    private String nickname;

    @Schema(description = "帐号状态，参见 CommonStatusEnum 枚举类（0 启用 / 1 停用）", example = "0")
    private Integer status;

    @Schema(description = "创建时间", example = "[2026-10-01 00:00:00, 2026-10-16 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
