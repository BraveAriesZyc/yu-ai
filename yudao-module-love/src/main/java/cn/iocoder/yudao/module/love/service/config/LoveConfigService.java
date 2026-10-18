package cn.iocoder.yudao.module.love.service.config;

import java.util.Map;

/**
 * 业务配置 Service 接口（app 通道，M23）
 *
 * ⚠ 类名带 Love 前缀：infra 模块已有同简名 ConfigService/ConfigServiceImpl
 * （bean 名冲突，48081 实例启动实测），跨模块简单名须唯一。
 *
 * love 业务专属 KV 配置的 App 读取面：仅启用行下发（status=1 停用行不下发，
 * 端上对缺键走内置兜底值），全量键值映射（十几行量级无分页）。
 *
 * @author 芋道源码
 */
public interface LoveConfigService {

    /**
     * 获得全部启用配置（configKey → configValue 映射）
     *
     * @return 键值映射；空表返回空 Map（端上全走内置兜底，不打错——零错误路径）
     */
    Map<String, String> getConfigMap();

}
