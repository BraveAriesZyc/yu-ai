package cn.iocoder.yudao.module.love.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 刷新令牌 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthRefreshTokenReqVO {

    @Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED, example = "nice")
    @NotEmpty(message = "刷新令牌不能为空")
    private String refreshToken;

    @Schema(description = "客户端编号，App 端固定传 couple-app", requiredMode = Schema.RequiredMode.REQUIRED, example = "couple-app")
    @NotEmpty(message = "客户端编号不能为空")
    private String clientId;

}
