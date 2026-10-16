package cn.iocoder.yudao.module.love.controller.app.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthLoginReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthRefreshTokenReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthResetPasswordReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthSmsSendReqVO;
import cn.iocoder.yudao.module.love.service.auth.AppAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 认证")
@RestController
@RequestMapping("/love/auth")
@Validated
@Slf4j
public class AppAuthController {

    @Resource
    private AppAuthService authService;

    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/register")
    @PermitAll
    @Operation(summary = "手机号 + 密码注册（短信验证码校验），成功后自动登录")
    @RateLimiter(time = 60, count = 6, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<AppAuthLoginRespVO> register(@RequestBody @Valid AppAuthRegisterReqVO reqVO) {
        return success(authService.register(reqVO));
    }

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "手机号 + 密码登录")
    @RateLimiter(time = 60, count = 6, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<AppAuthLoginRespVO> login(@RequestBody @Valid AppAuthLoginReqVO reqVO) {
        return success(authService.login(reqVO));
    }

    @PostMapping("/sms-login")
    @PermitAll
    @Operation(summary = "短信验证码登录，未注册自动注册", description = "首期主路径")
    @RateLimiter(time = 60, count = 6, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<AppAuthLoginRespVO> smsLogin(@RequestBody @Valid AppAuthSmsLoginReqVO reqVO) {
        return success(authService.smsLogin(reqVO));
    }

    @PostMapping("/send-sms-code")
    @PermitAll
    @Operation(summary = "发送短信验证码", description = "scene 固定传 1（登录场景），服务端按 1 处理")
    @RateLimiter(time = 60, count = 6, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<Boolean> sendSmsCode(@RequestBody @Valid AppAuthSmsSendReqVO reqVO) {
        authService.sendSmsCode(reqVO);
        return success(true);
    }

    @PostMapping("/refresh-token")
    @PermitAll
    @Operation(summary = "刷新令牌")
    public CommonResult<AppAuthLoginRespVO> refreshToken(@RequestBody @Valid AppAuthRefreshTokenReqVO reqVO) {
        return success(authService.refreshToken(reqVO));
    }

    @PostMapping("/reset-password")
    @PermitAll
    @Operation(summary = "重置密码（短信验证码校验）")
    @RateLimiter(time = 60, count = 6, keyResolver = ClientIpRateLimiterKeyResolver.class)
    public CommonResult<Boolean> resetPassword(@RequestBody @Valid AppAuthResetPasswordReqVO reqVO) {
        authService.resetPassword(reqVO);
        return success(true);
    }

    @PostMapping("/logout")
    @Operation(summary = "登出") // 无 @PermitAll，需要登录后访问
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        authService.logout(token);
        return success(true);
    }

}
