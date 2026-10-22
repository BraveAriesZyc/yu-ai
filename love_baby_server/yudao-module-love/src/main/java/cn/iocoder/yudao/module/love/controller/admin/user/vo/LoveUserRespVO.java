package cn.iocoder.yudao.module.love.controller.admin.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - C 端用户 Response VO")
@Data
public class LoveUserRespVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String mobile;

    @Schema(description = "用户昵称", example = "小爱")
    private String nickname;

    @Schema(description = "用户头像", example = "https://www.iocoder.cn/1.png")
    private String avatar;

    @Schema(description = "性别（0 女 / 1 男，null 未设置——与系统字典语义相反）", example = "0")
    private Integer sex;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "个性签名")
    private String signature;

    @Schema(description = "帐号状态，参见 CommonStatusEnum 枚举类（0 启用 / 1 停用）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "最后登录 IP", example = "127.0.0.1")
    private String lastLoginIp;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
