package cn.iocoder.yudao.module.love.controller.app.checkin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.checkin.vo.AppCheckinRespVO;
import cn.iocoder.yudao.module.love.controller.app.checkin.vo.AppCheckinSummaryRespVO;
import cn.iocoder.yudao.module.love.service.checkin.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 每日打卡的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户/couple 由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId；
 * check 不收任何参数（服务端以服务端当天为准，无补打，防端上时钟作弊）。
 *
 * 写端点统一 POST（M2 实施修正，§5.2 user 组注记）：Kuikly core 2.29 NetworkModule 仅支持 GET/POST；
 * 无 delete/update 端点（打了就是打了，撤销仅 admin delete，§5.3）。
 */
@Tag(name = "用户 App - 每日打卡")
@RestController
@RequestMapping("/love/checkin")
@Validated
public class AppCheckinController {

    @Resource
    private CheckinService checkinService;

    @PostMapping("/check")
    @Operation(summary = "打卡今天",
            description = "不收任何参数；uk_user_date 一人一天一条，重复打卡幂等返回已有记录（不打错）；" +
                    "同日解绑重绑场景刷新 couple_id 到当前 couple（mood record 覆盖写同款口径）；" +
                    "未绑定 couple 返回 1_003_004_003")
    public CommonResult<AppCheckinRespVO> checkinToday() {
        return success(checkinService.checkCheckin());
    }

    @GetMapping("/list")
    @Operation(summary = "获得当前 couple 指定月份的打卡记录",
            description = "month=yyyy-MM，缺省当月，非法格式打全局 BAD_REQUEST；返回 couple 双方全部记录" +
                    "（checkin_date asc）；未绑定 couple 返回 1_003_004_003")
    @Parameter(name = "month", description = "月份 yyyy-MM，缺省当月", example = "2026-10")
    public CommonResult<List<AppCheckinRespVO>> getCheckinList(
            @RequestParam(value = "month", required = false) String month) {
        return success(checkinService.getCheckinList(month));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得打卡汇总",
            description = "commonStreak=共同连续（双方同日都打才累计，今天缺一方从昨天起算）/" +
                    "myTotal/partnerTotal 全量跨月累计；服务端内存聚合，一行 SQL")
    public CommonResult<AppCheckinSummaryRespVO> getCheckinSummary() {
        return success(checkinService.getCheckinSummary());
    }

}
