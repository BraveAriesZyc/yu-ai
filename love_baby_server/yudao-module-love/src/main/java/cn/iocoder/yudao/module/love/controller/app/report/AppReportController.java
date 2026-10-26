package cn.iocoder.yudao.module.love.controller.app.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.report.vo.AppReportMonthlyRespVO;
import cn.iocoder.yudao.module.love.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 恋爱月报的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 userId（防 IDOR）。
 */
@Tag(name = "用户 App - 恋爱月报")
@RestController
@RequestMapping("/love/report")
@Validated
public class AppReportController {

    @Resource
    private ReportService reportService;

    @GetMapping("/monthly")
    @Operation(summary = "获得当前登录用户的恋爱月报",
            description = "单月五域只读聚合（打卡我/TA/共同天数 + 心情条数与 Top3 分布 + 悄悄话/相册/心愿完成计数）"
                    + "+ 在一起天数；month=yyyy-MM 缺省当月、非法格式 400；未绑定静默全 0（不抛 COUPLE_NOT_EXISTS）")
    public CommonResult<AppReportMonthlyRespVO> getMonthlyReport(
            @RequestParam(value = "month", required = false) String month) {
        return success(reportService.getMonthlyReport(month));
    }

}
