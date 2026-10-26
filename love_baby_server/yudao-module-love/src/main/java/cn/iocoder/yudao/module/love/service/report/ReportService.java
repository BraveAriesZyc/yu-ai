package cn.iocoder.yudao.module.love.service.report;

import cn.iocoder.yudao.module.love.controller.app.report.vo.AppReportMonthlyRespVO;

/**
 * 恋爱月报 Service 接口（M30）
 *
 * @author 芋道源码
 */
public interface ReportService {

    /**
     * 获得当前登录用户的恋爱月报（单月五域只读聚合；未绑定静默全 0）
     *
     * @param month yyyy-MM，缺省/空白取当月，非法格式打全局参数错误 400
     * @return 月报数据
     */
    AppReportMonthlyRespVO getMonthlyReport(String month);

}
