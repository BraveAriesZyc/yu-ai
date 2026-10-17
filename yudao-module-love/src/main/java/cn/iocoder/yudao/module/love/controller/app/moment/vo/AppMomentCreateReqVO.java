package cn.iocoder.yudao.module.love.controller.app.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 相册瞬间 - 发布瞬间的 App 端请求 VO（M9）
 *
 * imageUrl = infra 文件服务上传返回的完整 URL（端上先 pickAndUpload 拿到 url 再发本端点）；
 * text 配文可空（空串按无配文处理）。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 发布瞬间 Request VO")
@Data
public class AppMomentCreateReqVO {

    @Schema(description = "图片完整 URL（infra 文件服务上传返回）", requiredMode = Schema.RequiredMode.REQUIRED, example = "http://192.168.1.4:48080/admin-api/infra/file/4/get/xxx.jpg")
    @NotBlank(message = "图片不能为空")
    @Length(max = 512, message = "图片地址过长")
    private String imageUrl;

    @Schema(description = "配文（可选，≤64 字）", example = "今天的晚霞")
    @Length(max = 64, message = "配文不能超过 64 字")
    private String text;

}
