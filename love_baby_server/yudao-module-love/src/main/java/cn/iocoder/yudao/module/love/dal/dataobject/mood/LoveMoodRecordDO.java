package cn.iocoder.yudao.module.love.dal.dataobject.mood;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 每日心情记录表（love_mood_record）
 *
 * 用法（INTEGRATION-DESIGN.md §4 / §5.2 mood 组 / D-14 M3.5）：
 * 1. 一人一天一条：uk_user_date(user_id, mood_date) 唯一；重复记录走覆盖写
 *    （应用层 selectByUserIdAndDate 命中后 update，uk 冲突兜底终审并发，不打错误码）；
 * 2. 月历查询按 couple 维度：GET /love/mood/record/list?month= 返回双方记录（§7.4 MoodPage 切月拉取）；
 * 3. M3.5 起 mood_type 废弃（置 NULL，列允许 NULL），新值 = optionId（couple 私有字典）+ 快照三列
 *    （moodLabel/emoji/color 记录时点快照，端上月历渲染只吃快照，删除选项后历史不变灰，§12.5.2）；
 * 4. M31 起有 admin 管理页（D-13 裁决变更：用户拍板补页）——page + delete 两件套
 *    （LoveMoodRecordController，权限串 love:mood-record:query|delete）；无 create/update
 *    （记录由 App 端覆盖写产生）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），与 love_couple 的物理删除路线相反。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（moodDate→mood_date、moodType→mood_type，
 * 历史教训：DO 字段名必须与列名严格对应，拿不准就数一遍）。
 *
 * @author 芋道源码
 */
@TableName("love_mood_record")
@KeySequence("love_mood_record_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_mood_record 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveMoodRecordDO extends BaseDO {

    /**
     * 心情记录编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * idx_couple_month(couple_id, mood_date) 组成列
     */
    private Long coupleId;
    /**
     * 记录用户编号
     *
     * uk_user_date(user_id, mood_date) 组成列；月历返回时用于端上区分本人/伴侣
     */
    private Long userId;
    /**
     * 心情日期
     *
     * 列 mood_date 为 MySQL date 类型，Java 侧用 LocalDate（无时分秒）
     */
    private LocalDate moodDate;
    /**
     * 心情类型（废弃）
     *
     * 原值域 = {@link cn.iocoder.yudao.module.love.enums.MoodTypeEnum}（1 开心 / 2 平静 / 3 难过 / 4 生气 / 5 疲惫）；
     * M3.5 起新记录恒写 NULL，仅存量行有值（端上按旧 1-5 映射兜底渲染，D-14）
     */
    private Integer moodType;
    /**
     * 心情选项编号
     *
     * love_mood_option.id（couple 私有字典）；记录时点快照，选项删除后保留原值（尽力而为审计链路，
     * 渲染走下方快照三列不回查字典）
     */
    private Long optionId;
    /**
     * 心情名快照（记录时点）
     */
    private String moodLabel;
    /**
     * 表情快照（记录时点，utf8mb4 文本）
     */
    private String emoji;
    /**
     * 颜色 hex 快照（记录时点）
     */
    private String color;

}
