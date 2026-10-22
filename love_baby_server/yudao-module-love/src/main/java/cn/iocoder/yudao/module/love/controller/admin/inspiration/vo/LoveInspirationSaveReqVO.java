package cn.iocoder.yudao.module.love.controller.admin.inspiration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 恋爱灵感新增/更新 Request VO（M17）
 *
 * create：id 为空；update：id 必填（save 复用一 VO，B 端表单弹窗统一提交）。
 * status 不传时落库默认 0 启用（MP 插入忽略 null 字段走列默认）；
 * category 可不传（落库 ''，App 端归「其他」分组）。
 */
@Schema(description = "管理后台 - 恋爱灵感新增/更新 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveInspirationSaveReqVO {

    @Schema(description = "灵感编号（update 必填，create 不传）", example = "1")
    private Long id;

    @Schema(description = "类型（1-情话 2-约会灵感）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "类型不能为空")
    private Integer kind;

    @Schema(description = "标题（约会灵感建议填写，情话可不传）", example = "公园野餐日")
    @Length(max = 60, message = "标题长度不能超过 60 位")
    private String title;

    @Schema(description = "内容（情话正文 / 约会灵感描述）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "带上野餐垫、水果和小音箱，找一片树荫躺一下午")
    @NotEmpty(message = "内容不能为空")
    @Length(max = 200, message = "内容长度不能超过 200 位")
    private String content;

    @Schema(description = "分类（情话: 甜宠/日常/表白；约会灵感: 室内/户外/美食/观影/手作）", example = "户外")
    @Length(max = 20, message = "分类长度不能超过 20 位")
    private String category;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

}
