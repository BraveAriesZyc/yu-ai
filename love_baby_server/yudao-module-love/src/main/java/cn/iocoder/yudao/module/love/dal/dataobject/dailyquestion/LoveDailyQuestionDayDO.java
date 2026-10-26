package cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 每日一问当日题表（love_daily_question_day，M32）
 *
 * 用法（D-21 落位裁决核心）：
 * 1. 一天一行：uk_question_date(question_date) 保证每天至多一道出过的题；行由 service 懒生成
 *    （app 侧首个访问者触发 get-or-create，轮换取题见 DailyQuestionServiceImpl#getOrCreateDay），
 *    不做定时任务预生成（本地 Quartz 默认关，且懒生成天然零空转）；
 * 2. content / category 为题目【快照列】：从 love_daily_question 拷贝（D-19a 快照哲学）——
 *    题库后续改题/停用/删除，已出过的天永远原样，历史列表与当日题不受题库变更影响；
 *    question_id 保留血缘（溯源到题库行，快照为主、id 仅审计用）；
 * 3. 无删除路径：day 行一旦生成永不清（admin 不管理 day 行），故 uk 与逻辑删除无冲突遗留
 *    （checkin 的「逻辑删行占 uk 位需 revive」问题在本表不存在）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic）；
 * question_date 对应 MySQL DATE，MyBatis Plus 自动映射 LocalDate（love_checkin.checkin_date 同款）。
 *
 * @author 芋道源码
 */
@TableName("love_daily_question_day")
@KeySequence("love_daily_question_day_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_daily_question_day 的 tenant_id 过滤（LoveCheckinDO 同款）
public class LoveDailyQuestionDayDO extends BaseDO {

    /**
     * 当日题编号
     */
    @TableId
    private Long id;
    /**
     * 题目日期
     *
     * uk_question_date 一天一行；懒生成于当天首个 app 访问
     */
    private LocalDate questionDate;
    /**
     * 题库题目编号（love_daily_question.id）
     *
     * 血缘溯源用；题目内容以本表 content/category 快照为准（题库可变，快照不变）
     */
    private Long questionId;
    /**
     * 题目内容（快照）
     *
     * 生成时从题库拷贝，此后永不更新（D-19a 快照哲学）
     */
    private String content;
    /**
     * 分类（快照）
     */
    private String category;

}
