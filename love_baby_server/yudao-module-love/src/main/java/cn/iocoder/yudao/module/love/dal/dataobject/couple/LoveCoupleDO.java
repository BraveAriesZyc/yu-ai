package cn.iocoder.yudao.module.love.dal.dataobject.couple;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 情侣关系表（love_couple）
 *
 * 注意（INTEGRATION-DESIGN.md §4 / D-11）：
 * 1. 本表解绑采用【物理删除】，故不继承 BaseDO：无 @TableLogic、无 deleted 字段，
 *    Mapper 的 deleteById 即物理 DELETE，uk_user_a / uk_user_b 唯一键天然支持解绑后重绑；
 * 2. 关系行不留史：审计归对靠 love_message / love_todo 的冗余快照列，
 *    重绑产生新 couple_id，旧数据不迁移；
 * 3. 公共字段（creator / createTime / updater / updateTime）自带而非继承 BaseDO：
 *    DefaultDBFieldHandler.insertFill 仅处理 BaseDO 实例，对本类不生效，
 *    因此 insert 时由 service 显式赋值（DB 侧 CURRENT_TIMESTAMP 仅作兜底默认值）。
 *
 * @author 芋道源码
 */
@TableName("love_couple")
@KeySequence("love_couple_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_couple 的 tenant_id 过滤（LoveUserDO 同款）
public class LoveCoupleDO implements Serializable {

    /**
     * 关系状态 - 正常
     */
    public static final Integer STATUS_NORMAL = 0;

    private static final long serialVersionUID = 1L;

    /**
     * 关系编号
     */
    @TableId
    private Long id;
    /**
     * 发起方用户编号（邀请码属主）
     *
     * uk_user_a 唯一；字段名对应列 user_a_id（驼峰映射）
     */
    private Long userAId;
    /**
     * 受邀方用户编号
     *
     * uk_user_b 唯一；字段名对应列 user_b_id（驼峰映射）
     */
    private Long userBId;
    /**
     * 关系状态
     *
     * 首期恒为 {@link #STATUS_NORMAL}（解绑即物理删除行，无中间态）
     */
    private Integer status;
    /**
     * 绑定时间
     */
    private LocalDateTime bindTime;
    /**
     * 创建时间
     *
     * 不继承 BaseDO，insertFill 不生效，由 service 显式赋值
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     *
     * 不继承 BaseDO，insertFill 不生效，由 service 显式赋值
     */
    private LocalDateTime updateTime;
    /**
     * 创建者，使用 love_user 的 id 编号
     */
    private String creator;
    /**
     * 更新者，使用 love_user 的 id 编号
     */
    private String updater;

}
