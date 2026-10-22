package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationRespVO;
import cn.iocoder.yudao.module.love.controller.admin.inspiration.vo.LoveInspirationSaveReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.inspiration.LoveInspirationDO;
import cn.iocoder.yudao.module.love.dal.mysql.inspiration.InspirationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.INSPIRATION_NOT_EXISTS;

/**
 * 恋爱灵感 Service 实现类（admin 通道，M17）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在（1_003_011_000），
 * LoveAdminMoodOptionDefaultServiceImpl 同款口径；灵感即时生效（App 只读，无播种/缓存层）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminInspirationServiceImpl implements LoveAdminInspirationService {

    @Resource
    private InspirationMapper inspirationMapper;

    @Override
    public PageResult<LoveInspirationRespVO> getInspirationPage(LoveInspirationPageReqVO pageReqVO) {
        PageResult<LoveInspirationDO> pageResult = inspirationMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveInspirationDO>()
                        .likeIfPresent(LoveInspirationDO::getContent, pageReqVO.getContent())
                        .eqIfPresent(LoveInspirationDO::getKind, pageReqVO.getKind())
                        .eqIfPresent(LoveInspirationDO::getStatus, pageReqVO.getStatus())
                        .betweenIfPresent(LoveInspirationDO::getCreateTime, pageReqVO.getCreateTime())
                        // @TableLogic 自动附加 deleted = 0，已删灵感不出现在分页；id desc 新添加在前
                        .orderByDesc(LoveInspirationDO::getId));
        return BeanUtils.toBean(pageResult, LoveInspirationRespVO.class);
    }

    @Override
    public LoveInspirationRespVO createInspiration(LoveInspirationSaveReqVO reqVO) {
        // status 未传走列默认 0 启用（MP 插入忽略 null 字段）；id 忽略（create 语义，防端上误传）
        LoveInspirationDO entity = BeanUtils.toBean(reqVO, LoveInspirationDO.class).setId(null);
        inspirationMapper.insert(entity);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(entity, LoveInspirationRespVO.class);
    }

    @Override
    public void updateInspiration(LoveInspirationSaveReqVO reqVO) {
        // 校验存在（admin 按物理 id 定位）
        validateExists(reqVO.getId());
        // 全字段保存式：null 字段保持原值（MP updateById 忽略 null；B 端表单恒全量提交）
        inspirationMapper.updateById(BeanUtils.toBean(reqVO, LoveInspirationDO.class));
    }

    @Override
    public void deleteInspiration(Long id) {
        // 校验存在
        validateExists(id);
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）
        inspirationMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (inspirationMapper.selectById(id) == null) {
            throw exception(INSPIRATION_NOT_EXISTS);
        }
    }

}
