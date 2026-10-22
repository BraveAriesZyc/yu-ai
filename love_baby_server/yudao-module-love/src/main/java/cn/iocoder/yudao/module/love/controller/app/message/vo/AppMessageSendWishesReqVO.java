package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 发送心愿消息 Request VO（POST /love/message/send-wishes，kind=2，快照式 §5.2）
 *
 * 四字段整体快照入 love_message，服务端不校验 wish 归属（无 wish 实体，文案字典留端上）；
 * 均为可选快照字段：端上惯例「空串 = 缺省」（MessageModels 同款约定），仅按列宽做长度早拦防 DB 500
 */
@Schema(description = "用户 App - 发送心愿消息 Request VO（快照式）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageSendWishesReqVO {

    @Schema(description = "心愿 key 快照（端上文案字典的 key）", example = "hug")
    @Length(max = 32, message = "心愿 key 长度不能超过 32 位")
    private String wishKey;

    @Schema(description = "心愿文案快照（落 love_message.content）", example = "要一个大大的拥抱")
    @Length(max = 500, message = "心愿文案长度不能超过 500 位")
    private String text;

    @Schema(description = "心愿颜色快照（hex 色值字符串）", example = "#FF6B81")
    @Length(max = 16, message = "心愿颜色长度不能超过 16 位")
    private String color;

    @Schema(description = "心愿图标快照（icon name）", example = "gift")
    @Length(max = 32, message = "心愿图标长度不能超过 32 位")
    private String icon;

}
