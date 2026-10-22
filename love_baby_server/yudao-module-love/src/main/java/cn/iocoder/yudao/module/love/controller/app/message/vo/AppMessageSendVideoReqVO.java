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

@Schema(description = "用户 App - 发送视频消息 Request VO（POST /love/message/send-video，kind=5，M23）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendVideoReqVO {

    @Schema(description = "视频文件完整 URL（infra 文件服务上传返回；≤512）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "http://127.0.0.1:48080/app-api/infra/file/5/get/20261022/chat/video_xxx.mp4")
    @NotEmpty(message = "视频地址不能为空")
    @Length(max = 512, message = "视频地址长度不能超过 512 位")
    private String videoUrl;

    @Schema(description = "视频封面图完整 URL（端上 MediaMetadataRetriever 抽帧上传；可空 = 缺省，端上灰底兜底）",
            example = "http://127.0.0.1:48080/app-api/infra/file/6/get/20261022/chat/cover_xxx.jpg")
    @Length(max = 512, message = "封面地址长度不能超过 512 位")
    private String coverUrl;

    @Schema(description = "视频时长（秒）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "45")
    @NotNull(message = "视频时长不能为空")
    @Min(value = 1, message = "视频时长必须大于 0")
    @Max(value = 600, message = "视频时长不能超过 600 秒")
    private Integer duration;

}
