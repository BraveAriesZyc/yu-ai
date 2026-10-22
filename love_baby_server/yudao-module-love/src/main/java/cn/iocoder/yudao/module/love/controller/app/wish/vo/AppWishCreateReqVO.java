package cn.iocoder.yudao.module.love.controller.app.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 App - 创建心愿 Request VO，done/doneBy/doneTime 由服务端固定初始未完成态，不接受客户端传入（INTEGRATION-DESIGN.md §5.2 wish 组，M14）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppWishCreateReqVO {

    @Schema(description = "心愿标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "一起去看海")
    // 刻意比 todo 先例（@NotEmpty）严一档：@NotBlank 连纯空白标题一并拒绝（清单条目无标签兜底，
    // 空白标题 = 纯噪音）；service 层仍 trim 后落库，双保险
    @NotBlank(message = "标题不能为空")
    @Length(max = 64, message = "标题长度不能超过 64 位")
    private String title;

}
