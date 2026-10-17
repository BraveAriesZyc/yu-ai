package cn.iocoder.yudao.module.love.controller.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Schema(description = "用户 App - 当前登录用户个人信息 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUserProfileRespVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String mobile;

    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户5678")
    private String nickname;

    @Schema(description = "用户头像", example = "https://www.iocoder.cn/1.png")
    private String avatar;

    @Schema(description = "性别：0 女 / 1 男 / null 未设置（love 自建语义，与系统用户字典相反）", example = "0")
    private Integer sex;

    @Schema(description = "生日（全局 LocalDateSerializer 出 ISO 字符串 yyyy-MM-dd）", example = "1999-08-23")
    private LocalDate birthday;

    @Schema(description = "个性签名（空串即无签名）", example = "把想要的，说给TA听")
    private String signature;

}
