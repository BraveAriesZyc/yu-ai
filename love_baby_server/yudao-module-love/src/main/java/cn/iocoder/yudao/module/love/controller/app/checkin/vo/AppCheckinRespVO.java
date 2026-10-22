package cn.iocoder.yudao.module.love.controller.app.checkin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 每日打卡 Response VO，checkin/check 回包与 checkin/list、月历渲染统一使用；" +
        "id/userId 为服务端 Long，经 NumberSerializer 超长转字符串，App 端 DTO 一律按 String 解析（INTEGRATION-DESIGN.md §5.1）；" +
        "checkinDate 为 LocalDate，全局 LocalDateSerializer 出 ISO 字符串 yyyy-MM-dd（AppMoodRespVO.moodDate 同机制）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppCheckinRespVO {

    @Schema(description = "打卡编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "打卡用户编号（couple 双方各自记录，端上按 userId 区分我/TA）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "打卡日期 yyyy-MM-dd（服务端当天，无补打）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-17")
    private LocalDate checkinDate;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
