package cn.iocoder.yudao.module.love.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 相册瞬间点赞表（love_moment_like，M15）
 *
 * 用法（INTEGRATION-DESIGN.md §5.2 moment 组 / D-20b）：
 * 1. couple 恒 2 人，双方均可点赞任意瞬间（含自己）；点赞数 ∈ {0,1,2}；
 * 2. 点赞 = INSERT IGNORE 走 uk_moment_user 吸收并发重复（幂等，重复点赞 rows=0 不打错）；
 *    取消点赞 = 【物理 DELETE】（LoveMomentLikeMapper 显式 SQL）——uk 与逻辑删除冲突的唯一解
 *    （墓碑行会永久占 uk 位），故本表 deleted 恒 0；
 * 3. 删瞬间不级联清赞（D-20h）：残留行永不被查询（list 只按存活瞬间的 id 批量取），无害。
 *
 * 依赖：继承 BaseDO（deleted 列保留，本表恒 0，惯例一致性优先）；@TenantIgnore 双保险
 * 跳过 tenant_id 过滤（LoveMomentDO 同款）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（momentId→moment_id）。
 *
 * @author 芋道源码
 */
@TableName("love_moment_like")
@KeySequence("love_moment_like_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_moment_like 的 tenant_id 过滤（LoveMomentDO 同款）
public class LoveMomentLikeDO extends BaseDO {

    /**
     * 点赞编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * 归属第二道闸（service 已校验瞬间归属，此处冗余落库）；解绑重绑后留历史值（D-11）
     */
    private Long coupleId;
    /**
     * 瞬间编号（love_moment.id）
     *
     * 不设外键；uk_moment_user(moment_id, user_id) 幂等闸；idx_moment_id 批量计数走此索引
     */
    private Long momentId;
    /**
     * 点赞用户编号
     */
    private Long userId;

}
