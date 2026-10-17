package cn.iocoder.yudao.module.love.controller.app.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 删除心愿 Request VO，" +
        "id 为服务端 Long，App 端按 String 解析后原样回传（INTEGRATION-DESIGN.md §5.1）；仅创建者可删（D-19e）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppWishDeleteReqVO {

    @Schema(description = "心愿编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "心愿编号不能为空")
    private Long id;

}
