package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodRecordMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_RECORD_NOT_EXISTS;

/**
 * 心情记录的 admin Service 实现类（M31）：page + delete 两件套（LoveAdminMomentServiceImpl 同款惯例）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminMoodRecordServiceImpl implements LoveAdminMoodRecordService {

    @Resource
    private MoodRecordMapper moodRecordMapper;

    @Override
    public PageResult<LoveMoodRecordRespVO> getMoodRecordPage(LoveMoodRecordPageReqVO pageReqVO) {
        PageResult<LoveMoodRecordDO> pageResult = moodRecordMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(pageResult, LoveMoodRecordRespVO.class);
    }

    @Override
    public Boolean deleteMoodRecord(Long id) {
        // 判空抛专属码（LoveAdminMomentServiceImpl.java:35-43 惯例）；admin 兜底可删任何人的记录
        LoveMoodRecordDO record = moodRecordMapper.selectById(id);
        if (record == null) {
            throw exception(MOOD_RECORD_NOT_EXISTS);
        }
        moodRecordMapper.deleteById(id);
        return true;
    }

}
