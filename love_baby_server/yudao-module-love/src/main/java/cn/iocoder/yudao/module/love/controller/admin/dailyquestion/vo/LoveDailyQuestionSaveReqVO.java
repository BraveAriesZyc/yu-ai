package cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 每日一问题目新增/更新 Request VO（M32）
 *
 * create：id 为空；update：id 必填（save 复用一 VO，B 端表单弹窗统一提交）。
 * status 不传时落库默认 0 启用（MP 插入忽略 null 字段走列默认）；
 * category 可不传（落库 ''，App 端原样展示）。
 * 改题不影响历史：已出过题的天走 love_daily_question_day 快照（D-19a/D-21）。
 */
@Schema(description = "管理后台 - 每日一问题目新增/更新 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveDailyQuestionSaveReqVO {

    @Schema(description = "题目编号（update 必填，create 不传）", example = "1")
    private Long id;

    @Schema(description = "题目内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "最近一次让你觉得「有 TA 真好」的瞬间是什么？")
    @NotEmpty(message = "题目内容不能为空")
    @Length(max = 200, message = "题目内容长度不能超过 200 位")
    private String content;

    @Schema(description = "分类（自由文本：情感/日常/回忆/脑洞/未来…）", example = "回忆")
    @Length(max = 32, message = "分类长度不能超过 32 位")
    private String category;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

}
