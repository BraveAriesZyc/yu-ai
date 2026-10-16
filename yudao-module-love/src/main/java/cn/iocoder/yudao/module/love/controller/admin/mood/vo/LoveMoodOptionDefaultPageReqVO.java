package cn.iocoder.yudao.module.love.controller.admin.mood.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 心情默认选项分页 Request VO（M3.5）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoveMoodOptionDefaultPageReqVO extends PageParam {

    @Schema(description = "心情名关键字（模糊匹配）", example = "开心")
    private String label;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

}
