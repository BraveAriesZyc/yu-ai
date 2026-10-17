package cn.iocoder.yudao.module.love.controller.app.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 相册瞬间 - 删除瞬间的 App 端请求 VO（M9）
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 删除瞬间 Request VO")
@Data
public class AppMomentDeleteReqVO {

    @Schema(description = "瞬间编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "瞬间编号不能为空")
    private Long id;

}
