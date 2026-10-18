package cn.iocoder.yudao.module.love.dal.dataobject.config;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务配置表（love_config，M23）
 *
 * 用法（love 业务专属 KV 配置，B 端配置 App 只读）：
 * 1. 全局共享的运营配置：无 couple_id / user_id 归属，所有用户共读（B 端配置对象），
 *    App 侧只读单端点（GET /love/config/list 回 configKey→configValue 映射，仅启用行）；
 *    首个消费方 = 我的页「帮助与客服」行复制的客服微信号（customer_service_wechat，
 *    端上带内置兜底值，配置缺失/停用/拉取失败均不拦 UI）——后续新配置项直接加行即可。
 * 2. config_key 是 App 读取契约（小写字母数字下划线，@Pattern 校验）；name 仅供管理端展示；
 *    status 沿用 CommonStatusEnum（0 启用 / 1 停用），停用行不下发 App（端上走内置兜底）。
 * 3. 无 DB 唯一约束：逻辑删除行残留 + uk 会挡同键重建，唯一性由 service 层
 *    selectByKey（@TableLogic 自动过滤已删行）校验兜口（yudao infra_config 同哲学）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），love_inspiration 同路线；
 * idx_config_key 普通索引（App 全量拉取 + admin 键筛选）。
 *
 * @author 芋道源码
 */
@TableName("love_config")
@KeySequence("love_config_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_config 的 tenant_id 过滤（LoveWishDO 同款）
public class LoveConfigDO extends BaseDO {

    /**
     * 配置编号
     */
    @TableId
    private Long id;
    /**
     * 配置名称（管理端展示用，如「客服微信号」）
     */
    private String name;
    /**
     * 配置键（App 读取契约：小写字母开头 + 小写字母数字下划线，最长 64）
     *
     * 例如：customer_service_wechat（客服微信号，App 我的页复制引导）
     */
    private String configKey;
    /**
     * 配置值（App 按键读取；500 内足够联系方式/开关量等运营配置，超长文案走独立业务建模）
     */
    private String configValue;
    /**
     * 备注（说明该配置的用途与消费位置，提醒运营 config_key 是 App 契约勿随意改动）
     */
    private String remark;
    /**
     * 状态
     *
     * 0 启用 / 1 停用（CommonStatusEnum）；停用行不下发 App（端上走内置兜底值）
     */
    private Integer status;

}
