package cn.iocoder.yudao.module.love.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 登录 Response VO，登录与刷新令牌统一使用")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthLoginRespVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long userId;

    @Schema(description = "访问令牌", requiredMode = Schema.RequiredMode.REQUIRED, example = "happy")
    private String accessToken;

    @Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED, example = "nice")
    private String refreshToken;

    @Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime expiresTime;

    @Schema(description = "客户端编号，恒为 couple-app", requiredMode = Schema.RequiredMode.REQUIRED, example = "couple-app")
    private String clientId;

}
