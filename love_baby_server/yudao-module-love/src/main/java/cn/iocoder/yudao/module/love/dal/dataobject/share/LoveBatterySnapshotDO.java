package cn.iocoder.yudao.module.love.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电量快照表（love_battery_snapshot）
 *
 * 用法（M26 / INTEGRATION-DESIGN.md §12.4-2 扩展）：
 * 1. 每用户一行 upsert 快照：uk_user_id 一人一行，report = select→null?insert:updateById，
 *    永不删行（无 delete 通道，逻辑删除列仅作 BaseDO 惯例保留）；
 * 2. update_time 即上报时间（表无独立 report_time 列，AppBatteryPartnerVO 暴露为 reportTime）；
 * 3. 解绑/重绑后行保留，couple_id 在下次上报时刷新到当前 couple（checkin「当日再打卡刷新」同口径，D-11）。
 *
 * 依赖：继承 BaseDO（@TableLogic 逻辑删除路线）；uk 冲突由 service 捕获 DuplicateKey 重试 update。
 *
 * @author 芋道源码
 */
@TableName("love_battery_snapshot")
@KeySequence("love_battery_snapshot_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过本表的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveBatterySnapshotDO extends BaseDO {

    /**
     * 快照编号
     */
    @TableId
    private Long id;
    /**
     * 用户编号
     *
     * uk_user_id 一人一行（upsert 语义）
     */
    private Long userId;
    /**
     * 情侣关系编号
     *
     * 重绑后上报刷新到当前 couple
     */
    private Long coupleId;
    /**
     * 电量百分比 0-100
     *
     * -1 = 未知（端上读取失败兜底）
     */
    private Integer batteryLevel;
    /**
     * 充电状态
     *
     * 0 = 未充电、1 = 充电中、2 = 已充满（值域收口在 service report）
     */
    private Integer chargingStatus;

}
