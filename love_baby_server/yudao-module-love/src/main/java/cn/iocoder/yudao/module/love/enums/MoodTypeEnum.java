package cn.iocoder.yudao.module.love.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 心情类型枚举（INTEGRATION-DESIGN.md §4 love_mood_record.mood_type / §5.2 mood 组）
 *
 * ⚠ M3.5（D-14）起【废弃】：新记录不再写 mood_type（列置 NULL），心情值域改由
 * couple 私有字典 love_mood_option 定义（绑定从 love_mood_option_default 播种，B 端可配模板）。
 *
 * 保留理由：存量行 mood_type 1-5 的值域文档 + love.sql 回填段的映射定义源；
 * getByValue 供历史数据排查使用，无生产调用方（recordMood 已改走 optionId）。
 *
 * 依赖：无（零引用保留类；删除留待 M4 清理批次，届时与回填段一并裁决）。
 *
 * @author 芋道源码
 */
@Getter
@AllArgsConstructor
public enum MoodTypeEnum {

    HAPPY(1, "开心"),
    CALM(2, "平静"),
    SAD(3, "难过"),
    ANGRY(4, "生气"),
    TIRED(5, "疲惫");

    /**
     * 类型值（落库 love_mood_record.mood_type）
     */
    private final Integer value;
    /**
     * 中文标签（展示用）
     */
    private final String label;

    /**
     * 按值取枚举
     *
     * 兜底语义：null 或未知值一律返回 null，由调用方决定忽略或按参数错误处理，本方法不抛异常
     */
    public static MoodTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.value.equals(value))
                .findFirst()
                .orElse(null);
    }

}
