package cn.iocoder.yudao.module.love.controller.app.couple.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 凭邀请码绑定情侣关系 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppCoupleBindReqVO {

    @Schema(description = "对方邀请码", requiredMode = Schema.RequiredMode.REQUIRED, example = "AB12CD34")
    @NotEmpty(message = "邀请码不能为空")
    @Length(max = 8, message = "邀请码长度不能超过 8 位")
    private String inviteCode;

}
