package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 发送贴纸消息 Request VO（POST /love/message/send-sticker，kind=6，M23）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendStickerReqVO {

    @Schema(description = "贴纸 key（端上内置字典快照式，服务端不校验字典归属；≤64 字符）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "hug")
    @NotEmpty(message = "贴纸 key 不能为空")
    @Length(max = 64, message = "贴纸 key 长度不能超过 64 位")
    private String stickerKey;

}
