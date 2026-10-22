package cn.iocoder.yudao.module.love.controller.app.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 勾选/取消心愿 Request VO（done 端点单口双向，M14）：" +
        "done=true = 勾选完成（CAS 恰发一条庆祝消息，D-19b），done=false = 取消勾选（不发消息）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppWishDoneReqVO {

    @Schema(description = "心愿编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "心愿编号不能为空")
    private Long id;

    @Schema(description = "目标状态：true=勾选完成，false=取消勾选", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "目标状态不能为空")
    private Boolean done;

}
