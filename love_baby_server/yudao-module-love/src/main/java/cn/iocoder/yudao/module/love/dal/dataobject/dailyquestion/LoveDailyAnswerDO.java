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
 * 每日一问作答表（love_daily_answer，M32）
 *
 * 用法（D-21 落位裁决）：
 * 1. 一人一题一条：uk_user_question(user_id, question_id) 保证同一人对同一道题至多一条作答
 *    （照 love_checkin 的 uk_user_date 先例）；answer_date 为冗余列恒 = 当天（只能答当天，
 *    无补答），供月列表走 idx_couple_date(couple_id, answer_date) 直查；
 * 2. 揭晓锁：双方都提交后（同 question_id 下 couple 内两条作答齐）答案互相可见并【锁定】，
 *    再写抛 DAILY_ANSWER_LOCKED（1_003_016_001）；揭晓前自己的答案可覆盖写（mood record 口径）；
 * 3. 归属 = couple 共享：历史列表按 couple_id 过滤返回双方作答；解绑重绑场景覆盖写顺带刷新
 *    couple_id（CheckinServiceImpl#refreshCoupleIdIfNeeded 同款口径，防「答了但不显示」）；
 * 4. 无删除路径：答案不提供删除/撤销（M32 边界），故 uk 与逻辑删除无冲突遗留。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic）；
 * answer_date 对应 MySQL DATE，MyBatis Plus 自动映射 LocalDate（love_checkin.checkin_date 同款）。
 *
 * @author 芋道源码
 */
@TableName("love_daily_answer")
@KeySequence("love_daily_answer_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_daily_answer 的 tenant_id 过滤（LoveCheckinDO 同款）
public class LoveDailyAnswerDO extends BaseDO {

    /**
     * 作答编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * couple 共享，历史列表按此过滤；idx_couple_date 普通索引；解绑重绑后覆盖写会刷新此值
     */
    private Long coupleId;
    /**
     * 作答用户编号
     *
     * uk_user_question(user_id, question_id) 一人一题一条
     */
    private Long userId;
    /**
     * 当日题编号（love_daily_question_day.id）
     */
    private Long questionId;
    /**
     * 作答日期
     *
     * 冗余列恒 = question_date（只能答当天，无补答）；供月列表 idx_couple_date 直查
     */
    private LocalDate answerDate;
    /**
     * 答案内容
     */
    private String content;

}
