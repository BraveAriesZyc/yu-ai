package cn.iocoder.yudao.module.love.controller.app.inspiration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 今日情话 Response VO（M17）
 *
 * today / quote-random 两端点共用；恒对象形态（端上 HttpClient toOk 只分派对象/数组，
 * 标量落空对象——M11 冻结口径，禁直接回 String）。
 */
@Schema(description = "用户 App - 今日情话 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppInspirationQuoteRespVO {

    @Schema(description = "情话正文", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "我今天真的很想你，比昨天多一点，比明天少一点。")
    private String content;

}
