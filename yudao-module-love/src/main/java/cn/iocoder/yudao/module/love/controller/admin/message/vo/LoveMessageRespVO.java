package cn.iocoder.yudao.module.love.controller.admin.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 悄悄话消息 Response VO")
@Data
public class LoveMessageRespVO {

    @Schema(description = "消息编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号（解绑后留历史值，D-11）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long coupleId;

    @Schema(description = "发送方用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long senderId;

    @Schema(description = "接收方用户编号（快照列，解绑后审计归对依据，D-11）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long receiverId;

    @Schema(description = "消息类型（1 文本悄悄话 / 2 心愿消息）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer kind;

    @Schema(description = "文本内容，或心愿文案快照", example = "晚安")
    private String content;

    @Schema(description = "心愿 key 快照，kind = 2 时有值", example = "birthday")
    private String wishKey;

    @Schema(description = "心愿颜色快照，kind = 2 时有值", example = "#FF6B81")
    private String color;

    @Schema(description = "心愿图标快照，kind = 2 时有值", example = "gift")
    private String icon;

    @Schema(description = "审核状态（0 待审 / 1 通过 / 2 命中；首期恒为 1，§12.3）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer auditStatus;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
