package cn.iocoder.yudao.module.love.controller.app.inspiration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.inspiration.vo.AppInspirationQuoteRespVO;
import cn.iocoder.yudao.module.love.controller.app.inspiration.vo.AppInspirationRespVO;
import cn.iocoder.yudao.module.love.service.inspiration.InspirationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 恋爱灵感 App 接口（M17，首页「今日情话」「约会灵感」两卡数据源）
 *
 * 全局共享运营内容：无 coupleId/userId 依赖（服务端不反查，无 §5.2 归属约束），
 * 只读端点零错误路径（池空走服务端兜底文案）；回包恒对象形态
 * （端上 HttpClient toOk 只分派对象/数组，标量落空对象——M11 冻结口径）。
 */
@Tag(name = "用户 App - 恋爱灵感")
@RestController
@RequestMapping("/love/inspiration")
@Validated
public class AppInspirationController {

    @Resource
    private InspirationService inspirationService;

    @GetMapping("/today")
    @Operation(summary = "获得今日情话",
            description = "服务端按日轮换（epochDay 对启用情话池取模，id asc 稳定序），同一天恒同句；" +
                    "池空返回兜底文案不打错")
    public CommonResult<AppInspirationQuoteRespVO> getTodayQuote() {
        return success(inspirationService.getTodayQuote());
    }

    @GetMapping("/quote-random")
    @Operation(summary = "随机换一句情话",
            description = "随机取启用情话池一条（排除今日句，池 >1 时必换）；池 ≤1 条返回今日句本身")
    public CommonResult<AppInspirationQuoteRespVO> getRandomQuote() {
        return success(inspirationService.getRandomQuote());
    }

    @GetMapping("/list")
    @Operation(summary = "获得约会灵感全量列表",
            description = "kind=2 启用行，id asc 稳定序全量返回；端上分类筛选 + 「换一换」本地打乱" +
                    "（几十条量级全量拉一次即可，无分页）")
    public CommonResult<List<AppInspirationRespVO>> getInspirationList() {
        return success(inspirationService.getInspirationList());
    }

}
