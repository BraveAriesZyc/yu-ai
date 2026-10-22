package cn.iocoder.yudao.module.love.controller.admin.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 相册瞬间 - admin 响应 VO（M9）
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 相册瞬间 Response VO")
@Data
public class LoveMomentRespVO {

    @Schema(description = "瞬间编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long coupleId;

    @Schema(description = "发布用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long userId;

    @Schema(description = "图片完整 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageUrl;

    @Schema(description = "多图 URL JSON 数组（M15 二期，≤9 张；imageUrl 恒为首图冗余；M18 补透出）")
    private String imageUrls;

    @Schema(description = "配文", example = "今天的晚霞")
    private String text;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;

}
