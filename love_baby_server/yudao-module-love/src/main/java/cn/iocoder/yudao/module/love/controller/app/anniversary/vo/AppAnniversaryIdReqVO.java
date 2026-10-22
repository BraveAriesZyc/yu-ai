package cn.iocoder.yudao.module.love.controller.app.anniversary.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 按 id 操作纪念日 Request VO（delete 端点用），" +
        "id 为服务端 Long，App 端按 String 解析后原样回传（INTEGRATION-DESIGN.md §5.1）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAnniversaryIdReqVO {

    @Schema(description = "纪念日编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "纪念日编号不能为空")
    private Long id;

}
