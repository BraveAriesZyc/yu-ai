package cn.iocoder.yudao.module.love.controller.admin.user.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "管理后台 - C 端用户更新 Request VO，昵称/头像/状态/个性签名均为可选，仅更新提交的非空字段")
@Data
public class LoveUserUpdateReqVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "用户编号不能为空")
    private Long id;

    @Schema(description = "用户昵称", example = "小爱")
    @Length(max = 32, message = "昵称长度不能超过 32 位")
    private String nickname;

    @Schema(description = "用户头像", example = "https://www.iocoder.cn/1.png")
    @Length(max = 256, message = "头像长度不能超过 256 位")
    private String avatar;

    @Schema(description = "帐号状态，参见 CommonStatusEnum 枚举类（0 启用 / 1 停用）", example = "0")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

    @Schema(description = "个性签名", example = "喜欢看电影")
    @Length(max = 100, message = "个性签名长度不能超过 100 位")
    private String signature;

}
