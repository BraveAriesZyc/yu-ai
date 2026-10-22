package cn.iocoder.yudao.module.love.controller.app.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 相册瞬间 - 点赞操作回包 VO（M15）
 *
 * 恒回操作后服务端真值（D-20e）：端上按 id 原位替换，不做端上乐观推导。
 * likeCount ∈ {0,1,2}（couple 恒 2 人）。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 点赞瞬间 Response VO")
@Data
public class AppMomentLikeRespVO {

    @Schema(description = "操作后的点赞总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer likeCount;

    @Schema(description = "操作后当前用户是否已赞", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean liked;

}
