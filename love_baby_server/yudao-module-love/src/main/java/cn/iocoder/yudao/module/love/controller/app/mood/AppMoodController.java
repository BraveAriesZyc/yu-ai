package cn.iocoder.yudao.module.love.controller.app.mood;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionRespVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodRecordReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodRespVO;
import cn.iocoder.yudao.module.love.service.mood.MoodOptionService;
import cn.iocoder.yudao.module.love.service.mood.MoodService;
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
 * 每日心情的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId（防 IDOR）。
 * record 仍无独立错误码（uk_user_date 冲突走覆盖写不打错）；option 组错误码 1_003_002_xxx（M3.5 激活，D-14）。
 */
@Tag(name = "用户 App - 每日心情")
@RestController
@RequestMapping("/love/mood")
@Validated
public class AppMoodController {

    @Resource
    private MoodService moodService;
    @Resource
    private MoodOptionService moodOptionService;

    @PostMapping("/record")
    @Operation(summary = "记当日心情",
            description = "传 couple 私有字典的心情选项编号（M3.5）；uk_user_date 一人一天一条：同日已有记录则覆盖"
                    + " optionId 与快照三列（moodLabel/emoji/color 取自选项行，端上月历渲染只吃快照），未记录则插入；"
                    + "并发撞 uk 收敛为覆盖（§5.2 重复写覆盖语义，端上按成功处理非错误）")
    public CommonResult<Boolean> recordMood(@RequestBody @Valid AppMoodRecordReqVO reqVO) {
        return success(moodService.recordMood(reqVO));
    }

    @GetMapping("/record/list")
    @Operation(summary = "按月查询 couple 双方的心情记录",
            description = "month=yyyy-MM 解析当月起止日期闭区间查询（idx_couple_month）；某日无记录 = 回包无该日条目，端上按空缺渲染；"
                    + "moodDate 序列化为 yyyy-MM-dd（jsr310 LocalDateSerializer）；M3.5 起 RespVO 附带快照三列，"
                    + "端上渲染不依赖字典回查（删除选项后历史月历不变灰，D-14）")
    public CommonResult<List<AppMoodRespVO>> getMoodRecordList(
            @Parameter(description = "月份，格式 yyyy-MM（如 2026-10）", required = true, example = "2026-10")
            @RequestParam("month") String month) {
        return success(moodService.getMoodRecordList(month));
    }

    @GetMapping("/option/list")
    @Operation(summary = "查当前 couple 的心情选项字典",
            description = "couple 私有字典（双方共享一份），sort asc, id asc；进入心情页/切回 Tab 时拉取整表覆盖端上 MoodStore"
                    + "（§7.4 拉取触发点）；绑定播种行在前、双方新增行按创建序落尾")
    public CommonResult<List<AppMoodOptionRespVO>> getMoodOptionList() {
        return success(moodOptionService.getMoodOptionList());
    }

    @PostMapping("/option/create")
    @Operation(summary = "新增心情选项",
            description = "label/emoji/color 原样存储不校验取值（端上预设集选定后上送）；校验：couple 内重名 1_003_002_003、"
                    + "上限 20 1_003_002_002；sort 恒 999 落尾（不提供重排，M3.5 显式不做）；回包含服务端 id（端上按 String 解析 §5.1）")
    public CommonResult<AppMoodOptionRespVO> createMoodOption(@RequestBody @Valid AppMoodOptionCreateReqVO reqVO) {
        return success(moodOptionService.createMoodOption(reqVO));
    }

    @PostMapping("/option/delete")
    @Operation(summary = "删除心情选项",
            description = "逻辑删除；归属校验：不存在 1_003_002_000、跨 couple 1_003_002_001（防 IDOR）；"
                    + "历史月历渲染走 love_mood_record 快照列，删除选项后历史不变灰（D-14）；"
                    + "app-api 写端点恒 POST（core NetworkModule 仅 GET/POST，M2 实施修正沿用）")
    public CommonResult<Boolean> deleteMoodOption(@RequestBody @Valid AppMoodOptionIdReqVO reqVO) {
        moodOptionService.deleteMoodOption(reqVO);
        return success(true);
    }

}
