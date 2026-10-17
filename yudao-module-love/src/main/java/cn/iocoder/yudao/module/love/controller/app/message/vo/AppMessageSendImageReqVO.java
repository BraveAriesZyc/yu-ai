package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 发送图片消息 Request VO（POST /love/message/send-image，kind=3，content 列存图片 URL）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendImageReqVO {

    @Schema(description = "图片完整 URL（infra 文件服务上传返回；≤512，列 varchar(512)）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "http://127.0.0.1:48080/admin-api/infra/file/4/get/smoke.jpg")
    @NotEmpty(message = "图片地址不能为空")
    @Length(max = 512, message = "图片地址长度不能超过 512 位")
    private String imageUrl;

}
