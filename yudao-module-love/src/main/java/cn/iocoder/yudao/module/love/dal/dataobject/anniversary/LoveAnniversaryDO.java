package cn.iocoder.yudao.module.love.dal.dataobject.anniversary;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 纪念日表（love_anniversary，INTEGRATION-DESIGN.md §12.4 预留落位，M5）
 *
 * 用法（INTEGRATION-DESIGN.md §5.2 anniversary 组）：
 * 1. 归属 = couple 共享 + owner 仅记录创建人，列表对 couple 双方可见；
 *    所有按 id 写操作在 service 层强制 couple 归属校验（不属于当前 couple 返回 1_003_006_001，防 IDOR）；
 * 2. app 侧 GET /love/anniversary/list 全量拉取后端上整体覆盖 AnniversaryStore（§7.4），
 *    admin 侧删除的端上传播同样走全量覆盖，天然生效（§5.3）；
 * 3. 倒数算术全在端上（GMT+8 日序，每年重复 2/29 钳当年月末），服务端只存原始日期，不做任何推算；
 * 4. 无 update 端点（删了重加，D-17）；repeat_type 值域 1=每年重复 2=一次性，tinyint 预留月/周扩展。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），与 love_couple 的物理删除路线相反；
 * couple 解绑/重绑后本表数据保留，couple_id 留历史值，重绑产生新 couple_id（D-11）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（coupleId→couple_id、annivDate→anniv_date），
 * anniv_date 对应 MySQL DATE，MyBatis Plus 自动映射 LocalDate（love_mood_record.mood_date 同款）。
 *
 * @author 芋道源码
 */
@TableName("love_anniversary")
@KeySequence("love_anniversary_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_anniversary 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveAnniversaryDO extends BaseDO {

    /**
     * 纪念日编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * couple 共享，列表按此过滤；idx_couple 普通索引
     */
    private Long coupleId;
    /**
     * 创建人用户编号
     *
     * 仅记录归属；双方均可见可操作，不作为权限依据（权限 = couple 归属校验）
     */
    private Long ownerId;
    /**
     * 纪念日名称（恋爱纪念日/TA 的生日/领证日…）
     */
    private String title;
    /**
     * 纪念日日期
     *
     * DATE 列，只存原始日期；每年重复的「下一次发生日」推算在端上（D-17）
     */
    private LocalDate annivDate;
    /**
     * 重复类型
     *
     * 1=每年重复 2=一次性；值域收口在 service create（1_003_006 之外打全局 BAD_REQUEST，
     * 仓内无 @Range 使用先例，MoodServiceImpl 月份校验同款）
     */
    private Integer repeatType;
    /**
     * emoji 图标
     *
     * 端上预设字典选一（客户端字典，铁律⑥），可空（列 NOT NULL DEFAULT ''，空串端上按 "❤" 兜底）
     */
    private String emoji;

}
