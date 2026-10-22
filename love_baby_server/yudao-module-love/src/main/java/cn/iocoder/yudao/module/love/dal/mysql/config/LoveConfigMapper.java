package cn.iocoder.yudao.module.love.dal.mysql.config;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.config.LoveConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 业务配置 Mapper（B 端配置对象，M23）
 *
 * ⚠ 类名带 Love 前缀：infra 模块已有同简名 ConfigMapper（bean 名 configMapper 冲突，
 * 48081 实例启动实测 ConflictingBeanDefinitionException），跨模块简单名须唯一。
 *
 * 功能：admin 分页/CRUD 走 BaseMapperX 通用方法 + LambdaQueryWrapperX（service 层拼条件）；
 * app 侧全量取启用行（selectListEnabled，十几行量级 KV 无分页）。
 * 唯一性靠 service 层 selectByKey 校验（@TableLogic 自动过滤已删行），本表无 DB uk。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveConfigMapper extends BaseMapperX<LoveConfigDO> {

    /**
     * 按配置键查一条（service 层唯一性校验 + admin 键定位用）
     *
     * @TableLogic 自动附加 deleted = 0，已删的同键残留行天然不参与唯一性判定
     */
    default LoveConfigDO selectByKey(String configKey) {
        return selectOne(LoveConfigDO::getConfigKey, configKey);
    }

    /**
     * 查全部启用配置（app GET /love/config/list 数据源；id asc 稳定序便于 diff 观察）
     */
    default List<LoveConfigDO> selectListEnabled() {
        return selectList(new LambdaQueryWrapper<LoveConfigDO>()
                .eq(LoveConfigDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(LoveConfigDO::getId));
    }

}
