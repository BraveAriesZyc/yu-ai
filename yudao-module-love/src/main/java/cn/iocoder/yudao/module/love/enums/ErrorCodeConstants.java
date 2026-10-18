package cn.iocoder.yudao.module.love.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Love 错误码枚举类
 *
 * love 模块独立使用 1-003-000-000 段（infra = 1-001，system = 1-002），子段划分：
 * 待办 1-003-001-xxx；心情选项 1-003-002-xxx（M3.5 激活，record 覆盖写仍不打错）；
 * 消息 1-003-003-xxx；情侣 1-003-004-xxx；账号/鉴权 1-003-005-xxx；纪念日 1-003-006-xxx（M5）；
 * 打卡 1-003-007-xxx（M8）。
 * 相册瞬间 1-003-008-xxx（M9）。
 * 恋爱清单 1-003-009-xxx（M14）。
 * 用户资料 1-003-010-xxx（M16）。
 * 恋爱灵感 1-003-011-xxx（M17，B 端配置 App 只读）。
 *
 * 短信验证码本身的错误（未发送、已过期、不正确等）由 system 的 SmsCodeApi 抛其自有错误码，不在本段定义。
 */
public interface ErrorCodeConstants {

    // ========== 待办 模块 1-003-001-000（M3 追加） ==========
    /**
     * 错误码次序恒定（§12.5 演进原则：只增不删不改义），后续 todo 组端点如需新码从此顺延
     */
    ErrorCode TODO_NOT_EXISTS = new ErrorCode(1_003_001_000, "待办不存在");
    ErrorCode TODO_NO_PERMISSION = new ErrorCode(1_003_001_001, "无权操作该待办");

    // ========== 心情选项 模块 1-003-002-xxx（M3.5 激活，D-14） ==========
    // record 仍无独立错误码（uk_user_date 冲突走覆盖写不打错，§6.4）；
    // M3.5 心情选项字典（couple 私有）接入后从预留号段起分配，次序恒定只增不删（§12.5）
    ErrorCode MOOD_OPTION_NOT_EXISTS = new ErrorCode(1_003_002_000, "心情选项不存在");
    ErrorCode MOOD_OPTION_NO_PERMISSION = new ErrorCode(1_003_002_001, "无权操作该心情选项");
    ErrorCode MOOD_OPTION_LIMIT_EXCEEDED = new ErrorCode(1_003_002_002, "心情选项已达上限");
    ErrorCode MOOD_OPTION_LABEL_DUPLICATED = new ErrorCode(1_003_002_003, "心情名称已存在");
    /**
     * admin 通道对默认模板按物理 id 定位（无 app 侧上下文，存在性校验用独立码）；
     * app 侧不需要此码——app 全部以当前 couple 反查，行不存在统一按 1_003_002_000 处理
     */
    ErrorCode MOOD_OPTION_DEFAULT_NOT_EXISTS = new ErrorCode(1_003_002_004, "心情默认选项不存在");

    // ========== 消息 模块 1-003-003-xxx（M3 追加，M12 起两码） ==========
    /**
     * admin 按物理 id 删除时记录不存在，以及跨 couple / 非本人按 id 访问统一按
     * 「不存在」拒绝（不暴露他 couple 消息的存在性，IDOR 用例见 §11；M12 recall 校验链同口径）
     */
    ErrorCode MESSAGE_NOT_EXISTS = new ErrorCode(1_003_003_000, "消息不存在");
    /**
     * 撤回时间窗已过（M12）：发送方超 2 分钟撤回。服务端权威校验（服务端时钟），
     * 端上 createTime 预判仅省一次必败请求，两处窗口常数须同步 120s
     */
    ErrorCode MESSAGE_RECALL_EXPIRED = new ErrorCode(1_003_003_001, "消息发送已超过 2 分钟，无法撤回");

    // ========== 情侣 模块 1-003-004-000 ==========
    ErrorCode COUPLE_INVITE_CODE_INVALID = new ErrorCode(1_003_004_000, "邀请码无效");
    ErrorCode COUPLE_EXISTS = new ErrorCode(1_003_004_001, "你已存在有效的情侣关系");
    ErrorCode COUPLE_SELF_BIND = new ErrorCode(1_003_004_002, "不能与自己绑定");
    ErrorCode COUPLE_NOT_EXISTS = new ErrorCode(1_003_004_003, "尚未绑定情侣关系");
    ErrorCode COUPLE_INVITE_CODE_EXPIRED = new ErrorCode(1_003_004_004, "邀请码已过期");

    // ========== 账号/鉴权 模块 1-003-005-000 ==========
    ErrorCode AUTH_MOBILE_EXISTS = new ErrorCode(1_003_005_000, "手机号已注册");
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = new ErrorCode(1_003_005_001, "账号或密码错误");
    ErrorCode AUTH_LOGIN_USER_DISABLED = new ErrorCode(1_003_005_002, "账号已停用");
    ErrorCode AUTH_MOBILE_NOT_EXISTS = new ErrorCode(1_003_005_003, "手机号不存在");

