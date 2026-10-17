package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 聊天未读数 Response VO（GET /love/message/unread）")
@Data
public class AppMessageUnreadRespVO {

    @Schema(description = "未读消息数（couple 内 id 大于我方 chat_read_cursor 且非本人发送；未绑定恒 0）", example = "3")
    private Long unread;

}
