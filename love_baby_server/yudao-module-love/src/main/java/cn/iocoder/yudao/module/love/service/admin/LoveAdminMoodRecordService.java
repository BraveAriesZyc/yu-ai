package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordRespVO;

/**
 * 心情记录的 admin Service 接口（M31）
 *
 * @author 芋道源码
 */
public interface LoveAdminMoodRecordService {

    /**
     * 获得心情记录分页
     *
     * @param pageReqVO 分页请求（coupleId/userId 精确 + moodLabel 模糊 + moodDate 日期区间）
     * @return 心情记录分页结果
     */
    PageResult<LoveMoodRecordRespVO> getMoodRecordPage(LoveMoodRecordPageReqVO pageReqVO);

    /**
     * 删除心情记录（逻辑删除）
     *
     * @param id 心情记录编号
     * @return 是否成功
     */
    Boolean deleteMoodRecord(Long id);

}
