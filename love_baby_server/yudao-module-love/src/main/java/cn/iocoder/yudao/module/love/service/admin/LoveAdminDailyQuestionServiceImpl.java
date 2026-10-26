package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionRespVO;
import cn.iocoder.yudao.module.love.controller.admin.dailyquestion.vo.LoveDailyQuestionSaveReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyQuestionDO;
import cn.iocoder.yudao.module.love.dal.mysql.dailyquestion.DailyQuestionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.DAILY_QUESTION_NOT_EXISTS;

/**
 * 每日一问题库 Service 实现类（admin 通道，M32）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在（1_003_016_000），
 * LoveAdminInspirationServiceImpl 同款口径；题目即时进/退轮换池（App 只读经 day 快照，
 * 无播种/缓存层）。改题/删题不影响历史（day 快照行原样，D-19a/D-21）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminDailyQuestionServiceImpl implements LoveAdminDailyQuestionService {

    @Resource
    private DailyQuestionMapper dailyQuestionMapper;

    @Override
    public PageResult<LoveDailyQuestionRespVO> getDailyQuestionPage(LoveDailyQuestionPageReqVO pageReqVO) {
        PageResult<LoveDailyQuestionDO> pageResult = dailyQuestionMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveDailyQuestionDO>()
                        .likeIfPresent(LoveDailyQuestionDO::getContent, pageReqVO.getContent())
                        .likeIfPresent(LoveDailyQuestionDO::getCategory, pageReqVO.getCategory())
                        .eqIfPresent(LoveDailyQuestionDO::getStatus, pageReqVO.getStatus())
                        .betweenIfPresent(LoveDailyQuestionDO::getCreateTime, pageReqVO.getCreateTime())
                        // @TableLogic 自动附加 deleted = 0，已删题目不出现在分页；id desc 新添加在前
                        .orderByDesc(LoveDailyQuestionDO::getId));
        return BeanUtils.toBean(pageResult, LoveDailyQuestionRespVO.class);
    }

    @Override
    public LoveDailyQuestionRespVO createDailyQuestion(LoveDailyQuestionSaveReqVO reqVO) {
        // status 未传走列默认 0 启用（MP 插入忽略 null 字段）；id 忽略（create 语义，防端上误传）
        LoveDailyQuestionDO entity = BeanUtils.toBean(reqVO, LoveDailyQuestionDO.class).setId(null);
        dailyQuestionMapper.insert(entity);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(entity, LoveDailyQuestionRespVO.class);
    }

    @Override
    public void updateDailyQuestion(LoveDailyQuestionSaveReqVO reqVO) {
        // 校验存在（admin 按物理 id 定位）
        validateExists(reqVO.getId());
        // 全字段保存式：null 字段保持原值（MP updateById 忽略 null；B 端表单恒全量提交）
        dailyQuestionMapper.updateById(BeanUtils.toBean(reqVO, LoveDailyQuestionDO.class));
    }

    @Override
    public void deleteDailyQuestion(Long id) {
        // 校验存在
        validateExists(id);
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）；已出过的天走 day 快照原样不变
        dailyQuestionMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (dailyQuestionMapper.selectById(id) == null) {
            throw exception(DAILY_QUESTION_NOT_EXISTS);
        }
    }

}
