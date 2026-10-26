package cn.iocoder.yudao.module.love.controller.admin.mood;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordRespVO;
import cn.iocoder.yudao.module.love.service.admin.LoveAdminMoodRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 心情记录的 admin 端接口（M31，D-13 裁决变更：用户拍板补后台页）：供管理后台（admin-api），
 * 权限串 love:mood-record:query|delete。常规 page + delete 两件套（LoveMomentController 同款）；
 * 无 create/update（记录由 App 端覆盖写产生，一人一天一条 uk_user_date）。
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 心情记录")
@RestController
@RequestMapping("/love/mood-record")
@Validated
public class LoveMoodRecordController {

    @Resource
    private LoveAdminMoodRecordService moodRecordService;

    @GetMapping("/page")
    @Operation(summary = "获得心情记录分页",
            description = "筛选：coupleId 精确 / userId 精确 / moodLabel 模糊 / moodDate 日期区间")
    @PreAuthorize("@ss.hasPermission('love:mood-record:query')")
    public CommonResult<PageResult<LoveMoodRecordRespVO>> getMoodRecordPage(LoveMoodRecordPageReqVO pageReqVO) {
        return success(moodRecordService.getMoodRecordPage(pageReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除心情记录",
            description = "逻辑删除；行不存在 1_003_002_005；只删记录快照行，不动 couple 私有选项字典（D-14 边界）")
    @Parameter(name = "id", description = "心情记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('love:mood-record:delete')")
    public CommonResult<Boolean> deleteMoodRecord(@RequestParam("id") Long id) {
        return success(moodRecordService.deleteMoodRecord(id));
    }

}
