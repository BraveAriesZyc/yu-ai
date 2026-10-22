package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 发送自定义卡片消息 Request VO（POST /love/message/send-card，kind=7，M23）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendCardReqVO {

    @Schema(description = "卡片类型（快照式，服务端不做白名单校验，端上按 type 分派渲染；首期仅 wishWall，后续自定义类型加 type 值不加 kind）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "wishWall")
    @NotEmpty(message = "卡片类型不能为空")
    @Length(max = 32, message = "卡片类型长度不能超过 32 位")
    private String cardType;

    @Schema(description = "卡片标题快照", requiredMode = Schema.RequiredMode.REQUIRED, example = "我们的心愿墙")
    @NotEmpty(message = "卡片标题不能为空")
    @Length(max = 64, message = "卡片标题长度不能超过 64 位")
    private String title;

    @Schema(description = "卡片副文案快照（可空 = 缺省）", example = "一起去实现愿望吧")
    @Length(max = 128, message = "卡片副文案长度不能超过 128 位")
    private String sub;

    @Schema(description = "卡片图标快照（icon name 字符串，心愿消息 icon 列同款惯例；可空 = 缺省）",
            example = "Star")
    @Length(max = 32, message = "卡片图标长度不能超过 32 位")
    private String icon;

}
