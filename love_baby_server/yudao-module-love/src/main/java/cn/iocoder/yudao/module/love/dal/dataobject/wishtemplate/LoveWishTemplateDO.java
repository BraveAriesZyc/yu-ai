package cn.iocoder.yudao.module.love.dal.dataobject.wishtemplate;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 心愿墙挑选模板表（love_wish_template，M25）
 *
 * 用法（字典服务端化，M25 段）：
 * 1. 全局共享的运营内容：无 couple_id / user_id 归属，所有用户共读同一池（B 端配置对象），
 *    App 侧只读单端点（GET /love/wish-template/list），零错误路径（池空端上走内置兜底）；
 * 2. ⚠ id 即 App 送出 wishKey——kind=2 心愿消息为快照式四字段上送（wishKey/text/color/icon，
 *    服务端不校验字典、气泡渲染走快照不查本表），删/停模板不影响历史消息，双向兼容；
 * 3. color_start/color_end 为卡片渐变双色 hex（原端上 catColors 分类级色拆到条目级，更灵活）；
 *    icon 为端上 AppIconType 枚举 name（未知名端上兜底 Heart）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），love_inspiration 同路线；
 * 无 uk（title 重复靠运营自律）；idx_category 覆盖 App 聚合与 admin 筛选。
 *
 * @author 芋道源码
 */
@TableName("love_wish_template")
@KeySequence("love_wish_template_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_wish_template 的 tenant_id 过滤（LoveInspirationDO 同款）
public class LoveWishTemplateDO extends BaseDO {

    /**
     * 模板编号（= App 送出 wishKey；快照式，历史消息渲染不依赖本表）
     */
    @TableId
    private Long id;
    /**
     * 分类
     *
     * App 端动态聚合 chips（建议：想亲亲/想抱抱/想吃点/想念/撒个娇/和好，可自由新增）
     */
    private String category;
    /**
     * 标题（气泡主文案 + 卡片首行，随消息快照上送）
     */
    private String title;
    /**
     * 副标题（卡片第二行，仅挑选列表展示，不随消息上送）
     */
    private String subtitle;
    /**
     * 卡片渐变起始色 hex #RRGGBB（sendWishes 上送的主色）
     */
    private String colorStart;
    /**
     * 卡片渐变结束色 hex #RRGGBB
     */
    private String colorEnd;
    /**
     * 图标（端上 AppIconType 枚举 name；未知名端上兜底 Heart）
     */
    private String icon;
    /**
     * 显示顺序（sort asc, id asc，越小越靠前）
     */
    private Integer sort;
    /**
     * 状态
     *
     * 0 启用 / 1 停用（CommonStatusEnum）；停用行 App 不可见
     */
    private Integer status;

}
