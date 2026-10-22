package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 标记聊天已读 Request VO（POST /love/message/read）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageReadReqVO {

    @Schema(description = "已读到的最大消息 id（服务端 GREATEST 只进不退；不校验消息归属——游标只推进自己行，谎报大 id 仅压制自己的红点）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "消息编号不能为空")
    @Positive(message = "消息编号必须为正数")
    private Long lastMessageId;

}
