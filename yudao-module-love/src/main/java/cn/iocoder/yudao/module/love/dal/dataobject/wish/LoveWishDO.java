package cn.iocoder.yudao.module.love.dal.dataobject.wish;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 恋爱清单表（love_wish）
 *
 * 用法（INTEGRATION-DESIGN.md §4 / §5.2 wish 组，M14）：
 * 1. 归属 = couple 共享 + userId 仅记录创建人，列表对 couple 双方可见；
 *    所有按 id 写操作在 service 层强制 couple 归属校验（不存在 / 跨 couple 统一 1_003_009_000，防 IDOR），
 *    删除另校验 userId = 当前用户（仅创建者可删，D-19e）；
 * 2. app 侧 GET /love/wish/list 全量拉取后端上整体覆盖（照 todo 全量覆盖先例，§7.4），
 *    admin 侧删除的端上传播同样走全量覆盖，天然生效（§5.3）；
 * 3. 与 love_message kind=2 心愿卡消息【并存不替代】（D-19a，§12.4 落位兑现）：
 *    kind=2 是快照式聊天消息，本表是可勾选完成的共享清单实体；wish_key 不升级外键（快照哲学）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic）；
 * couple 解绑/重绑后本表数据保留，couple_id 留历史值，重绑产生新 couple_id（D-11）。
 *
 * 功能：无 uk（同一 couple 双方均可添加多条，moment 先例）；
 * done 对应 MySQL bit(1)，MyBatis Plus 自动映射 Boolean；
 * done_by / done_time 可空（未完成时为 NULL，undo 时注解 SQL 显式置 NULL——updateById 忽略 null 字段置不上）；
 * 无 celebrated 类防重发列：完成消息的「恰发一条」由 markDone 的 CAS（WHERE done = b'0'）单闸保证（D-19b）。
 *
 * @author 芋道源码
 */
@TableName("love_wish")
@KeySequence("love_wish_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_wish 的 tenant_id 过滤（LoveTodoDO 同款，§10 M-1）
public class LoveWishDO extends BaseDO {

    /**
     * 心愿编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * couple 共享，列表按此过滤；idx_couple_id 普通索引
     */
    private Long coupleId;
    /**
     * 创建人用户编号
     *
     * 删除权限依据（仅创建者可删，D-19e）；完成/勾选不限创建者（双方均可）
     */
    private Long userId;
    /**
     * 心愿标题
     */
    private String title;
    /**
     * 是否完成
     *
     * 列 done bit(1)；状态迁移走 WishMapper 注解 SQL（CAS + NULL 置位），不走 updateById
     */
    private Boolean done;
    /**
     * 完成人用户编号（勾选者；未完成为 NULL）
     */
    private Long doneBy;
    /**
     * 完成时间（未完成为 NULL）
     */
    private LocalDateTime doneTime;

}
