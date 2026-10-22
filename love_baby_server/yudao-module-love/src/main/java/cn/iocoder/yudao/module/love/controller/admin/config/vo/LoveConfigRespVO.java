package cn.iocoder.yudao.module.love.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 业务配置 Response VO（admin 分页元素，M23）
 */
@Schema(description = "管理后台 - 业务配置 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveConfigRespVO {

    @Schema(description = "配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "配置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客服微信号")
    private String name;

    @Schema(description = "配置键", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "customer_service_wechat")
    private String configKey;

    @Schema(description = "配置值", requiredMode = Schema.RequiredMode.REQUIRED, example = "lovebaby-helper")
    private String configValue;

    @Schema(description = "备注", example = "我的页「帮助与客服」行复制引导")
    private String remark;

    @Schema(description = "状态（0启用 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
