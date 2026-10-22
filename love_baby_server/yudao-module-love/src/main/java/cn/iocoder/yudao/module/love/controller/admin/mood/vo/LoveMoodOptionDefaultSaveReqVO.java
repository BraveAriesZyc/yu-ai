package cn.iocoder.yudao.module.love.controller.admin.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 心情默认选项新增/更新 Request VO（M3.5）
 *
 * create：id 为空；update：id 必填（save 复用一 VO，B 端表单弹窗统一提交）。
 * 排序不校验取值范围（整数即可，越小越靠前）；status 不传时落库默认 0 启用（MP 插入忽略 null 字段走列默认）。
 */
@Schema(description = "管理后台 - 心情默认选项新增/更新 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveMoodOptionDefaultSaveReqVO {

    @Schema(description = "默认选项编号（update 必填，create 不传）", example = "1")
    private Long id;

    @Schema(description = "心情名", requiredMode = Schema.RequiredMode.REQUIRED, example = "元气满满")
    @NotEmpty(message = "心情名不能为空")
    @Length(max = 16, message = "心情名长度不能超过 16 位")
    private String label;

    @Schema(description = "表情（utf8mb4 文本）", requiredMode = Schema.RequiredMode.REQUIRED, example = "😄")
    @NotEmpty(message = "表情不能为空")
    @Length(max = 16, message = "表情长度不能超过 16 位")
    private String emoji;

    @Schema(description = "颜色 hex 色值字符串", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FFB93C")
    @NotEmpty(message = "颜色不能为空")
    @Length(max = 16, message = "颜色长度不能超过 16 位")
    private String color;

    @Schema(description = "显示顺序（越小越靠前，播种行继承此值）", example = "1")
    @NotNull(message = "显示顺序不能为空")
    private Integer sort;

    @Schema(description = "状态（0启用 1停用；停用行不参与绑定播种）", example = "0")
    private Integer status;

}
