package cn.iocoder.yudao.module.love.controller.admin.anniversary.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 纪念日 Response VO（M5）")
@Data
public class LoveAnniversaryRespVO {

    @Schema(description = "纪念日编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号（couple 共享；解绑后留历史值，D-11）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long coupleId;

    @Schema(description = "创建人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerId;

    @Schema(description = "纪念日名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "领证纪念日")
    private String title;

    @Schema(description = "纪念日日期 yyyy-MM-dd（原始日期，服务端不做下一次发生日推算，D-17）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-06-01")
    private LocalDate annivDate;

    @Schema(description = "重复类型：1=每年重复 2=一次性", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer repeatType;

    @Schema(description = "emoji 图标（客户端字典，空串 = 未选）", example = "🎂")
    private String emoji;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
