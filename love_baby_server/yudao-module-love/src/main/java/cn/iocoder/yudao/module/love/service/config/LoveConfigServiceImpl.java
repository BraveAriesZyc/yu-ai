package cn.iocoder.yudao.module.love.service.config;

import cn.iocoder.yudao.module.love.dal.dataobject.config.LoveConfigDO;
import cn.iocoder.yudao.module.love.dal.mysql.config.LoveConfigMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务配置 Service 实现类（app 通道，M23）
 *
 * ⚠ 类名带 Love 前缀：infra 模块已有同简名 ConfigServiceImpl（bean 名 configServiceImpl
 * 冲突，48081 实例启动实测 ConflictingBeanDefinitionException），跨模块简单名须唯一。
 *
 * 全局共享运营配置：无 couple/user 归属校验、零错误路径——空表/全停用返回空 Map
 * （端上 LoveConfigStore 对缺键走内置兜底值，不打错）；LinkedHashMap 保持 id asc
 * 稳定序（回包字段序稳定，便于冒烟 diff 与端上调试观察）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveConfigServiceImpl implements LoveConfigService {

    @Resource
    private LoveConfigMapper loveConfigMapper;

    @Override
    public Map<String, String> getConfigMap() {
        List<LoveConfigDO> list = loveConfigMapper.selectListEnabled();
        Map<String, String> result = new LinkedHashMap<>(list.size() * 2);
        for (LoveConfigDO config : list) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

}
