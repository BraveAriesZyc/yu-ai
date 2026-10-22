package cn.iocoder.yudao.module.love.controller.app.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 创建待办 Request VO，done 由服务端固定初始 false，不接受客户端传入（INTEGRATION-DESIGN.md §5.2 todo 组）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppTodoCreateReqVO {

    @Schema(description = "待办标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "交水电费")
    @NotEmpty(message = "标题不能为空")
    @Length(max = 64, message = "标题长度不能超过 64 位")
    private String title;

    @Schema(description = "标签文本，服务端只存字符串不校验取值（客户端字典）", example = "生活")
    @Length(max = 32, message = "标签长度不能超过 32 位")
    private String tag;

    @Schema(description = "标签颜色 hex 色值字符串，服务端只存字符串不校验取值（客户端字典）", example = "#FF6B81")
    @Length(max = 16, message = "颜色长度不能超过 16 位")
    private String color;

}
