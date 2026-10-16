package cn.iocoder.yudao.module.love.controller.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 更新个人信息 Request VO，昵称/头像均为可选，仅更新提交的非空字段")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUserProfileUpdateReqVO {

    @Schema(description = "用户昵称", example = "小红")
    @Length(max = 32, message = "昵称长度不能超过 32 位")
    private String nickname;

    @Schema(description = "用户头像", example = "https://www.iocoder.cn/1.png")
    @Length(max = 256, message = "头像长度不能超过 256 位")
    private String avatar;

}
