package cn.iocoder.yudao.module.love.controller.admin.mood.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 心情记录 - admin 分页请求 VO（M31，D-13 裁决变更：补后台页）
 *
 * 筛选口径：coupleId 精确 / userId 精确 / moodLabel 模糊 / moodDate 日期区间（业务日期比
 * createTime 更贴近「那天心情如何」的查询语义）；moodDate 数组走 @DateTimeFormat yyyy-MM-dd
 * （LoveMomentPageReqVO 的 createTime 同款）。
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 心情记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoveMoodRecordPageReqVO extends PageParam {

    @Schema(description = "情侣关系编号", example = "2")
    private Long coupleId;

    @Schema(description = "记录用户编号", example = "2048")
    private Long userId;

    @Schema(description = "心情名快照，模糊匹配", example = "开心")
    private String moodLabel;

    @Schema(description = "心情日期区间")
    @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] moodDate;

}
