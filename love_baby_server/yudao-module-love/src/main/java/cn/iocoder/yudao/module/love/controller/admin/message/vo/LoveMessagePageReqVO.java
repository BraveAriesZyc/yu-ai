package cn.iocoder.yudao.module.love.controller.admin.message.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 悄悄话消息分页 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoveMessagePageReqVO extends PageParam {

    @Schema(description = "情侣关系编号", example = "1024")
    private Long coupleId;

    @Schema(description = "消息类型（1 文本悄悄话 / 2 心愿消息）", example = "1")
    private Integer kind;

    @Schema(description = "内容关键字，模糊匹配（审计四件套之一，§5.3）", example = "生日")
    private String content;

    @Schema(description = "创建时间", example = "[2026-10-01 00:00:00, 2026-10-16 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
