package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 发送文本悄悄话 Request VO（POST /love/message/send，kind=1）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendReqVO {

    @Schema(description = "文本内容（service 层 trim 后非空；≤500，列 varchar(500)）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "今晚一起看电影呀")
    @NotEmpty(message = "内容不能为空")
    @Length(max = 500, message = "内容长度不能超过 500 位")
    private String content;

}
