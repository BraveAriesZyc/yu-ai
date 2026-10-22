package cn.iocoder.yudao.module.love.dal.dataobject.message;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 悄悄话/心愿消息表（love_message）
 *
 * 用法（INTEGRATION-DESIGN.md §4 / §5.2 message 组）：
 * 1. 增量拉取：GET /love/message/list?lastId=&limit=，按 idx_couple_time(couple_id, id) 顺序扫；
 *    空参 lastId 首屏取最近一页（id 倒序取 limit 条后由 service 正序返回）；
 * 2. kind=2 心愿消息为【快照式】：content/wish_key/color/icon 四字段整体入列，
 *    服务端不校验 wish 归属（无 wish 实体，文案字典留端上），两设备还原靠快照字段（§5.2）；
 * 3. 首期无用户侧删除；M12 起发送方 2 分钟内可撤回自己发的消息（撤回 = recalled 墓碑列标记
 *    不删行，见 {@link #recalled}），admin 删除已拉取消息在端上残留（lastId 只增不减，显式接受）；
 * 4. audit_status 首期恒写 {@link #AUDIT_STATUS_APPROVED} 兜底，事前审核二期接入（§12.3）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），与 love_couple 的物理删除路线相反；
 * couple 物理解绑后本表数据保留，receiver_id 快照保证审计归对（D-11）。
 *
 * 功能：kind / audit_status 的值域常量在本类内定义（照 LoveCoupleDO.STATUS_NORMAL 先例）；
 * 字段名与列名 snake_case 严格驼峰对应（wishKey→wish_key、auditStatus→audit_status）。
 *
 * @author 芋道源码
 */
@TableName("love_message")
@KeySequence("love_message_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_message 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveMessageDO extends BaseDO {

    /**
     * 消息类型 - 文本悄悄话
     */
    public static final Integer KIND_TEXT = 1;

    /**
     * 消息类型 - 心愿消息（快照式，见类注释）
     */
    public static final Integer KIND_WISH = 2;

    /**
     * 消息类型 - 图片消息（content 存图片完整 URL，infra 文件服务上传返回）
     */
    public static final Integer KIND_IMAGE = 3;

    /**
     * 消息类型 - 语音消息（M23：content 存 JSON 快照 {"url":<语音URL>,"dur":<秒>}，
     * 服务端组装不校验语义，端上解析还原；录音时长端上 60s 上限、服务端校验放宽至 600）
     */
    public static final Integer KIND_VOICE = 4;

    /**
     * 消息类型 - 视频消息（M23：content 存 JSON 快照 {"url":<视频URL>,"cover":<封面URL>,"dur":<秒>}，
     * cover 可空串（端上灰底兜底）；与语音同为富媒体快照格式）
     */
    public static final Integer KIND_VIDEO = 5;

    /**
     * 消息类型 - 贴纸消息（M23：content 存贴纸 key 明文，≤64 字符；快照式同 kind=2 wishKey——
     * 服务端不校验字典归属，端上按 key 对齐同版本内置字典渲染，未知 key 占位降级）
     */
    public static final Integer KIND_STICKER = 6;

    /**
     * 消息类型 - 自定义卡片消息（M23：content 存 JSON 快照 {"type":<卡片类型>,
     * "title":<标题>,"sub":<副文案>,"icon":<icon name>}；type 首期仅 wishWall（心愿墙入口卡），
     * 后续自定义类型（红包/约会邀请等）复用本 kind 加 type 值，不再新增 kind 常量）
     */
    public static final Integer KIND_CARD = 7;

    /**
     * 审核状态 - 待审
     */
    public static final Integer AUDIT_STATUS_PENDING = 0;

    /**
     * 审核状态 - 通过（首期恒写此值兜底，§12.3）
     */
    public static final Integer AUDIT_STATUS_APPROVED = 1;

    /**
     * 审核状态 - 命中（敏感词拦截，事前审核二期接入后启用）
     */
    public static final Integer AUDIT_STATUS_HIT = 2;

    /**
     * 消息编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * idx_couple_time(couple_id, id) 组成列：增量拉取按 couple_id 过滤 + id 游标顺序扫
     */
    private Long coupleId;
    /**
     * 发送方用户编号
     */
    private Long senderId;
    /**
     * 接收方用户编号
     *
     * 对方快照：couple 物理解绑后审计仍可归对（D-11）
     */
    private Long receiverId;
    /**
     * 消息类型
     *
     * 枚举 {@link #KIND_TEXT} / {@link #KIND_WISH} / {@link #KIND_IMAGE} /
     * {@link #KIND_VOICE} / {@link #KIND_VIDEO} / {@link #KIND_STICKER} / {@link #KIND_CARD}（M23）
     */
    private Integer kind;
    /**
     * 文本内容，或心愿文案快照，或图片完整 URL（kind=3），或语音/视频/卡片 JSON 快照、贴纸 key 明文（kind=4-7，M23）
     *
     * 列 content varchar(512)（M10 500→512，URL 按 ≤512 设计，同 love_moment.image_url 口径；
     * M23 语音/视频/卡片为服务端从 VO 字段组装的紧凑 JSON 快照，组装后 >512 抛 BAD_REQUEST 兜底）
     */
    private String content;
    /**
     * 心愿 key 快照
     *
     * kind=2 时有值；服务端不校验归属（无 wish 实体，文案字典留端上）
     */
    private String wishKey;
    /**
     * 心愿颜色快照
     *
     * hex 色值字符串（如 #FF6B81），kind=2 时有值；Repository 层 ↔ Color（铁律⑥）
     */
    private String color;
    /**
     * 心愿图标快照
     *
     * icon name（如 gift），kind=2 时有值；Repository 层 ↔ AppIconType（铁律⑥）
     */
    private String icon;
    /**
     * 审核状态
     *
     * 枚举 {@link #AUDIT_STATUS_PENDING} / {@link #AUDIT_STATUS_APPROVED} / {@link #AUDIT_STATUS_HIT}；
     * 首期恒写 {@link #AUDIT_STATUS_APPROVED} 兜底（§4 / §12.3）
     */
    private Integer auditStatus;
    /**
     * 是否已撤回（M12 墓碑标记）
     *
     * 撤回不删行：content 原文保留（admin 审计可看原文），与 deleted 逻辑删独立双列；
     * message/list 照常回传（端上按此渲染灰条提示），wish-list 排除
     */
    private Boolean recalled;

}
