package cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 每日一问题库表（love_daily_question，M32）
 *
 * 用法（INTEGRATION-DESIGN.md (M32) + D-21 落位裁决）：
 * 1. 全局共享的运营内容池：无 couple_id / user_id 归属，所有情侣同一天同题（与今日情话轮换口径一致）；
 *    admin 全 CRUD（B 端配置对象），App 侧零直接读——题目经 love_daily_question_day 快照行下发；
 * 2. 「今日题」由 love_daily_question_day 懒生成：service 按启用池 id asc 稳定序 + 日期取模轮换取题
 *    （InspirationMapper.selectListQuotesEnabled 同口径），写入 day 行后本题与题库解耦；
 * 3. 题库增删/停用/改题【不影响历史】：day 行持有 content/category 快照（D-19a 快照哲学），
 *    已出过的题永远原样；停用题只影响未来轮换池；
 * 4. status 沿用 yudao CommonStatusEnum 语义（0 启用 / 1 停用）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），love_inspiration 同路线；
 * 无 uk（内容无唯一性约束，重复靠运营自律）；分类为自由文本（不字典化，M32 边界）。
 *
 * @author 芋道源码
 */
@TableName("love_daily_question")
@KeySequence("love_daily_question_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_daily_question 的 tenant_id 过滤（LoveInspirationDO 同款）
public class LoveDailyQuestionDO extends BaseDO {

    /**
     * 题目编号
     */
    @TableId
    private Long id;
    /**
     * 题目内容
     */
    private String content;
    /**
     * 分类
     *
     * 自由文本（情感/日常/回忆/脑洞/未来…），不字典化；App 端原样展示
     */
    private String category;
    /**
     * 状态
     *
     * 0 启用 / 1 停用（CommonStatusEnum）；停用行退出轮换池（已出的 day 快照不受影响）
     */
    private Integer status;

}
