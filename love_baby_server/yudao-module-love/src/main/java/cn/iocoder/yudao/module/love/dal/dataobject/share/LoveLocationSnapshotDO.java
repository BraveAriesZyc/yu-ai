package cn.iocoder.yudao.module.love.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 位置点表（love_location_snapshot）
 *
 * 用法（M26 / INTEGRATION-DESIGN.md §12.4 位置共享落位）：
 * 1. append-only 轨迹点：无 uk、无更新通道，插入即落库；坐标统一存 GCJ-02
 *    （高德系，端上 WGS-84→GCJ-02 纠偏后上传，staticmap/regeo 直接可用）；
 * 2. 30 天保留策略：report 时顺带物理清除该用户 30 天前旧点（LocationSnapshotMapper.purgeBefore
 *    手写 @Delete 物理 SQL——BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删）；
 * 3. 双索引：idx_couple_user_time 供轨迹区间扫，idx_user_time 供按用户清理（前者最左前缀
 *    是 couple_id，按 user 扫不动）；
 * 4. 解绑/重绑：历史点 couple_id 留旧值，trail 按 (couple_id, user_id) 过滤天然隐没，
 *    随 30 天窗口物理消亡（D-11）。
 *
 * 依赖：继承 BaseDO（@TableLogic 逻辑删除路线，purgeBefore 是唯一的物理删除例外）。
 *
 * @author 芋道源码
 */
@TableName("love_location_snapshot")
@KeySequence("love_location_snapshot_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过本表的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveLocationSnapshotDO extends BaseDO {

    /**
     * 位置点编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * 重绑后新 couple 起新流，旧流随 30 天窗口消亡
     */
    private Long coupleId;
    /**
     * 上报用户编号
     */
    private Long userId;
    /**
     * 纬度（GCJ-02）
     */
    private Double lat;
    /**
     * 经度（GCJ-02）
     */
    private Double lng;
    /**
     * 定位精度（米，0 = 未知）
     */
    private Integer accuracy;
    /**
     * 定位时刻
     *
     * 端上 fix 时间，非入库时间
     */
    private LocalDateTime reportTime;

}
