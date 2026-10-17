package cn.iocoder.yudao.module.love.controller.app.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 相册瞬间 - 点赞/取消点赞的 App 端请求 VO（M15）
 *
 * liked=true 点赞 / false 取消；端点幂等（D-20b/D-20e），重复操作收敛到同一真值不打错。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 点赞瞬间 Request VO")
@Data
public class AppMomentLikeReqVO {

    @Schema(description = "瞬间编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "瞬间编号不能为空")
    private Long id;

    @Schema(description = "true=点赞 false=取消点赞", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "点赞状态不能为空")
    private Boolean liked;

}
