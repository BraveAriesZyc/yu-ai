package cn.iocoder.yudao.module.love.controller.admin.dailyquestion;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionRespVO;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionSaveReqVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminDailyQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 每日一问题库的管理后台接口（M32）
 *
 * 管理对象 = 每日一问的内容池（全局共享，B 端配置 App 只读经 day 快照下发）；
 * admin-api RESTful 不受限（§5.1，love_inspiration 同款：create POST / update PUT）。
 * 改题/删题不影响历史（day 快照行原样，D-19a/D-21）；停用题只退出未来轮换池。
 */
@Tag(name = "管理后台 - 每日一问题库")
@RestController
@RequestMapping("/love/daily-question")
@Validated
public class LoveDailyQuestionController {

    @Resource
    private LoveAdminDailyQuestionService loveAdminDailyQuestionService;

    @GetMapping("/page")
    @Operation(summary = "获得每日一问题目分页",
            description = "内容模糊 / 分类模糊 / status 精确 / 创建时间段；排序 id desc（新添加在前）")
    @PreAuthorize("@ss.hasPermission('love:daily-question:query')")
    public CommonResult<PageResult<LoveDailyQuestionRespVO>> getDailyQuestionPage(
            @Valid LoveDailyQuestionPageReqVO pageReqVO) {
        return success(loveAdminDailyQuestionService.getDailyQuestionPage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增题目",
            description = "status 未传落库默认启用；即时进轮换池（app 侧当日题已生成则次日生效）")
    @PreAuthorize("@ss.hasPermission('love:daily-question:create')")
    public CommonResult<LoveDailyQuestionRespVO> createDailyQuestion(
            @Valid @RequestBody LoveDailyQuestionSaveReqVO reqVO) {
        return success(loveAdminDailyQuestionService.createDailyQuestion(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新题目",
            description = "按 id 全字段保存（null 字段保持原值）；只影响轮换池，" +
                    "已出过题的天走 day 快照原样不变（D-19a 快照哲学）")
    @PreAuthorize("@ss.hasPermission('love:daily-question:update')")
    public CommonResult<Boolean> updateDailyQuestion(@Valid @RequestBody LoveDailyQuestionSaveReqVO reqVO) {
        loveAdminDailyQuestionService.updateDailyQuestion(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除题目",
            description = "逻辑删除；只退出轮换池，已出过题的天走 day 快照原样不变（D-19a 快照哲学）")
    @Parameter(name = "id", description = "题目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('love:daily-question:delete')")
    public CommonResult<Boolean> deleteDailyQuestion(@RequestParam("id") Long id) {
        loveAdminDailyQuestionService.deleteDailyQuestion(id);
        return success(true);
    }

}
