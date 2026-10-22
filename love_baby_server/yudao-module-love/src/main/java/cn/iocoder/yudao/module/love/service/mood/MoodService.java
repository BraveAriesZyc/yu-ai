package cn.iocoder.yudao.module.love.service.mood;

import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodRecordReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodRespVO;

import java.util.List;

/**
 * 每日心情 Service 接口
 *
 * 端上契约见 INTEGRATION-DESIGN.md §5.2 mood 组：POST /love/mood/record 记当日（uk_user_date
 * 一人一天一条，重复写覆盖）、GET /love/mood/record/list?month= 按 couple 维度返回双方月度记录。
 * mood_type 值域以 {@link cn.iocoder.yudao.module.love.enums.MoodTypeEnum} 为唯一定义源（§6.6 首期零字典）。
 *
 * @author 芋道源码
 */
public interface MoodService {

    /**
     * 记当日心情：同日已有记录则覆盖 moodType，未记录则插入；并发撞 uk_user_date 收敛为覆盖，不打错
     *
     * @param reqVO 心情类型（值域 MoodTypeEnum）
     * @return 恒 true（覆盖写对端上是成功语义，端上按成功处理非错误）
     */
    Boolean recordMood(AppMoodRecordReqVO reqVO);

    /**
     * 按月查询 couple 双方的心情记录（闭区间 [当月 1 日, 当月末日]）
     *
     * @param month 月份 yyyy-MM（如 2026-10）
     * @return 记录列表（按 mood_date 升序）；某日无记录 = 回包无该日条目，端上按空缺渲染
     */
    List<AppMoodRespVO> getMoodRecordList(String month);

}
