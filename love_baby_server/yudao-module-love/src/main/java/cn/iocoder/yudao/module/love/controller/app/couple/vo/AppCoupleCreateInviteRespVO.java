package cn.iocoder.yudao.module.love.controller.app.couple.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 生成/刷新我的邀请码 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppCoupleCreateInviteRespVO {

    @Schema(description = "我的邀请码，8 位（大写字母 + 数字，排除易混淆的 0/O/1/I）", requiredMode = Schema.RequiredMode.REQUIRED, example = "AB12CD34")
    private String inviteCode;

    @Schema(description = "邀请码过期时间（生成时间 + 7 天，惰性校验）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime expireTime;

}
