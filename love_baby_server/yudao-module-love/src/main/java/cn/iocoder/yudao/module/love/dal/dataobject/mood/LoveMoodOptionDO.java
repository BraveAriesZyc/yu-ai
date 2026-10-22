package cn.iocoder.yudao.module.love.dal.dataobject.mood;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 心情选项表（love_mood_option，情侣私有字典，M3.5）
 *
 * 用法（INTEGRATION-DESIGN.md D-14）：
 * 1. couple 维度私有字典：绑定事务从 love_mood_option_default 播种基础行，双方 app 端增删（写恒 POST）；
 * 2. 不建 (couple_id, label) uk：逻辑删除行占位 uk 是 uk_user_date 已踩过的 DDL 坑，
 *    couple 内重名由 service 查重兜底（并发极端重复可接受，可删可再加）；
 * 3. 解绑不清理（couple_id 留历史值，D-11 同语义）；重绑产生新 couple_id 重新播种。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic）；被删选项的历史月历渲染
 * 走 love_mood_record 快照列（mood_label/emoji/color），与本表解耦（先快照后实体，§12.5.2）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（coupleId→couple_id）；
 * sort 恒 999 = 用户新增（落尾），播种行继承模板 sort，排序 sort asc, id asc。
 *
 * @author 芋道源码
 */
@TableName("love_mood_option")
@KeySequence("love_mood_option_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_mood_option 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveMoodOptionDO extends BaseDO {

    /**
     * 心情选项编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * 私有字典归属；idx_couple 普通索引，列表按此过滤
     */
    private Long coupleId;
    /**
     * 心情名（如 开心）
     */
    private String label;
    /**
     * 表情（utf8mb4 文本，如 😄）
     */
    private String emoji;
    /**
     * 颜色 hex（如 #FFB93C，与心愿消息 color 同款约定）
     */
    private String color;
    /**
     * 显示顺序
     *
     * 播种行继承模板值；用户新增恒 999 落尾（service 写死），排序 sort asc, id asc
     */
    private Integer sort;

}
