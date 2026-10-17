package cn.iocoder.yudao.module.love.dal.dataobject.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * C 端用户表（love_user）
 *
 * 注意：uk_mobile 唯一键与逻辑删除冲突，管理后台删除用户时将 mobile 改写为
 * concat(mobile, '#', id) 释放唯一键（yudao 惯例手法，见 INTEGRATION-DESIGN.md m-9）
 *
 * @author 芋道源码
 */
@TableName("love_user")
@KeySequence("love_user_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_user 的 tenant_id 过滤（正常情况下 application.yaml 已显式关闭租户）
public class LoveUserDO extends BaseDO {

    /**
     * 用户编号
     */
    @TableId
    private Long id;
    /**
     * 手机号
     *
     * uk_mobile 唯一
     */
    private String mobile;
    /**
     * 密码，BCrypt 加密
     *
     * 纯短信注册的用户为空，此时不允许密码登录
     */
    private String password;
    /**
     * 用户昵称，默认「用户 + 手机号后四位」
     */
    private String nickname;
    /**
     * 用户头像
     */
    private String avatar;
    /**
     * 帐号状态
     *
     * 枚举 {@link CommonStatusEnum}：0 启用 / 1 停用；停用时登录被拒、业务接口返回 1_003_005_002
     */
    private Integer status;
    /**
     * 我的邀请码
     */
    private String inviteCode;
    /**
     * 邀请码过期时间
     *
     * 惰性校验（校验时判断是否过期），无定时任务
     */
    private LocalDateTime inviteExpireTime;
    /**
     * 最后登录 IP
     */
    private String lastLoginIp;
    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
    /**
     * 聊天已读游标（M11 未读红点）
     *
     * 已读到的最大 love_message.id（0 = 未读过）；全局消息 id 只增不减，
     * 解绑重绑新 couple 的消息 id 必大于旧游标，语义自然（D-11 口径）
     */
    private Long chatReadCursor;

}
