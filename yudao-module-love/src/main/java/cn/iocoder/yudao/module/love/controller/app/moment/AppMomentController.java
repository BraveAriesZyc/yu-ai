package cn.iocoder.yudao.module.love.controller.app.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentDeleteReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentLikeReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentLikeRespVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentRespVO;
import cn.iocoder.yudao.module.love.service.moment.MomentService;
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
 * 相册瞬间的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户/couple 由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId。
 *
 * 写端点统一 POST（M2 实施修正，§5.2 user 组注记）：Kuikly core 2.29 NetworkModule 仅支持 GET/POST；
 * delete 亦为 POST body（§5.2 todo/message 口径）。
 */
@Tag(name = "用户 App - 相册瞬间")
@RestController
@RequestMapping("/love/moment")
@Validated
public class AppMomentController {

    @Resource
    private MomentService momentService;

    @PostMapping("/create")
    @Operation(summary = "发布瞬间",
            description = "imageUrl 为 infra 文件服务上传返回的完整 URL（端上先 pickAndUpload 再发本端点）；" +
                    "text 配文可空；未绑定 couple 返回 1_003_004_003")
    public CommonResult<AppMomentRespVO> createMoment(@Valid @RequestBody AppMomentCreateReqVO reqVO) {
        return success(momentService.createMoment(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得当前 couple 的瞬间列表",
            description = "游标分页：id 倒序（新→旧），beforeId 空/0 = 取最新一页，非空取 id < beforeId 的下一页；" +
                    "limit 默认 30 上限 100；未绑定 couple 返回 1_003_004_003")
    @Parameter(name = "beforeId", description = "游标：取 id 小于该值的下一页，0/缺省 = 首页", example = "1024")
    @Parameter(name = "limit", description = "每页条数，默认 30 上限 100", example = "30")
    public CommonResult<List<AppMomentRespVO>> getMomentList(
            @RequestParam(value = "beforeId", required = false, defaultValue = "0") Long beforeId,
            @RequestParam(value = "limit", required = false, defaultValue = "30") Integer limit) {
        return success(momentService.getMomentList(beforeId, limit));
    }

    @PostMapping("/delete")
    @Operation(summary = "删除瞬间",
            description = "仅上传者本人可删（伴侣删对方的返回 1_003_008_001，防误删；admin 兜底可删任何，§5.3）；" +
                    "行不存在返回 1_003_008_000；逻辑删除，图片本体留 infra_file 不级联删")
    public CommonResult<Boolean> deleteMoment(@Valid @RequestBody AppMomentDeleteReqVO reqVO) {
        return success(momentService.deleteMoment(reqVO.getId()));
    }

    @PostMapping("/like")
    @Operation(summary = "点赞/取消点赞瞬间（幂等）",
            description = "M15：liked=true 点赞 / false 取消；重复操作幂等收敛不打错；" +
                    "瞬间不存在/跨 couple 统一返回 1_003_008_000（防探测，零新增错误码）；" +
                    "不发悄悄话消息、无 WS 信号（D-20c），对端靠 App 可见期静默轮询感知；" +
                    "回包恒操作后真值 {likeCount, liked}，端上按 id 原位替换（D-20e）")
    public CommonResult<AppMomentLikeRespVO> likeMoment(@Valid @RequestBody AppMomentLikeReqVO reqVO) {
        return success(momentService.likeMoment(reqVO));
    }

}
