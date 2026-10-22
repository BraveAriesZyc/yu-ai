package cn.iocoder.yudao.module.love.controller.admin.moment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 相册瞬间 - admin 分页请求 VO（M9）
 *
 * 筛选三项冻结口径：coupleId 精确 / userId 精确 / 创建时间段；createTime 数组走
 * @DateTimeFormat yyyy-MM-dd（LoveCheckinPageReqVO 同款）。
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 相册瞬间分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoveMomentPageReqVO extends PageParam {

    @Schema(description = "情侣关系编号", example = "2")
    private Long coupleId;

    @Schema(description = "发布用户编号", example = "2048")
    private Long userId;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] createTime;

}
