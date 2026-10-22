package cn.iocoder.yudao.module.love.controller.admin.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 心情默认选项 Response VO（admin 分页元素，M3.5）
 *
 * 模板行仅此一页消费（app 端不感知模板表，只消费播种后的私有字典）；
 * emoji/color 原样字符串透传，B 端表格渲染色块由前端处理。
 */
@Schema(description = "管理后台 - 心情默认选项 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveMoodOptionDefaultRespVO {

    @Schema(description = "默认选项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "心情名", requiredMode = Schema.RequiredMode.REQUIRED, example = "开心")
    private String label;

    @Schema(description = "表情（utf8mb4 文本原样透传）", requiredMode = Schema.RequiredMode.REQUIRED, example = "😄")
    private String emoji;

    @Schema(description = "颜色 hex（原样透传，B 端渲染色块）", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FFB93C")
    private String color;

    @Schema(description = "显示顺序（越小越靠前，播种行继承此值）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "状态（0启用 1停用；停用行不参与绑定播种）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
