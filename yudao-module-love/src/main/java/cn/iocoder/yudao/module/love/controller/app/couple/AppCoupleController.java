package cn.iocoder.yudao.module.love.controller.app.couple;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleBindReqVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleCreateInviteRespVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleOnlineRespVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleRespVO;
import cn.iocoder.yudao.module.love.service.couple.CoupleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 情侣关系的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId（防 IDOR）。
 */
@Tag(name = "用户 App - 情侣关系")
@RestController
@RequestMapping("/love/couple")
@Validated
public class AppCoupleController {

    @Resource
    private CoupleService coupleService;

    @PostMapping("/create-invite")
    @Operation(summary = "生成/刷新我的邀请码",
            description = "已有未过期邀请码则复用（不续期），否则重新生成，有效期 7 天（写 love_user.invite_code / invite_expire_time）")
    public CommonResult<AppCoupleCreateInviteRespVO> createInvite() {
        return success(coupleService.createInvite());
    }

    @PostMapping("/bind")
    @Operation(summary = "凭邀请码绑定情侣关系",
            description = "事务内执行 D-8 步骤：查码 → 惰性判过期 → 非自绑 → 升序锁双方行锁内校验均未绑定（couple 用 FOR UPDATE 当前读，终审 A/B 互绑竞态）→ 插入 love_couple；uk_user_a/uk_user_b 唯一键仅终审同邀请方多受邀方/同一受邀方多邀请方两类竞态")
    public CommonResult<AppCoupleRespVO> bind(@RequestBody @Valid AppCoupleBindReqVO reqVO) {
        return success(coupleService.bind(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得我的情侣关系与伴侣信息（昵称/头像回查 love_user）",
            description = "未绑定时返回 1_003_004_003，端上据此切回绑定引导态（解绑/被解绑后对方端的感知路径）")
    public CommonResult<AppCoupleRespVO> getCouple() {
        return success(coupleService.getCouple());
    }

    @GetMapping("/online")
    @Operation(summary = "查询伴侣在线状态（M13）",
            description = "以 WS 内存会话表为准（sender-type=local 单实例，D-18）：对方名下任一在线连接即 true；"
                    + "websocket 关闭/未桥接端恒 false（渐进降级）；未绑定 → 1_003_004_003，错误码零新增；"
                    + "回包为对象形态——端上 HttpClient toOk 标量落空对象，禁改回 CommonResult<Boolean>（M11 冻结决策）；"
                    + "端上仅聊天页停留期间 5s 轮询（离页重置），无 lastSeen，「最近在线时间」显式不做（D-18）")
    public CommonResult<AppCoupleOnlineRespVO> getCoupleOnline() {
        return success(coupleService.getCoupleOnline());
    }

    @PostMapping("/unbind")
    @Operation(summary = "解绑情侣关系",
            description = "双方任一可发起，事务内物理删除 couple 行（D-11），todo/mood/message 数据保留，重绑产生新 couple_id")
    public CommonResult<Boolean> unbind() {
        coupleService.unbind();
        return success(true);
    }

}
