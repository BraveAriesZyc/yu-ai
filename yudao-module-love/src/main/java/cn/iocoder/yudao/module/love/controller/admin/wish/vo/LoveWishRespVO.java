package cn.iocoder.yudao.module.love.controller.admin.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 心愿 Response VO（INTEGRATION-DESIGN.md §5.3 wish 组，M14）；" +
        "比 App 侧 AppWishRespVO 多 coupleId（管理维度按 couple 检索）")
@Data
public class LoveWishRespVO {

    @Schema(description = "心愿编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long coupleId;

    @Schema(description = "创建人用户编号（删除权限依据）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "心愿标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "一起去看海")
    private String title;

    @Schema(description = "是否完成", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean done;

    @Schema(description = "完成人用户编号（勾选者，可与创建人不同；未完成为 null）", example = "2")
    private Long doneBy;

    @Schema(description = "完成时间（未完成为 null）")
    private LocalDateTime doneTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
