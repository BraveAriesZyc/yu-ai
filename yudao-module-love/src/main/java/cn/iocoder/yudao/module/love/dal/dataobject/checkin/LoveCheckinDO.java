package cn.iocoder.yudao.module.love.dal.dataobject.checkin;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 每日打卡表（love_checkin，INTEGRATION-DESIGN.md §12.4 预留落位，M8）
 *
 * 用法（INTEGRATION-DESIGN.md §5.2 checkin 组）：
 * 1. 纯日期点打卡：无文字无图，每天点一下；uk_user_date(user_id, checkin_date) 保证一人一天至多一条
 *    （照 love_mood_record 的 uk_user_date 先例），重复打卡幂等返回不打错；
 * 2. 归属 = couple 共享：列表/月历/汇总均按 couple_id 过滤返回双方记录；
 *    无按 id 写端点（app 侧只能打今天，服务端以服务端当天为准，无补打；撤销仅 admin delete）；
 * 3. 共同连续（streak）等聚合口径在 service 层内存计算（表天然小，一人一天一条），
 *    服务端不落任何「连续天数」冗余列。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），与 love_anniversary 同路线；
 * couple 解绑/重绑后本表数据保留，couple_id 留历史值，重绑后当日再打卡刷新 couple_id
 * （MoodServiceImpl 同日解绑重绑同款口径，防「打了但不显示」）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（coupleId→couple_id、checkinDate→checkin_date），
 * checkin_date 对应 MySQL DATE，MyBatis Plus 自动映射 LocalDate（love_mood_record.mood_date 同款）。
 *
 * @author 芋道源码
 */
@TableName("love_checkin")
@KeySequence("love_checkin_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_checkin 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveCheckinDO extends BaseDO {

    /**
     * 打卡编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * couple 共享，列表/月历/汇总按此过滤；idx_couple_date 普通索引；解绑重绑后当日打卡会刷新此值
     */
    private Long coupleId;
    /**
     * 打卡用户编号
     *
     * uk_user_date(user_id, checkin_date) 一人一天一条；双人各自记录，共同点亮 = 同日双方都有行
     */
    private Long userId;
    /**
     * 打卡日期
     *
     * DATE 列，服务端以服务端当天为准（无补打，check 端点不收日期参数）
     */
    private LocalDate checkinDate;

}
