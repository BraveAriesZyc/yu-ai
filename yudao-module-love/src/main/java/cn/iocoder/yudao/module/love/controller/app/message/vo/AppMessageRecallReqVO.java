package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 撤回聊天消息 Request VO（POST /love/message/recall，M12）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageRecallReqVO {

    @Schema(description = "待撤回消息编号（必须为当前用户在当前 couple 内发送的消息；"
            + "存在/跨 couple/非本人统一按 1_003_003_000 拒绝，防探测）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "消息编号不能为空")
    @Positive(message = "消息编号必须为正数")
    private Long messageId;

}
