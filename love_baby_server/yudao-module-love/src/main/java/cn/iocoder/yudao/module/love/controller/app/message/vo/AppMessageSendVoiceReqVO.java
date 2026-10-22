package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 发送语音消息 Request VO（POST /love/message/send-voice，kind=4，M23）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendVoiceReqVO {

    @Schema(description = "语音文件完整 URL（infra 文件服务上传返回；≤512，列 varchar(512)）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "http://127.0.0.1:48080/app-api/infra/file/4/get/20261022/chat/voice_xxx.m4a")
    @NotEmpty(message = "语音地址不能为空")
    @Length(max = 512, message = "语音地址长度不能超过 512 位")
    private String voiceUrl;

    @Schema(description = "语音时长（秒，端上录音 60s 上限，服务端校验放宽至 600）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    @NotNull(message = "语音时长不能为空")
    @Min(value = 1, message = "语音时长必须大于 0")
    @Max(value = 600, message = "语音时长不能超过 600 秒")
    private Integer duration;

}
