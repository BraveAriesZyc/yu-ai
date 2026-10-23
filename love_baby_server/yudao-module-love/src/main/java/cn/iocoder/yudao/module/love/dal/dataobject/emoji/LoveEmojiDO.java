package cn.iocoder.yudao.module.love.dal.dataobject.emoji;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天表情包字典表（love_emoji，M25）
 *
 * 用法（字典服务端化，M25 段）：
 * 1. 全局共享的运营内容：无 couple_id / user_id 归属，所有用户共读同一池（B 端配置对象），
 *    App 侧只读单端点（GET /love/emoji/list），零错误路径（池空端上走内置兜底）；
 * 2. category 由 App 端动态聚合 tab（建议：爱意/情绪/手势/庆祝，可自由新增，后台加分类端上零改动）；
 * 3. emoji 点选直插输入框走 TEXT 链（emoji 字面量随消息全文落库快照），服务端不校验表情内容。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），love_inspiration 同路线；
 * 无 uk（emoji 内容无唯一性约束，重复靠运营自律）；idx_category 覆盖 App 聚合与 admin 筛选。
 *
 * @author 芋道源码
 */
@TableName("love_emoji")
@KeySequence("love_emoji_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_emoji 的 tenant_id 过滤（LoveInspirationDO 同款）
public class LoveEmojiDO extends BaseDO {

    /**
     * 表情编号
     */
    @TableId
    private Long id;
    /**
     * 分类
     *
     * App 端动态聚合 tab；空串归端上「常用」桶
     */
    private String category;
    /**
     * emoji 字符（App 点选直插输入框，随消息全文落库快照）
     */
    private String emoji;
    /**
     * 显示顺序（同分类内 sort asc, id asc，越小越靠前）
     */
    private Integer sort;
    /**
     * 状态
     *
     * 0 启用 / 1 停用（CommonStatusEnum）；停用行 App 不可见
     */
    private Integer status;

}
