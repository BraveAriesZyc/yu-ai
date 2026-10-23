package cn.iocoder.yudao.module.love.controller.app.wishtemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 心愿墙挑选模板 Response VO（M25，app /list 列表元素）
 *
 * Long id 序列化为字符串（App 端统一字符串 id 口径，即 sendWishes 上送的 wishKey）；
 * subtitle/icon/color 可能为空（运营未填），端上各有兜底
 * （icon 未知名兜底 Heart、hex 解析失败兜底主题粉）；不含 sort/审计列。
 */
@Schema(description = "用户 App - 心愿墙挑选模板 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppWishTemplateRespVO {

    @Schema(description = "模板编号（= App 送出 wishKey）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "分类（App 端动态聚合 chips）", example = "想亲亲")
    private String category;

    @Schema(description = "标题（气泡主文案）", requiredMode = Schema.RequiredMode.REQUIRED, example = "求亲亲")
    private String title;

    @Schema(description = "副标题（卡片第二行，可空）", example = "mua 一口就跑")
    private String subtitle;

    @Schema(description = "卡片渐变起始色 hex #RRGGBB", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FF9AC4")
    private String colorStart;

    @Schema(description = "卡片渐变结束色 hex #RRGGBB", requiredMode = Schema.RequiredMode.REQUIRED, example = "#FF5A8C")
    private String colorEnd;

    @Schema(description = "图标（端上 AppIconType 枚举 name，未知名端上兜底 Heart）", requiredMode = Schema.RequiredMode.REQUIRED, example = "Heart")
    private String icon;

}
