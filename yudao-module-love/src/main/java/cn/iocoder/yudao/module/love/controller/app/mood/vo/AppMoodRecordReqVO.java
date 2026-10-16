package cn.iocoder.yudao.module.love.controller.app.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 记当日心情 Request VO（POST /love/mood/record）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMoodRecordReqVO {

    @Schema(description = "心情选项编号（couple 私有字典 love_mood_option.id，M3.5；跨 couple 选项按无权拒绝），" +
            "App 端按 String 解析后原样回传（§5.1）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "心情选项不能为空")
    private Long optionId;

}
