package cn.iocoder.yudao.module.love.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 业务配置新增/更新 Request VO（M23）
 *
 * create：id 为空；update：id 必填（save 复用一 VO，B 端表单弹窗统一提交）。
 * configKey 是 App 读取契约：小写字母开头 + 小写字母数字下划线（@Pattern 收口，
 * ⚠ 校验违规经全局异常处理包成 HTTP 200 body code=400，冒烟断言勿期待 HTTP 400——M16 教训）；
 * configValue 要求非空（清空诉求用「停用」承载，语义更明确，App 侧走内置兜底）；
 * status 不传时落库默认 0 启用（MP 插入忽略 null 字段走列默认）。
 */
@Schema(description = "管理后台 - 业务配置新增/更新 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoveConfigSaveReqVO {

    /**
     * 配置键格式：小写字母开头，仅小写字母/数字/下划线，总长 ≤64（与 @Length 双闸）
     */
    public static final String KEY_PATTERN = "^[a-z][a-z0-9_]{0,63}$";

    @Schema(description = "配置编号（update 必填，create 不传）", example = "1")
    private Long id;

    @Schema(description = "配置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客服微信号")
    @NotEmpty(message = "配置名称不能为空")
    @Length(max = 64, message = "配置名称长度不能超过 64 位")
    private String name;

    @Schema(description = "配置键（App 读取契约，小写字母数字下划线）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "customer_service_wechat")
    @NotEmpty(message = "配置键不能为空")
    @Pattern(regexp = KEY_PATTERN, message = "配置键必须以小写字母开头，仅含小写字母、数字、下划线")
    private String configKey;

    @Schema(description = "配置值", requiredMode = Schema.RequiredMode.REQUIRED, example = "lovebaby-helper")
    @NotEmpty(message = "配置值不能为空")
    @Length(max = 500, message = "配置值长度不能超过 500 位")
    private String configValue;

    @Schema(description = "备注（说明用途与消费位置，提醒键是 App 契约勿随意改）", example = "我的页「帮助与客服」行复制引导")
    @Length(max = 200, message = "备注长度不能超过 200 位")
    private String remark;

    @Schema(description = "状态（0启用 1停用；停用不下发 App）", example = "0")
    private Integer status;

}
