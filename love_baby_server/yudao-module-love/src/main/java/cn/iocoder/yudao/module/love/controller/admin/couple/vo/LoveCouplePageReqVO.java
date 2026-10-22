package cn.iocoder.yudao.module.love.controller.admin.couple.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 情侣关系分页 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoveCouplePageReqVO extends PageParam {

    @Schema(description = "关系状态，首期恒为 0（正常）；解绑即物理删除行，无中间态", example = "0")
    private Integer status;

    @Schema(description = "发起方（user_a）昵称，模糊匹配", example = "小爱")
    private String userANickname;

    @Schema(description = "受邀方（user_b）昵称，模糊匹配", example = "小侣")
    private String userBNickname;

    @Schema(description = "创建时间", example = "[2026-10-01 00:00:00, 2026-10-16 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
