package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionRespVO;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionSaveReqVO;

/**
 * 每日一问题库 Service 接口（admin 通道，M32）
 *
 * @author 芋道源码
 */
public interface LoveAdminDailyQuestionService {

    /**
     * 获得每日一问题目分页
     */
    PageResult<LoveDailyQuestionRespVO> getDailyQuestionPage(LoveDailyQuestionPageReqVO pageReqVO);

    /**
     * 新增题目，返回带自增 id 的 VO
     */
    LoveDailyQuestionRespVO createDailyQuestion(LoveDailyQuestionSaveReqVO reqVO);

    /**
     * 更新题目（全字段保存式，null 字段保持原值）
     */
    void updateDailyQuestion(LoveDailyQuestionSaveReqVO reqVO);

    /**
     * 删除题目（逻辑删除）
     */
    void deleteDailyQuestion(Long id);

}
