package cn.iocoder.yudao.module.love.controller.app.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 相册瞬间 - App 端响应 VO（M9；M15 多图 + 点赞扩展）
 *
 * id/userId 一律 Long：JSON 出参超 2^53-1 时 NumberSerializer 输出字符串、范围内是数字，
 * 端上一律 optString 解析（§5.1 NumberSerializer 数字/字符串双向兼容）。
 * createTime 为 epoch 毫秒（yudao 全局时间序列化出参），端上发「刚刚/x 分钟前」用。
 * imageUrls/likeCount/liked 由 service 层组装（M15，接口只加不改 D-20g）：
 * imageUrls 恒非空（存量单图 = [imageUrl]），likeCount ∈ {0,1,2}（couple 恒 2 人），
 * liked = 当前用户是否已赞（按人算，双方视角不同）。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 相册瞬间 Response VO")
@Data
public class AppMomentRespVO {

    @Schema(description = "瞬间编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "发布用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long userId;

    @Schema(description = "首图完整 URL（imageUrls[0] 冗余，admin/web 沿用）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageUrl;

    @Schema(description = "多图完整 URL 列表（M15，恒非空；存量单图 = [imageUrl]）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> imageUrls;

    @Schema(description = "配文（空串 = 无配文）", example = "今天的晚霞")
    private String text;

    @Schema(description = "发布时间（epoch 毫秒）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long createTime;

    @Schema(description = "点赞数（M15，couple 恒 2 人，∈{0,1,2}）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer likeCount;

    @Schema(description = "当前用户是否已赞（M15，按人算）", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean liked;

}
