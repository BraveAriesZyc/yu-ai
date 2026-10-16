package cn.iocoder.yudao.module.love.dal.dataobject.mood;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 心情默认选项模板表（love_mood_option_default，B 端管理对象，M3.5）
 *
 * 用法（INTEGRATION-DESIGN.md D-14 / §5.3）：
 * 1. 仅 admin-api 读写（love:mood-option-default:* 权限），app 端不感知本表；
 * 2. 绑定事务内复制 status=启用 行到 love_mood_option（播种即复制，归属情侣）——
 *    admin 后续改模板【不回灌】已绑定情侣（私有边界，显式声明）；
 * 3. status 停用 = 不参与播种（软下架，不删历史）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应；
 * sort 参与播种并被播种行继承（1..5 初始档），admin 可改序。
 *
 * @author 芋道源码
 */
@TableName("love_mood_option_default")
@KeySequence("love_mood_option_default_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_mood_option_default 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveMoodOptionDefaultDO extends BaseDO {

    /**
     * 默认选项编号
     */
    @TableId
    private Long id;
    /**
     * 心情名（如 开心）
     */
    private String label;
    /**
     * 表情（utf8mb4 文本，如 😄）
     */
    private String emoji;
    /**
     * 颜色 hex（如 #FFB93C）
     */
    private String color;
    /**
     * 显示顺序（播种行继承此值；越小越靠前）
     */
    private Integer sort;
    /**
     * 状态
     *
     * {@link cn.iocoder.yudao.framework.common.enums.CommonStatusEnum}：0 启用 / 1 停用；
     * 停用行不参与绑定播种（admin 软下架）
     */
    private Integer status;

}
