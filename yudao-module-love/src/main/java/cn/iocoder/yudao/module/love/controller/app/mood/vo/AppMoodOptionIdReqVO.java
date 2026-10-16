package cn.iocoder.yudao.module.love.controller.app.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 按 id 操作心情选项 Request VO（delete 端点用，M3.5），" +
        "id 为服务端 Long，App 端按 String 解析后原样回传（INTEGRATION-DESIGN.md §5.1）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMoodOptionIdReqVO {

    @Schema(description = "心情选项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "心情选项编号不能为空")
    private Long id;

}
