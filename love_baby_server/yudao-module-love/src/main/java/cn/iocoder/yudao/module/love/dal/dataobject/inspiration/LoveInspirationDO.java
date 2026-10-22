package cn.iocoder.yudao.module.love.dal.dataobject.inspiration;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 恋爱灵感表（love_inspiration，M17）
 *
 * 用法（首页「今日情话」「约会灵感」两卡落位）：
 * 1. 全局共享的运营内容：无 couple_id / user_id 归属，所有情侣共读同一池（B 端配置对象），
 *    App 侧只读零写端点（today / quote-random / list），零错误路径（池空走兜底文案）；
 * 2. kind=1 情话（「今日情话」池，服务端按日轮换 dateKey 取模 + 随机换一句）；
 *    kind=2 约会灵感（「约会灵感」列表池，全量拉取，端上分类筛选 + 换一换打乱）；
 * 3. status 沿用 yudao CommonStatusEnum 语义（0 启用 / 1 停用），停用行两端均不可见。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），mood_option_default 同路线；
 * 无 uk（内容无唯一性约束，重复靠运营自律）；idx_kind 普通索引（两池各自按 kind 查询）。
 *
 * @author 芋道源码
 */
@TableName("love_inspiration")
@KeySequence("love_inspiration_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_inspiration 的 tenant_id 过滤（LoveWishDO 同款）
public class LoveInspirationDO extends BaseDO {

    /**
     * 灵感编号
     */
    @TableId
    private Long id;
    /**
     * 类型
     *
     * 1-情话（今日情话池）；2-约会灵感（约会灵感列表池）
     */
    private Integer kind;
    /**
     * 标题（约会灵感用，情话为 NULL）
     */
    private String title;
    /**
     * 内容（情话正文 / 约会灵感描述）
     */
    private String content;
    /**
     * 分类
     *
     * 情话：甜宠/日常/表白；约会灵感：室内/户外/美食/观影/手作；App 端动态聚合 chips
     */
    private String category;
    /**
     * 状态
     *
     * 0 启用 / 1 停用（CommonStatusEnum）；停用行 App 不可见
     */
    private Integer status;

}
