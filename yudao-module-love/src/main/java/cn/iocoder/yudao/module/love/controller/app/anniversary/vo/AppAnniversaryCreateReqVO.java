package cn.iocoder.yudao.module.love.controller.app.anniversary.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 创建纪念日 Request VO（INTEGRATION-DESIGN.md §5.2 anniversary 组，M5）\n" +
        "annivDate 用 String 而非 LocalDate：@Pattern 挡格式后服务端 LocalDate.parse 二次校验日历合法性" +
        "（如 2026-02-30 打 1_003_006_002），错误语义比全局反序列化失败更准")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAnniversaryCreateReqVO {

    @Schema(description = "纪念日名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "领证纪念日")
    @NotEmpty(message = "纪念日名称不能为空")
    @Length(max = 32, message = "纪念日名称长度不能超过 32 位")
    private String title;

    @Schema(description = "纪念日日期，格式 yyyy-MM-dd", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-06-01")
    @NotEmpty(message = "日期不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式应为 yyyy-MM-dd")
    private String annivDate;

    @Schema(description = "重复类型：1=每年重复 2=一次性", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "重复类型不能为空")
    private Integer repeatType;

    @Schema(description = "emoji 图标（端上预设字典选一，客户端字典不校验取值，铁律⑥；可空）", example = "🎂")
    @Length(max = 16, message = "emoji 长度不能超过 16 位")
    private String emoji;

}
