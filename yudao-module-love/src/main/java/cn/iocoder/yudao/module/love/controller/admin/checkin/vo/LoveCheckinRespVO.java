package cn.iocoder.yudao.module.love.controller.admin.checkin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 每日打卡 Response VO（M8）")
@Data
public class LoveCheckinRespVO {

    @Schema(description = "打卡编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号（couple 共享；解绑后留历史值，D-11）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long coupleId;

    @Schema(description = "打卡用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "打卡日期 yyyy-MM-dd（服务端当天，无补打）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-17")
    private LocalDate checkinDate;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