    // ========== 账号/鉴权 模块 1-003-005-004（M3 admin 追加） ==========
    /**
     * admin 更新/删除 C 端用户时校验存在性（§5.3 PUT /love/user/update、DELETE /love/user/delete）；
     * app 侧不需要此码——app 全部以当前登录人为出发点，行不存在统一按 {@link #AUTH_LOGIN_USER_DISABLED} 处理
     */
    ErrorCode AUTH_USER_NOT_EXISTS = new ErrorCode(1_003_005_004, "用户不存在");

    // ========== 纪念日 模块 1-003-006-xxx（M5 追加） ==========
    /**
     * 错误码次序恒定（§12.5 演进原则：只增不删不改义）
     */
    ErrorCode ANNIVERSARY_NOT_EXISTS = new ErrorCode(1_003_006_000, "纪念日不存在");
    ErrorCode ANNIVERSARY_NO_PERMISSION = new ErrorCode(1_003_006_001, "无权操作该纪念日");
    /**
     * annivDate 入参为 String + @Pattern 挡格式，此处挡「正则通过但日历非法」（如 2026-02-30）
     */
    ErrorCode ANNIVERSARY_DATE_INVALID = new ErrorCode(1_003_006_002, "日期格式非法");

    // ========== 每日打卡 模块 1-003-007-xxx（M8 追加） ==========
    /**
     * 错误码次序恒定（§12.5 演进原则：只增不删不改义）。check 无独立错误码——
     * uk_user_date 冲突走幂等返回不打错（MoodServiceImpl 覆盖写先例），未绑定复用
     * {@link #COUPLE_NOT_EXISTS}（1_003_004_003）；本码仅供 admin 按物理 id 删除时判空
     * （LoveAdminAnniversaryServiceImpl 同款口径），app 侧无按 id 写端点用不到它
     */
    ErrorCode CHECKIN_NOT_EXISTS = new ErrorCode(1_003_007_000, "打卡记录不存在");

    // ========== 相册瞬间 模块 1-003-008-xxx（M9 追加） ==========
    /**
     * 删除瞬间按物理 id 判空（app 侧 + admin 侧共用）：app delete 先 selectById 判空再校验归属
     */
    ErrorCode MOMENT_NOT_EXISTS = new ErrorCode(1_003_008_000, "瞬间不存在");
    /**
     * M9 冻结决策：删除权限 = 仅上传者本人（用户维度校验，非 couple 维度）——
     * 伴侣不能删对方的瞬间（防误删），admin 兜底可删任何（LoveAdminMomentServiceImpl 不校验此码）
     */
    ErrorCode MOMENT_DELETE_FORBIDDEN = new ErrorCode(1_003_008_001, "只能删除自己发布的瞬间");

    // ========== 恋爱清单 模块 1-003-009-xxx（M14 追加） ==========
    /**
     * 错误码次序恒定（§12.5 演进原则：只增不删不改义）。app done/delete 与 admin delete
     * 三处共用：不存在 / 跨 couple 统一按此码拒绝（防探测，MESSAGE_NOT_EXISTS 口径——
     * couple 内双方本就经 list 互见全部心愿，统一码只对跨 couple 有信息屏蔽意义）
     */
    ErrorCode WISH_NOT_EXISTS = new ErrorCode(1_003_009_000, "心愿不存在");
    /**
     * M14 冻结决策（D-19e）：删除权限 = 仅创建者本人（用户维度校验，照 moment 口径）——
     * 伴侣不能删对方添加的心愿（防误删），admin 兜底可删任何（LoveAdminWishServiceImpl 不校验此码）
     */
    ErrorCode WISH_DELETE_FORBIDDEN = new ErrorCode(1_003_009_001, "只能删除自己创建的心愿");

    // ========== 用户资料（1_003_010）==========

    ErrorCode USER_PROFILE_BIRTHDAY_INVALID = new ErrorCode(1_003_010_000, "生日格式非法");

    /** M18：sex 值域收口，仅 0=女/1=男（0=女 1=男 NULL=未设置，与系统字典语义相反） */
    ErrorCode USER_PROFILE_SEX_INVALID = new ErrorCode(1_003_010_001, "性别取值非法");

    // ========== 恋爱灵感 模块 1-003-011-xxx（M17 追加） ==========
    /**
     * 错误码次序恒定（§12.5 演进原则：只增不删不改义）。灵感是全局共享的运营内容
     * （无 couple 归属、无按 id 的 app 写端点），app 侧 today / quote-random / list 三端点
     * 只读零错误路径（池空走兜底文案，不打错）；本码仅供 admin 按物理 id 更新/删除时判空
     * （LoveAdminMoodOptionDefaultServiceImpl 同款口径）
     */
    ErrorCode INSPIRATION_NOT_EXISTS = new ErrorCode(1_003_011_000, "灵感不存在");

}
