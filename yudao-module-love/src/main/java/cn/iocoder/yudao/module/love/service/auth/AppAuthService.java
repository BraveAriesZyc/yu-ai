package cn.iocoder.yudao.module.love.service.auth;

import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthLoginReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthRefreshTokenReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthResetPasswordReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthSmsSendReqVO;

/**
 * C 端用户的认证 Service 接口
 *
 * 注册/登录/登出/重置密码。令牌全部复用 system 的 OAuth2TokenCommonApi（clientId = couple-app，userType = MEMBER），
 * 短信验证码复用 system 的 SmsCodeApi（scene 固定 1，MEMBER_LOGIN），love 模块绝不自造 token/短信。
 *
 * @author 芋道源码
 */
public interface AppAuthService {

    /**
     * 手机号 + 密码注册（短信验证码校验），成功后自动登录
     *
     * @param reqVO 注册信息
     * @return 登录结果
     */
    AppAuthLoginRespVO register(AppAuthRegisterReqVO reqVO);

    /**
     * 手机号 + 密码登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO login(AppAuthLoginReqVO reqVO);

    /**
     * 短信验证码登录，未注册自动注册（首期主路径）
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO smsLogin(AppAuthSmsLoginReqVO reqVO);

    /**
     * 发送短信验证码
     *
     * @param reqVO 发送请求
     */
    void sendSmsCode(AppAuthSmsSendReqVO reqVO);

    /**
     * 刷新访问令牌
     *
     * @param reqVO 刷新请求
     * @return 登录结果
     */
    AppAuthLoginRespVO refreshToken(AppAuthRefreshTokenReqVO reqVO);

    /**
     * 重置密码（短信验证码校验）
     *
     * @param reqVO 重置请求
     */
    void resetPassword(AppAuthResetPasswordReqVO reqVO);

    /**
     * 登出：移除访问令牌（连带刷新令牌），并记录登出日志
     *
     * @param token 访问令牌
     */
    void logout(String token);

}
