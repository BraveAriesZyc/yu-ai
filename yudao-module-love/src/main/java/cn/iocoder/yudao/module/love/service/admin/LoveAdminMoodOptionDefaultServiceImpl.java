package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultRespVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultSaveReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodOptionDefaultDO;
import cn.iocoder.yudao.module.love.dal.mysql.mood.MoodOptionDefaultMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOOD_OPTION_DEFAULT_NOT_EXISTS;

/**
 * 心情默认选项模板 Service 实现类（admin 通道，M3.5 / §5.3）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在（1_003_002_004）；
 * 模板改动只影响之后的新绑定（播种即复制，CoupleServiceImpl.bind 事务内读启用行），不回灌存量 couple。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminMoodOptionDefaultServiceImpl implements LoveAdminMoodOptionDefaultService {

    @Resource
    private MoodOptionDefaultMapper moodOptionDefaultMapper;

    @Override
    public PageResult<LoveMoodOptionDefaultRespVO> getMoodOptionDefaultPage(LoveMoodOptionDefaultPageReqVO pageReqVO) {
        PageResult<LoveMoodOptionDefaultDO> pageResult = moodOptionDefaultMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveMoodOptionDefaultDO>()
                        .likeIfPresent(LoveMoodOptionDefaultDO::getLabel, pageReqVO.getLabel())
                        .eqIfPresent(LoveMoodOptionDefaultDO::getStatus, pageReqVO.getStatus())
                        // @TableLogic 自动附加 deleted = 0，已删模板不出现在分页
                        // 排序 sort asc, id asc：模板的效果序即管理序（播种行按此序复制落库）
                        .orderByAsc(LoveMoodOptionDefaultDO::getSort)
                        .orderByAsc(LoveMoodOptionDefaultDO::getId));
        return BeanUtils.toBean(pageResult, LoveMoodOptionDefaultRespVO.class);
    }

    @Override
    public LoveMoodOptionDefaultRespVO createMoodOptionDefault(LoveMoodOptionDefaultSaveReqVO reqVO) {
        // status 未传走列默认 0 启用（MP 插入忽略 null 字段）；id 忽略（create 语义，防端上误传）
        LoveMoodOptionDefaultDO entity = BeanUtils.toBean(reqVO, LoveMoodOptionDefaultDO.class).setId(null);
        moodOptionDefaultMapper.insert(entity);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(entity, LoveMoodOptionDefaultRespVO.class);
    }

    @Override
    public void updateMoodOptionDefault(LoveMoodOptionDefaultSaveReqVO reqVO) {
        // 校验存在（admin 按物理 id 定位，app 侧同表无此路径）
        validateExists(reqVO.getId());
        // 全字段保存式：null 字段保持原值（MP updateById 忽略 null；B 端表单恒全量提交）
        moodOptionDefaultMapper.updateById(BeanUtils.toBean(reqVO, LoveMoodOptionDefaultDO.class));
    }

    @Override
    public void deleteMoodOptionDefault(Long id) {
        // 校验存在
        validateExists(id);
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）；不影响已绑定 couple（播种即复制，D-14）
        moodOptionDefaultMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (moodOptionDefaultMapper.selectById(id) == null) {
            throw exception(MOOD_OPTION_DEFAULT_NOT_EXISTS);
        }
    }

}
