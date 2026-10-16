package cn.iocoder.yudao.module.love.controller.admin.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 待办 Response VO")
@Data
public class LoveTodoRespVO {

    @Schema(description = "待办编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "情侣关系编号（couple 共享；解绑后留历史值，D-11）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long coupleId;

    @Schema(description = "创建人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerId;

    @Schema(description = "待办标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "一起看电影")
    private String title;

    @Schema(description = "是否完成", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean done;

    @Schema(description = "标签文本（家庭/恋爱/健康/生活/随手记）", example = "恋爱")
    private String tag;

    @Schema(description = "标签颜色，hex 色值字符串", example = "#FF6B81")
    private String color;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
