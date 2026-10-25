package cn.iocoder.yudao.module.love.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 共享开关表（love_share_setting）
 *
 * 用法（M26 / INTEGRATION-DESIGN.md §12.4-2 隐私定案）：
 * 1. 每用户一行开关：uk_user_id 一人一行，懒创建（无行按默认值返回，首次 update 才落行，
 *    省注册链写入）；
 * 2. 电量默认开（battery_share bit default 1，低敏数据）；位置默认关（location_share bit
 *    default 0，§12.4 强隐私：默认不采、双方显式开关）；
 * 3. 开关关 = 服务端拒收上报 + 对方查询只见 shareEnabled:false，双端同断（service 收口）。
 *
 * 依赖：继承 BaseDO（@TableLogic 逻辑删除路线）；永不删行（无 delete 通道）。
 *
 * @author 芋道源码
 */
@TableName("love_share_setting")
@KeySequence("love_share_setting_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过本表的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveShareSettingDO extends BaseDO {

    /**
     * 设置编号
     */
    @TableId
    private Long id;
    /**
     * 用户编号
     *
     * uk_user_id 一人一行
     */
    private Long userId;
    /**
     * 电量共享开关
     *
     * 默认开（低敏）；bit(1) ↔ Boolean
     */
    private Boolean batteryShare;
    /**
     * 位置共享开关
     *
     * 默认关（强隐私 §12.4）；bit(1) ↔ Boolean
     */
    private Boolean locationShare;

}
