package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigRespVO;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigSaveReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.config.LoveConfigDO;
import cn.iocoder.yudao.module.love.dal.mysql.config.LoveConfigMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.CONFIG_KEY_DUPLICATE;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.CONFIG_NOT_EXISTS;

/**
 * 业务配置 Service 实现类（admin 通道，M23）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在（1_003_012_000），
 * LoveAdminInspirationServiceImpl 同款口径；config_key 唯一性在 service 层校验
 * （1_003_012_001）——本表无 DB uk（逻辑删除行残留 + uk 会挡同键重建，
 * selectByKey 经 @TableLogic 自动过滤已删行，yudao infra_config 同哲学）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminConfigServiceImpl implements LoveAdminConfigService {

    @Resource
    private LoveConfigMapper loveConfigMapper;

    @Override
    public PageResult<LoveConfigRespVO> getConfigPage(LoveConfigPageReqVO pageReqVO) {
        PageResult<LoveConfigDO> pageResult = loveConfigMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveConfigDO>()
                        .likeIfPresent(LoveConfigDO::getName, pageReqVO.getName())
                        .likeIfPresent(LoveConfigDO::getConfigKey, pageReqVO.getConfigKey())
                        .eqIfPresent(LoveConfigDO::getStatus, pageReqVO.getStatus())
                        .betweenIfPresent(LoveConfigDO::getCreateTime, pageReqVO.getCreateTime())
                        // @TableLogic 自动附加 deleted = 0，已删配置不出现在分页；id desc 新添加在前
                        .orderByDesc(LoveConfigDO::getId));
        return BeanUtils.toBean(pageResult, LoveConfigRespVO.class);
    }

    @Override
    public LoveConfigRespVO createConfig(LoveConfigSaveReqVO reqVO) {
        // config_key 唯一性（服务层唯一闸口，已删同键行不参与判定）
        validateKeyDuplicate(reqVO.getConfigKey(), null);
        // status 未传走列默认 0 启用（MP 插入忽略 null 字段）；id 忽略（create 语义，防端上误传）
        LoveConfigDO entity = BeanUtils.toBean(reqVO, LoveConfigDO.class).setId(null);
        loveConfigMapper.insert(entity);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(entity, LoveConfigRespVO.class);
    }

    @Override
    public void updateConfig(LoveConfigSaveReqVO reqVO) {
        // 校验存在（admin 按物理 id 定位）
        validateExists(reqVO.getId());
        // config_key 唯一性（改名撞已启用键拦截；排除自身）
        validateKeyDuplicate(reqVO.getConfigKey(), reqVO.getId());
        // 全字段保存式：null 字段保持原值（MP updateById 忽略 null；B 端表单恒全量提交）
        loveConfigMapper.updateById(BeanUtils.toBean(reqVO, LoveConfigDO.class));
    }

    @Override
    public void deleteConfig(Long id) {
        // 校验存在
        validateExists(id);
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）；App 下一轮拉取即少该键（端上走内置兜底）
        loveConfigMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (loveConfigMapper.selectById(id) == null) {
            throw exception(CONFIG_NOT_EXISTS);
        }
    }

    /**
     * config_key 唯一性校验：已存在同键启用/停用行（排除自身 id）则拒绝
     */
    private void validateKeyDuplicate(String configKey, Long excludeId) {
        LoveConfigDO existing = loveConfigMapper.selectByKey(configKey);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw exception(CONFIG_KEY_DUPLICATE);
        }
    }

}
