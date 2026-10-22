package cn.iocoder.yudao.module.love.controller.admin.couple.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情侣关系分页行（§5.3：join love_user 带出双方昵称）
 *
 * 注意：该 VO 同时作为 LoveCoupleMapper.selectPageWithUser 的 MyBatis resultType
 * （方法返回值 IPage&lt;LoveCoupleRespVO&gt; 泛型推断），列别名下划线 → 驼峰自动映射，
 * 因此字段名与 SQL 别名（user_a_nickname 等）严格驼峰对应，勿改名。
 */
@Schema(description = "管理后台 - 情侣关系 Response VO")
@Data
public class LoveCoupleRespVO {

    @Schema(description = "关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "发起方用户编号（邀请码属主）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userAId;

    @Schema(description = "受邀方用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long userBId;

    @Schema(description = "发起方昵称（join love_user，已删用户为 null）", example = "小爱")
    private String userANickname;

    @Schema(description = "受邀方昵称（join love_user，已删用户为 null）", example = "小侣")
    private String userBNickname;

    @Schema(description = "关系状态，首期恒为 0（正常）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "绑定时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime bindTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
