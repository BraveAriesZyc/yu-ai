package cn.iocoder.yudao.module.love.controller.app.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 心愿 Response VO，wish/list 全量列表与 wish/create、wish/done 回包统一使用（M14）；" +
        "id/userId/doneBy 为服务端 Long，经 NumberSerializer 超长转字符串，App 端 DTO 一律按 String 解析（INTEGRATION-DESIGN.md §5.1）；" +
        "⚠ 回包恒为对象形态——禁改回标量（端上 HttpClient toOk 只分派对象/数组，标量落空对象，M11 冻结口径）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppWishRespVO {

    @Schema(description = "心愿编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "创建人用户编号（删除权限依据：仅创建者可删）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
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
