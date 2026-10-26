package cn.iocoder.yudao.module.love.controller.app.dailyquestion;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionAnswerReqVO;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionItemRespVO;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionTodayRespVO;
import cn.iocoder.yudao.module.love.service.dailyquestion.DailyQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 每日一问的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户/couple 由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId；
 * 答题不收日期参数（服务端以服务端当天为准，只能答当天，无补答，D-21，防端上时钟作弊）。
 *
 * 写端点统一 POST（M2 实施修正，§5.2 user 组注记）：Kuikly core 2.29 NetworkModule 仅支持 GET/POST。
 */
@Tag(name = "用户 App - 每日一问")
@RestController
@RequestMapping("/love/daily-question")
@Validated
public class AppDailyQuestionController {

    @Resource
    private DailyQuestionService dailyQuestionService;

    @GetMapping("/today")
    @Operation(summary = "获得今日题与双方作答状态",
            description = "当日题懒生成（当天首个访问者触发轮换取题，此后全天恒同题）；" +
                    "partnerAnswer 仅 revealed 时下发（揭晓前对方答案不出接口）；" +
                    "题库空 = 全字段 null（端上显「今日无题」占位）；未绑定 couple 返回 1_003_004_003")
    public CommonResult<AppDailyQuestionTodayRespVO> getTodayQuestion() {
        return success(dailyQuestionService.getTodayQuestion());
    }

    @PostMapping("/answer")
    @Operation(summary = "提交/修改今天的答案",
            description = "只答当天（无补答）；揭晓前自己的答案可覆盖写（mood record 口径），" +
                    "双方都已提交（已揭晓）再写返回 1_003_016_001；返回 latest 今日题态（答完即时反映互见）；" +
                    "未绑定 couple 返回 1_003_004_003")
    public CommonResult<AppDailyQuestionTodayRespVO> answerQuestion(
            @Valid @RequestBody AppDailyQuestionAnswerReqVO reqVO) {
        return success(dailyQuestionService.answerQuestion(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得当前 couple 指定月份的每日一问历史",
            description = "month=yyyy-MM，缺省当月，非法格式打全局 BAD_REQUEST；返回当月全部当日题" +
                    "（question_date asc，含没答的天）；partnerAnswer 同样仅 revealed 时下发；" +
                    "未绑定 couple 返回 1_003_004_003")
    @Parameter(name = "month", description = "月份 yyyy-MM，缺省当月", example = "2026-10")
    public CommonResult<List<AppDailyQuestionItemRespVO>> getDailyQuestionList(
            @RequestParam(value = "month", required = false) String month) {
        return success(dailyQuestionService.getDailyQuestionList(month));
    }

}
