package cn.iocoder.yudao.module.love.dal.dataobject.todo;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办表（love_todo）
 *
 * 用法（INTEGRATION-DESIGN.md §4 / §5.2 todo 组）：
 * 1. 归属 = couple 共享 + owner 仅记录创建人，列表对 couple 双方可见；
 *    所有按 id 写操作在 service 层强制 couple 归属校验（不属于当前 couple 返回 1_003_001_001，防 IDOR）；
 * 2. app 侧 GET /love/todo/list 全量拉取后端上整体覆盖 TodoStore（§7.4），
 *    admin 侧删除的端上传播同样走全量覆盖，天然生效（§5.3）；
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），与 love_couple 的物理删除路线相反；
 * couple 解绑/重绑后本表数据保留，couple_id 留历史值，重绑产生新 couple_id（D-11）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（coupleId→couple_id、ownerId→owner_id），
 * done 对应 MySQL bit(1)，MyBatis Plus 自动映射 Boolean。
 *
 * @author 芋道源码
 */
@TableName("love_todo")
@KeySequence("love_todo_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_todo 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveTodoDO extends BaseDO {

    /**
     * 待办编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * couple 共享，列表按此过滤；idx_couple 普通索引
     */
    private Long coupleId;
    /**
     * 创建人用户编号
     *
     * 仅记录归属；双方均可见可操作，不作为权限依据（权限 = couple 归属校验）
     */
    private Long ownerId;
    /**
     * 待办标题
     */
    private String title;
    /**
     * 是否完成
     *
     * 列 done bit(1)，含 toggle done 更新（§5.2 todo update）
     */
    private Boolean done;
    /**
     * 标签文本
     *
     * 家庭/恋爱/健康/生活/随手记；客户端按文本渲染（TodoPage.kt:205 实测）
     */
    private String tag;
    /**
     * 标签颜色
     *
     * hex 色值字符串（如 #FF6B81），Repository 层 ↔ Color（对应客户端 tagColor，铁律⑥）
     */
    private String color;

}
