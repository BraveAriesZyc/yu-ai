package cn.iocoder.yudao.module.love.controller.app.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 待办 Response VO，todo/list 全量列表与 todo/create 回包统一使用；" +
        "id/ownerId 为服务端 Long，经 NumberSerializer 超长转字符串，App 端 DTO 一律按 String 解析（INTEGRATION-DESIGN.md §5.1）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppTodoRespVO {

    @Schema(description = "待办编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "创建人用户编号（仅记录归属，双方均可见可操作）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerId;

    @Schema(description = "待办标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "交水电费")
    private String title;

    @Schema(description = "是否完成", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean done;

    @Schema(description = "标签文本（家庭/恋爱/健康/生活/随手记，客户端按文本渲染）", example = "生活")
    private String tag;

    @Schema(description = "标签颜色 hex 色值字符串（客户端 Repository 层 ↔ Color，铁律⑥）", example = "#FF6B81")
    private String color;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
