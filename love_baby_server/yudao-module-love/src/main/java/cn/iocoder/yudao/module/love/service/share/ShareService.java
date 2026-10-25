package cn.iocoder.yudao.module.love.service.share;

import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryReportReqVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationLatestRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPlaceListRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPointVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationReportReqVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationTrailRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppShareSettingRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppShareSettingUpdateReqVO;

/**
 * 共享域（电量互看 + 位置/轨迹 + 共享开关）Service 接口
 *
 * M26：三资源合组一个域（7 端点合一 Controller），本期纯 app-api（anniversary 前例，无 admin 通道）；
 * 全部端点需登录 + 绑定 couple（未绑定 1_003_004_003、停用 1_003_005_002，service 四件套收口）。
 *
 * @author 芋道源码
 */
public interface ShareService {

    /**
     * 查我的共享开关（懒创建：无行按默认值返回——电量开/位置关）
     */
    AppShareSettingRespVO getShareSetting();

    /**
     * 更新我的共享开关（null = 不改动该开关；懒创建行），回更新后的全量开关
     */
    AppShareSettingRespVO updateShareSetting(AppShareSettingUpdateReqVO reqVO);

    /**
     * 上报电量快照（每用户一行 upsert，update_time 即 reportTime），回 {mine, partner}
     */
    AppBatteryRespVO reportBattery(AppBatteryReportReqVO reqVO);

    /**
     * 查电量双方状态（电量页 60s 轮询用），回包结构与 reportBattery 相同
     */
    AppBatteryRespVO getBattery();

    /**
     * 上报位置点（30s 限频 + 30 天保留策略顺带清理），回操作后我的最新点
     */
    AppLocationPointVO reportLocation(AppLocationReportReqVO reqVO);

    /**
     * 查最新位置（我的 + TA 的），TA 开关关只回 shareEnabled=false 不打错
     */
    AppLocationLatestRespVO getLatestLocation();

    /**
     * 查轨迹点（target=self|partner，[startTs, endTs] 毫秒区间闭区间，升序，最近 500 点截断）
     */
    AppLocationTrailRespVO getTrail(String target, Long startTs, Long endTs);

    /**
     * 查地点记录（target=self|partner，[startTs, endTs] 毫秒区间闭区间，升序，最近 100 条截断）
     *
     * M28 行动轨迹地点记录：30min 粒度 + 换地 ≥300m 即记的地点列表（省市区 + POI/地名）
     */
    AppLocationPlaceListRespVO getPlaceList(String target, Long startTs, Long endTs);

    /**
     * 提醒对方更新位置（WS 推 love-location-pull 帧，content = "{}" 占位）
     *
     * 对方在线且前台 + 位置共享开才实际回点（端上 doReport 双闸），其余场景静默——
     * 服务端拿不到 WS 投递结果（sender 返回 void），端上 toast 用「已提醒」语义不承诺已更新
     */
    void pullPartnerLocation();

}
