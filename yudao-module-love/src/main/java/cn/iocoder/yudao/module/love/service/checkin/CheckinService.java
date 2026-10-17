package cn.iocoder.yudao.module.love.service.checkin;

import cn.iocoder.yudao.module.love.controller.app.checkin.vo.AppCheckinRespVO;
import cn.iocoder.yudao.module.love.controller.app.checkin.vo.AppCheckinSummaryRespVO;

import java.util.List;

/**
 * 每日打卡 Service 接口（INTEGRATION-DESIGN.md §5.2 checkin 组，M8）
 *
 * 以「当前登录用户」为出发点（SecurityFrameworkUtils 反查），不接受客户端传入 coupleId / userId，
 * 天然防 IDOR；写入与查询均挂当前 couple。check 幂等（uk_user_date 命中即返回），
 * 无按 id 写端点（撤销仅 admin delete）。
 *
 * @author 芋道源码
 */
public interface CheckinService {

    /**
     * 打卡今天（服务端以服务端当天为准，无补打）
     *
     * uk_user_date 一人一天一条：命中即幂等返回已有记录（重复打卡不打错）；
     * 未命中插入，uk 冲突（并发双击/两设备同打）回退为再查返回；
     * 同日解绑重绑场景刷新 couple_id 到当前 couple（MoodServiceImpl 覆盖写同款口径）
     *
     * @return 打卡记录（含 id/checkinDate/createTime，端上回包落态）
     */
    AppCheckinRespVO checkCheckin();

    /**
     * 获得当前 couple 指定月份的打卡记录（月历渲染数据源，双方全部行）
     *
     * @param month 月份 yyyy-MM，缺省当月，非法格式打全局 BAD_REQUEST
     * @return 打卡记录列表（checkin_date asc）
     */
    List<AppCheckinRespVO> getCheckinList(String month);

    /**
     * 获得打卡汇总（首页「今日打卡」条与打卡页页头共用）
     *
     * @return commonStreak（共同连续）/ myTotal / partnerTotal
     */
    AppCheckinSummaryRespVO getCheckinSummary();

}
