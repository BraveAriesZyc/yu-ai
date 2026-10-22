package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigRespVO;
import cn.iocoder.yudao.module.love.controller.admin.config.vo.LoveConfigSaveReqVO;

/**
 * 业务配置 Service 接口（admin 通道，M23）
 *
 * love 业务专属 KV 配置的 CRUD：B 端配置对象，App 只读（service/config/ConfigService）。
 * 即时生效：App 每次进页/回前台全量拉取（LoveConfigStore），改配置下一个前台周期可见。
 *
 * @author 芋道源码
 */
public interface LoveAdminConfigService {

    /**
     * 获得配置分页
     *
     * @param pageReqVO 分页查询（name/configKey 模糊、status 精确、创建时间段）
     * @return 配置分页
     */
    PageResult<LoveConfigRespVO> getConfigPage(LoveConfigPageReqVO pageReqVO);

    /**
     * 新增配置
     *
     * @param reqVO 新增信息
     * @return 配置编号回填后的完整信息
     */
    LoveConfigRespVO createConfig(LoveConfigSaveReqVO reqVO);

    /**
     * 更新配置（按 id 全字段保存）
     *
     * @param reqVO 更新信息
     */
    void updateConfig(LoveConfigSaveReqVO reqVO);

    /**
     * 删除配置（逻辑删除）
     *
     * @param id 配置编号
     */
    void deleteConfig(Long id);

}
