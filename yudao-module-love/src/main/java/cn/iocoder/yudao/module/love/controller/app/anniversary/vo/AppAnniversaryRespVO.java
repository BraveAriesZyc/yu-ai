package cn.iocoder.yudao.module.love.controller.app.anniversary.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 纪念日 Response VO，anniversary/list 全量列表与 anniversary/create 回包统一使用；" +
        "id/ownerId 为服务端 Long，经 NumberSerializer 超长转字符串，App 端 DTO 一律按 String 解析（INTEGRATION-DESIGN.md §5.1）；" +
        "annivDate 为 LocalDate，全局 LocalDateSerializer 出 ISO 字符串 yyyy-MM-dd（AppMoodRespVO.moodDate 同机制）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAnniversaryRespVO {

    @Schema(description = "纪念日编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "创建人用户编号（仅记录归属，双方均可见可操作）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerId;

    @Schema(description = "纪念日名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "领证纪念日")
    private String title;

    @Schema(description = "纪念日日期 yyyy-MM-dd（原始日期，每年重复的下一次发生日由端上推算，D-17）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-06-01")
    private LocalDate annivDate;

    @Schema(description = "重复类型：1=每年重复 2=一次性", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer repeatType;

    @Schema(description = "emoji 图标（客户端字典，空串端上按 \"❤\" 兜底）", example = "🎂")
    private String emoji;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
