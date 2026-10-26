package cn.iocoder.yudao.module.love.controller.admin.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 心情记录 - admin 响应 VO（M31）
 *
 * 只透出快照三列（moodLabel/emoji/color）与 moodDate——moodType 废弃列（M3.5 起新记录恒 NULL）
 * 与 optionId（渲染不回查字典的尽力而为审计链）不下发。
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 心情记录 Response VO")
@Data
public class LoveMoodRecordRespVO {

    @Schema(description = "心情记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long coupleId;

    @Schema(description = "记录用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long userId;

    @Schema(description = "心情日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate moodDate;

    @Schema(description = "心情名快照（记录时点）", example = "开心")
    private String moodLabel;

    @Schema(description = "表情快照（记录时点）")
    private String emoji;

    @Schema(description = "颜色 hex 快照（记录时点）", example = "#FFB27A")
    private String color;

    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;

}
