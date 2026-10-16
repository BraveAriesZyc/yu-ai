package cn.iocoder.yudao.module.love.controller.app.couple.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 我的情侣关系 Response VO，bind 成功与 couple/get 统一使用")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppCoupleRespVO {

    @Schema(description = "情侣关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "发起方用户编号（邀请码属主）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userIdA;

    @Schema(description = "受邀方用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long userIdB;

    @Schema(description = "关系状态（0 正常）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "绑定时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime bindTime;

    @Schema(description = "伴侣信息（昵称/头像回查 love_user，见 INTEGRATION-DESIGN.md §5.2）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Partner partner;

    @Schema(description = "用户 App - 伴侣信息")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Partner {

        @Schema(description = "伴侣用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
        private Long id;

        @Schema(description = "伴侣昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户5678")
        private String nickname;

        @Schema(description = "伴侣头像", example = "https://www.iocoder.cn/1.png")
        private String avatar;

    }

}
