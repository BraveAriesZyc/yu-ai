package cn.iocoder.yudao.module.love.controller.app.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 悄悄话/心愿消息 Response VO（message/list 数组元素与 send、send-wishes 回包统一契约，§5.2）
 *
 * id/senderId/receiverId 服务端 Long，经 NumberSerializer 序列化（超 2^53-1 转字符串，§5.1），
 * 端上一律按 String 解析；createTime 为 LocalDateTime，走仓内 TimestampLocalDateTimeSerializer
 * 序列化为 epoch 毫秒（yudao 全局惯例），端上按 Long 解析（0 = 未回传）
 */
@Schema(description = "用户 App - 悄悄话/心愿消息 Response VO，send / send-wishes 回包供端上乐观替换与 lastId 推进（§7.4）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMessageRespVO {

    @Schema(description = "消息编号（端上 lastId 游标推进依据）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long coupleId;

    @Schema(description = "发送方用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long senderId;

    @Schema(description = "接收方用户编号（对方快照，解绑后审计归对，D-11）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long receiverId;

    @Schema(description = "消息类型（1 文本悄悄话 / 2 心愿消息 / 3 图片消息）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer kind;

    @Schema(description = "文本内容，或心愿文案快照，或图片 URL（kind=3）", requiredMode = Schema.RequiredMode.REQUIRED, example = "今晚一起看电影呀")
    private String content;

    @Schema(description = "心愿 key 快照（kind=2 时有值，可空）", example = "hug")
    private String wishKey;

    @Schema(description = "心愿颜色快照（hex，kind=2 时有值，可空）", example = "#FF6B81")
    private String color;

    @Schema(description = "心愿图标快照（icon name，kind=2 时有值，可空）", example = "gift")
    private String icon;

    @Schema(description = "审核状态（0 待审 / 1 通过 / 2 命中；首期恒写 1 兜底，§12.3）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer auditStatus;

    @Schema(description = "发送时间（epoch 毫秒）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
