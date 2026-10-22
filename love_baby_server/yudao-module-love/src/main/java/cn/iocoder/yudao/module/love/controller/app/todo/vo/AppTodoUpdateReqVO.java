package cn.iocoder.yudao.module.love.controller.app.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 更新待办 Request VO，全字段保存式（含 toggle done：端上整条提交当前状态），" +
        "id 为服务端 Long，App 端按 String 解析后原样回传（INTEGRATION-DESIGN.md §5.1）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppTodoUpdateReqVO {

    @Schema(description = "待办编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "待办编号不能为空")
    private Long id;

    @Schema(description = "待办标题（全字段保存式，必须随整条提交）", requiredMode = Schema.RequiredMode.REQUIRED, example = "交水电费")
    @NotEmpty(message = "标题不能为空")
    @Length(max = 64, message = "标题长度不能超过 64 位")
    private String title;

    @Schema(description = "是否完成（toggle done 走本字段）", example = "true")
    private Boolean done;

    @Schema(description = "标签文本，服务端只存字符串不校验取值（客户端字典）", example = "生活")
    @Length(max = 32, message = "标签长度不能超过 32 位")
    private String tag;

    @Schema(description = "标签颜色 hex 色值字符串，服务端只存字符串不校验取值（客户端字典）", example = "#FF6B81")
    @Length(max = 16, message = "颜色长度不能超过 16 位")
    private String color;

}
