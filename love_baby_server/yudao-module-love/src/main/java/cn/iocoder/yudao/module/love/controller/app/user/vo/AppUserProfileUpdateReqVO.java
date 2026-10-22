package cn.iocoder.yudao.module.love.controller.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 更新个人信息 Request VO，各字段均为可选，仅更新提交的非 null 字段（签名空串=清空）")
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

    @Schema(description = "性别：0 女 / 1 男，null=不更新（love 自建语义，与系统用户字典相反）", example = "0")
    private Integer sex;

    @Schema(description = "生日 yyyy-MM-dd，null=不更新；格式由 @Pattern 挡，日历合法性由 service LocalDate.parse 二次校验（anniversary 同款双层）", example = "1999-08-23")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式应为 yyyy-MM-dd")
    private String birthday;

    @Schema(description = "个性签名，null=不更新；空串=清空（端上清空语义）", example = "把想要的，说给TA听")
    @Length(max = 100, message = "签名长度不能超过 100 位")
    private String signature;

}
