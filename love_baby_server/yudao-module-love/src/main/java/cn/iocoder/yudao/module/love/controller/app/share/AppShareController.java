package cn.iocoder.yudao.module.love.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryReportReqVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppBatteryRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationLatestRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPlaceListRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationPointVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationReportReqVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppLocationTrailRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppShareSettingRespVO;
import cn.iocoder.yudao.module.love.controller.app.share.vo.AppShareSettingUpdateReqVO;
import cn.iocoder.yudao.module.love.service.share.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 共享域（电量互看 + 位置/轨迹 + 共享开关）的 App 端接口：全部需登录
 * （无 @PermitAll，走 anyRequest().authenticated()），当前用户/couple 由 service 层经
 * SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId；
 * 开关关是对端「正常态」不打错误码（对方查询只见 shareEnabled:false），仅我方上报被拒收。
 *
 * 写端点统一 POST 而非 RESTful 的 PUT/DELETE（M2 实施修正，§5.2 user 组注记）：
 * Kuikly core 2.29 NetworkModule 仅支持 GET/POST（反编译 AAR 核实）。
 * 三资源合组一域（M26）：路径前缀 /love/share/{setting|battery|location}/**。
 */
@Tag(name = "用户 App - 共享域（电量/位置/开关）")
@RestController
@RequestMapping("/love/share")
@Validated
public class AppShareController {

    @Resource
    private ShareService shareService;

    // ========== 共享开关 ==========

    @GetMapping("/setting/get")
    @Operation(summary = "获得我的共享开关",
            description = "懒创建：无行按默认值返回（电量开/位置关），首次 update 才落行；位置默认关为 §12.4 强隐私定案")
    public CommonResult<AppShareSettingRespVO> getShareSetting() {
        return success(shareService.getShareSetting());
    }

    @PostMapping("/setting/update")
    @Operation(summary = "更新我的共享开关",
            description = "两开关均可空，null = 不改动该开关（端上单开关独立切换）；回更新后的全量开关")
    public CommonResult<AppShareSettingRespVO> updateShareSetting(@RequestBody @Valid AppShareSettingUpdateReqVO reqVO) {
        return success(shareService.updateShareSetting(reqVO));
    }

    // ========== 电量互看 ==========

    @PostMapping("/battery/report")
    @Operation(summary = "上报我的电量快照",
            description = "每用户一行 upsert（update_time 即 reportTime）；我方电量共享关闭时拒收 1_003_015_002；" +
                    "回 {mine, partner} 一次带回双方状态，端上免二次拉取")
    public CommonResult<AppBatteryRespVO> reportBattery(@RequestBody @Valid AppBatteryReportReqVO reqVO) {
        return success(shareService.reportBattery(reqVO));
    }

    @GetMapping("/battery/get")
    @Operation(summary = "获得电量双方状态",
            description = "电量页 60s 轮询用，回包结构与 battery/report 相同；partner 从未上报时电量字段为 null；" +
                    "TA 开关关只回 shareEnabled=false 不打错误码")
    public CommonResult<AppBatteryRespVO> getBattery() {
        return success(shareService.getBattery());
    }

    // ========== 位置/轨迹 ==========

    @PostMapping("/location/report")
    @Operation(summary = "上报位置点",
            description = "GCJ-02 坐标（端上 WGS-84→GCJ-02 纠偏后上传）；(0,0) 拒 1_003_015_001；" +
                    "30s 内重复上报静默吞点（服务端 create_time 口径限频）仍回最新点；" +
                    "顺带物理清除 30 天前旧点（保留策略）；我方位置共享关闭时拒收 1_003_015_000")
    public CommonResult<AppLocationPointVO> reportLocation(@RequestBody @Valid AppLocationReportReqVO reqVO) {
        return success(shareService.reportLocation(reqVO));
    }

    @GetMapping("/location/latest")
    @Operation(summary = "获得最新位置（我的 + TA 的）",
            description = "TA 开关关只回 shareEnabled=false；开关开但从未上报时 point=null（端上区分「没共享」与「没数据」）")
    public CommonResult<AppLocationLatestRespVO> getLatestLocation() {
        return success(shareService.getLatestLocation());
    }

    @GetMapping("/location/trail")
    @Operation(summary = "获得轨迹点",
            description = "target=self|partner；[startTs, endTs] epoch 毫秒闭区间；points 按 report_time 升序、" +
                    "最近 500 点截断（DESC 截断后翻转，保证是最近的点）+ truncated 标志；" +
                    "target=partner 且对方开关关回 shareEnabled=false + 空列表（不打错误码）")
    public CommonResult<AppLocationTrailRespVO> getTrail(
            @RequestParam("target") @NotEmpty(message = "target 不能为空") String target,
            @RequestParam("startTs") Long startTs,
            @RequestParam("endTs") Long endTs) {
        return success(shareService.getTrail(target, startTs, endTs));
    }

    @GetMapping("/location/place/list")
    @Operation(summary = "获得地点记录列表（M28）",
            description = "行动轨迹半层的地点列表数据源：30min 粒度 + 换地 ≥300m 即记的地点行（省市区 + POI/地名），" +
                    "report 顺路落库；参数口径与 location/trail 完全一致；places 按 report_time 升序、" +
                    "最近 100 条截断（DESC 截断后翻转）+ truncated 标志；" +
                    "target=partner 且对方开关关回 shareEnabled=false + 空列表（不打错误码）")
    public CommonResult<AppLocationPlaceListRespVO> getPlaceList(
            @RequestParam("target") @NotEmpty(message = "target 不能为空") String target,
            @RequestParam("startTs") Long startTs,
            @RequestParam("endTs") Long endTs) {
        return success(shareService.getPlaceList(target, startTs, endTs));
    }

    @PostMapping("/location/pull")
    @Operation(summary = "提醒对方更新位置（M28）",
            description = "行动轨迹半层「提醒 TA」按钮：给对方在线连接推 love-location-pull 信号帧（content=\"{}\"），" +
                    "对方端上收到即触发一次位置上报（其位置共享开 + App 前台才实际出点，双闸静默）；" +
                    "服务端拿不到 WS 投递结果，恒 success——端上 toast 用「已提醒」语义不承诺已更新，" +
                    "对方回点走既有 love-location-new 帧实时落 marker")
    public CommonResult<Boolean> pullPartnerLocation() {
        shareService.pullPartnerLocation();
        return success(true);
    }

}
