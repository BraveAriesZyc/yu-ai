package cn.iocoder.yudao.module.love.controller.app.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileRespVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserStatsRespVO;
import cn.iocoder.yudao.module.love.service.auth.AppAuthService;
import cn.iocoder.yudao.module.love.service.user.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户资料的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 userId（防 IDOR）。
 */
@Tag(name = "用户 App - 用户资料")
@RestController
@RequestMapping("/love/user")
@Validated
public class AppUserController {

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private AppAuthService authService;

    @Resource
    private SecurityProperties securityProperties;

    @GetMapping("/get")
    @Operation(summary = "获得当前登录用户的个人信息",
            description = "昵称/头像一律由此接口回查 love_user（MEMBER 上下文 buildUserInfo 返回空 Map，见 INTEGRATION-DESIGN.md M-6）")
    public CommonResult<AppUserProfileRespVO> getUser() {
        return success(userProfileService.getUser());
    }

    @GetMapping("/stats")
    @Operation(summary = "获得当前登录用户的恋爱统计",
            description = "在一起天数（couple.bind_time 自然日含当天）+ 当前 couple 的悄悄话/待办/心情计数；未绑定全 0")
    public CommonResult<AppUserStatsRespVO> getStats() {
        return success(userProfileService.getStats());
    }

    @PostMapping("/update")
    @Operation(summary = "更新当前登录用户的昵称/头像",
            description = "M2 实施修正：app-api 用 POST 而非 RESTful 的 PUT —— Kuikly core 2.29 NetworkModule 仅支持 GET/POST（反编译 AAR 核实），M3 的 todo update/delete 同理；admin-api 不受限仍 RESTful")
    public CommonResult<Boolean> updateUser(@RequestBody @Valid AppUserProfileUpdateReqVO reqVO) {
        userProfileService.updateUser(reqVO);
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "注销当前登录账号",
            description = "M33 上架合规（商店硬性要求）：解绑（物理删 couple）+ 个人敏感数据物理清除" +
                    "（位置轨迹/地点记录/电量快照/共享开关）+ mobile 改写释放 uk_mobile + 逻辑删除用户；" +
                    "couple 共同业务数据保留（D-11）。无请求体，仅确认弹窗后调用")
    public CommonResult<Boolean> cancelAccount(HttpServletRequest request) {
        userProfileService.cancelAccount();
        // 撤销当前 token（提取同 AppAuthController.logout）：置于 service 成功之后；logout 失败
        // 不回滚注销——token TTL 内残留由 getValidatedCurrentUser 停用收口兜底（逻辑删行查不到
        // = 停用同语义，端上清会话回 Gate）
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        authService.logout(token);
        return success(true);
    }

}
