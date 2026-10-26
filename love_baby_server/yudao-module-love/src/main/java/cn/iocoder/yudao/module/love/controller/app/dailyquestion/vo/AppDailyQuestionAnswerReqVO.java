package cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 每日一问提交答案 Request VO（POST /love/daily-question/answer，M32）
 *
 * 不收 questionDate / questionId：只能答当天（无补答，D-21），服务端以服务端当天为准，
 * 防端上时钟作弊（AppCheckinController 口径）；答案内容 ≤500 字。
 */
@Schema(description = "用户 App - 每日一问提交答案 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppDailyQuestionAnswerReqVO {

    @Schema(description = "答案内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "上周发烧到 39 度，TA 半夜爬起来给我物理降温")
    @NotEmpty(message = "答案内容不能为空")
    @Length(max = 500, message = "答案内容长度不能超过 500 位")
    private String content;

}
