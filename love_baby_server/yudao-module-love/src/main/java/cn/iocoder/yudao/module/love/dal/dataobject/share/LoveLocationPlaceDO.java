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
 * 行程地点表（love_location_place）
 *
 * 用法（M28 / 行动轨迹地点记录）：
 * 1. 地点粒度的 append-only 记录：无 uk、无更新通道，插入即落库；由 location/report
 *    顺路落库——距本人最新 place ≥30min 或位移 ≥300m 任一成立才插（recordPlaceIfDue，
 *    服务端时钟与库内数据判定，防多端/绕过客户端）；
 * 2. 坐标统一存 GCJ-02（随 love_location_snapshot 同源上报）；省/市/区/地名/POI 由端上
 *    regeo 解析随点上报（服务端无高德 key 不做逆地理），解析失败可空，端上降级显坐标；
 * 3. 30 天保留策略：report 时顺带物理清除该用户 30 天前旧行（LocationPlaceMapper.purgeBefore
 *    手写 @Delete 物理 SQL——BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删）；
 * 4. 解绑/重绑：历史行 couple_id 留旧值，列表按 (couple_id, user_id) 过滤天然隐没，
 *    随 30 天窗口物理消亡（D-11，love_location_snapshot 同口径）。
 *
 * 依赖：继承 BaseDO（@TableLogic 逻辑删除路线，purgeBefore 是唯一的物理删除例外）。
 *
 * @author 芋道源码
 */
@TableName("love_location_place")
@KeySequence("love_location_place_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过本表的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveLocationPlaceDO extends BaseDO {

    /**
     * 地点记录编号
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
     * 省（端上 regeo 解析，直辖市可为空）
     */
    private String province;
    /**
     * 市（端上 regeo 解析，直辖市可为空）
     */
    private String city;
    /**
     * 区/县（端上 regeo 解析，可空）
     */
    private String district;
    /**
     * 完整地址（regeo formatted_address，可空）
     */
    private String address;
    /**
     * 地名/POI（regeo pois[0].name，可空）
     */
    private String poi;
    /**
     * 到达时刻
     *
     * 端上 fix 时间，非入库时间
     */
    private LocalDateTime reportTime;

}
