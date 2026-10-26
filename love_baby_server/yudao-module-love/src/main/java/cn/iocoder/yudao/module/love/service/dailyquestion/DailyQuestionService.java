package cn.iocoder.yudao.module.love.service.dailyquestion;

import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionAnswerReqVO;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionItemRespVO;
import cn.iocoder.yudao.module.love.controller.app.dailyquestion.vo.AppDailyQuestionTodayRespVO;

import java.util.List;

/**
 * 每日一问 Service 接口（app 通道，M32）
 *
 * @author 芋道源码
 */
public interface DailyQuestionService {

    /**
     * 获得今日题与双方作答状态（当日题懒生成；题库空返回全 null VO，端上显「今日无题」）
     */
    AppDailyQuestionTodayRespVO getTodayQuestion();

    /**
     * 提交/修改今天的答案（揭晓前可覆盖写；已揭晓抛 1_003_016_001），返回 latest 今日题态
     */
    AppDailyQuestionTodayRespVO answerQuestion(AppDailyQuestionAnswerReqVO reqVO);

    /**
     * 获得当前 couple 指定月份的每日一问历史（question_date asc，含没答的天）
     */
    List<AppDailyQuestionItemRespVO> getDailyQuestionList(String month);

}
