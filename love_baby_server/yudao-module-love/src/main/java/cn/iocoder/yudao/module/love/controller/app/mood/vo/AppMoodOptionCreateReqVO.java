package cn.iocoder.yudao.module.love.controller.app.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 新增心情选项 Request VO（POST /love/mood/option/create，M3.5）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMoodOptionCreateReqVO {

    @Schema(description = "心情名", requiredMode = Schema.RequiredMode.REQUIRED, example = "元气满满")
    @NotEmpty(message = "心情名不能为空")
    @Length(max = 16, message = "心情名长度不能超过 16 位")
    private String label;

    @Schema(description = "表情（utf8mb4 文本，端上预设集选定后原样上送）", example = "😄")
    @NotEmpty(message = "表情不能为空")
    @Length(max = 16, message = "表情长度不能超过 16 位")
    private String emoji;

    @Schema(description = "颜色 hex 色值字符串（端上色板选定后原样上送）", example = "#FFB93C")
    @NotEmpty(message = "颜色不能为空")
    @Length(max = 16, message = "颜色长度不能超过 16 位")
    private String color;

}
