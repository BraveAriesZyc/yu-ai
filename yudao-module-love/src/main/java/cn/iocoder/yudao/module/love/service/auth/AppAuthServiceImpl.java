package cn.iocoder.yudao.module.love.service.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthLoginReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthRefreshTokenReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthResetPasswordReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import cn.iocoder.yudao.module.love.controller.app.auth.vo.AppAuthSmsSendReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_MOBILE_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_MOBILE_NOT_EXISTS;

/**
 * C 端用户的认证 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class AppAuthServiceImpl implements AppAuthService {

    /**
     * OAuth2 客户端编号，对应 love.sql 中 system_oauth2_client 的 couple-app 行
     */
    public static final String CLIENT_ID_COUPLE_APP = "couple-app";

    /**
     * ⚠️ 兜底万能验证码（测试用）：任意手机号填此验证码即可跳过短信校验，省去联调时每条短信的费用；
     * 接入真实短信渠道后，注释掉 {@link #validateSmsCode} 中命中 MASTER_SMS_CODE 的 if 即恢复真实校验
     * （生产环境必须关闭，已列入 INTEGRATION-DESIGN.md D-4 上线检查项）
     */
    public static final String MASTER_SMS_CODE = "888888";

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;
    @Resource
    private LoginLogApi loginLogApi;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public AppAuthLoginRespVO register(AppAuthRegisterReqVO reqVO) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_MOBILE;
        // 1. 校验并使用短信验证码（scene 固定 1，见 INTEGRATION-DESIGN.md D-3）
        validateSmsCode(reqVO.getMobile(), reqVO.getCode());
        // 2. 校验手机号是否已注册（应用层查重 + uk_mobile 唯一键兜底）
        validateMobileNotExists(reqVO.getMobile());
        // 3. 创建 love_user 用户，BCrypt 加密密码
        LoveUserDO user = new LoveUserDO()
                .setMobile(reqVO.getMobile())
                .setPassword(encodePassword(reqVO.getPassword()))
                .setNickname(buildDefaultNickname(reqVO.getMobile()))
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        try {
            loveUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw exception(AUTH_MOBILE_EXISTS);
        }
        // 4. 创建令牌，记录登录日志
        return createTokenAfterLoginSuccess(user.getId(), reqVO.getMobile(), logTypeEnum);
    }

    @Override
    public AppAuthLoginRespVO login(AppAuthLoginReqVO reqVO) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_MOBILE;
        // 1. 校验账号是否存在
        LoveUserDO user = loveUserMapper.selectByMobile(reqVO.getMobile());
        if (user == null) {
            createLoginLog(null, reqVO.getMobile(), logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 2. 校验密码（纯短信注册用户无密码，matches 直接返回 false）
        if (!passwordEncoder.matches(reqVO.getPassword(), user.getPassword())) {
            createLoginLog(user.getId(), reqVO.getMobile(), logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 3. 校验是否停用
        validateUserStatus(user, reqVO.getMobile(), logTypeEnum);
        // 4. 创建令牌，记录登录日志
        return createTokenAfterLoginSuccess(user.getId(), reqVO.getMobile(), logTypeEnum);
    }

    @Override
    public AppAuthLoginRespVO smsLogin(AppAuthSmsLoginReqVO reqVO) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_SMS;
        // 1. 校验并使用短信验证码
        validateSmsCode(reqVO.getMobile(), reqVO.getCode());
        // 2. 查询用户，未注册自动注册（首期主路径）
        LoveUserDO user = loveUserMapper.selectByMobile(reqVO.getMobile());
        if (user == null) {
            user = createUserByMobile(reqVO.getMobile());
        }
        // 3. 校验是否停用
        validateUserStatus(user, reqVO.getMobile(), logTypeEnum);
        // 4. 创建令牌，记录登录日志
        return createTokenAfterLoginSuccess(user.getId(), reqVO.getMobile(), logTypeEnum);
    }

    @Override
    public void sendSmsCode(AppAuthSmsSendReqVO reqVO) {
        // 场景固定 MEMBER_LOGIN(1)，App 端仅用于登录/注册/重置密码（见 INTEGRATION-DESIGN.md D-3）
        // 每手机号的频控（1 分钟 1 条 / 每天 10 条）由 system 的 SmsCodeService 保证
        smsCodeApi.sendSmsCode(new SmsCodeSendReqDTO()
                .setMobile(reqVO.getMobile())
                .setScene(SmsSceneEnum.MEMBER_LOGIN.getScene())
                .setCreateIp(getClientIP()));
    }

    @Override
    public AppAuthLoginRespVO refreshToken(AppAuthRefreshTokenReqVO reqVO) {
        // 转发 OAuth2TokenCommonApi；clientId 与令牌不匹配、刷新令牌无效或已过期时，由 system 抛出对应错误
        OAuth2AccessTokenRespDTO accessTokenDO = oauth2TokenApi.refreshAccessToken(
                reqVO.getRefreshToken(), reqVO.getClientId());
        return buildLoginRespVO(accessTokenDO);
    }

    @Override
    public void resetPassword(AppAuthResetPasswordReqVO reqVO) {
        // 1. 校验并使用短信验证码
        validateSmsCode(reqVO.getMobile(), reqVO.getCode());
        // 2. 校验用户存在
        LoveUserDO user = loveUserMapper.selectByMobile(reqVO.getMobile());
        if (user == null) {
            throw exception(AUTH_MOBILE_NOT_EXISTS);
        }
        // 3. 更新密码
        loveUserMapper.updateById(new LoveUserDO().setId(user.getId())
                .setPassword(encodePassword(reqVO.getPassword())));
    }

    @Override
    public void logout(String token) {
        // 令牌为空，无需处理
        if (StrUtil.isBlank(token)) {
            return;
        }
        // 1. 移除访问令牌（连带删除刷新令牌）
        OAuth2AccessTokenRespDTO accessTokenDO = oauth2TokenApi.removeAccessToken(token);
        if (accessTokenDO == null) {
            return;
        }
        // 2. 记录登出日志
        createLogoutLog(accessTokenDO.getUserId(), accessTokenDO.getUserType());
    }

    private LoveUserDO createUserByMobile(String mobile) {
        LoveUserDO user = new LoveUserDO()
                .setMobile(mobile)
                .setNickname(buildDefaultNickname(mobile))
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        try {
            loveUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册时可能已被抢先插入，改为查询返回
            user = loveUserMapper.selectByMobile(mobile);
            if (user == null) {
                throw exception(AUTH_MOBILE_EXISTS);
            }
        }
        return user;
    }

    private void validateSmsCode(String mobile, String code) {
        // ⚠️ 兜底万能验证码（测试用）：接入真实短信渠道后，注释掉此 if 即恢复真实校验（D-4 生产检查项）
        if (MASTER_SMS_CODE.equals(code)) {
            log.warn("[validateSmsCode][mobile({}) 命中兜底万能验证码，仅限测试环境，生产环境必须关闭！]", mobile);
            return;
        }
        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                .setMobile(mobile)
                .setCode(code)
                .setScene(SmsSceneEnum.MEMBER_LOGIN.getScene())
                .setUsedIp(getClientIP()));
    }

    private void validateMobileNotExists(String mobile) {
        if (loveUserMapper.selectByMobile(mobile) != null) {
            throw exception(AUTH_MOBILE_EXISTS);
        }
    }

    private void validateUserStatus(LoveUserDO user, String mobile, LoginLogTypeEnum logType) {
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), mobile, logType, LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

    private AppAuthLoginRespVO createTokenAfterLoginSuccess(Long userId, String mobile, LoginLogTypeEnum logType) {
        // 1. 记录登录日志（成功时同时更新 last_login_ip / last_login_time）
        createLoginLog(userId, mobile, logType, LoginResultEnum.SUCCESS);
        // 2. 创建访问令牌
        OAuth2AccessTokenRespDTO accessTokenDO = oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(userId)
                .setUserType(UserTypeEnum.MEMBER.getValue())
                .setClientId(CLIENT_ID_COUPLE_APP));
        // 3. 构建返回结果
        return buildLoginRespVO(accessTokenDO);
    }

    private void createLoginLog(Long userId, String username,
                                LoginLogTypeEnum logTypeEnum, LoginResultEnum loginResult) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logTypeEnum.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(UserTypeEnum.MEMBER.getValue());
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogApi.createLoginLog(reqDTO);
        // 登录成功时，更新最后登录 IP 与时间（对齐 AdminAuthServiceImpl 的 updateUserLogin 惯例）
        if (userId != null && Objects.equals(LoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            loveUserMapper.updateById(new LoveUserDO().setId(userId)
                    .setLastLoginIp(ServletUtils.getClientIP())
                    .setLastLoginTime(LocalDateTime.now()));
        }
    }

    private void createLogoutLog(Long userId, Integer userType) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(LoginLogTypeEnum.LOGOUT_SELF.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(userType);
        reqDTO.setUsername(getUserMobile(userId));
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogApi.createLoginLog(reqDTO);
    }

    private String getUserMobile(Long userId) {
        if (userId == null) {
            return null;
        }
        LoveUserDO user = loveUserMapper.selectById(userId);
        return user != null ? user.getMobile() : null;
    }

    private AppAuthLoginRespVO buildLoginRespVO(OAuth2AccessTokenRespDTO accessTokenDO) {
        AppAuthLoginRespVO respVO = BeanUtils.toBean(accessTokenDO, AppAuthLoginRespVO.class);
        respVO.setClientId(CLIENT_ID_COUPLE_APP);
        return respVO;
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String buildDefaultNickname(String mobile) {
        // 默认昵称 = 用户 + 手机号后四位
        return "用户" + mobile.substring(mobile.length() - 4);
    }

}
