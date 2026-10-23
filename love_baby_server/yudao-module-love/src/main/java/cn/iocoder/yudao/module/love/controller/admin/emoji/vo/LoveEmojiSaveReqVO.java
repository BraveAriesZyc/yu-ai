package cn.iocoder.yudao.module.love.controller.admin.emoji.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 聊天表情包新增/更新 Request VO（M25）
 *
 * create：id 为空；update：id 必填（save 复用一 VO，B 端表单弹窗统一提交）。
 * status 不传时落库默认 0 启用（MP 插入忽略 null 字段走列默认）；
 * category 可不传（落库 ''，App 端归「常用」分组）。
 */
@Schema(description = "管理后台 - 聊天表情包新增/更新 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveEmojiSaveReqVO {

    @Schema(description = "表情编号（update 必填，create 不传）", example = "1")
    private Long id;

    @Schema(description = "分类（App 端按此聚合 tab，建议：爱意/情绪/手势/庆祝）", example = "爱意")
    @Length(max = 20, message = "分类长度不能超过 20 位")
    private String category;

    @Schema(description = "emoji 字符（App 点选直插输入框）", requiredMode = Schema.RequiredMode.REQUIRED, example = "🤗")
    @NotBlank(message = "emoji 不能为空")
    @Length(max = 16, message = "emoji 长度不能超过 16 位")
    private String emoji;

    @Schema(description = "显示顺序（同分类内越小越靠前）", example = "1")
    private Integer sort;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

}
