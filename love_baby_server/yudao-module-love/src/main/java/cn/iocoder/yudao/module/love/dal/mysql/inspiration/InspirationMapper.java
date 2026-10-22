package cn.iocoder.yudao.module.love.dal.mysql.inspiration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.inspiration.LoveInspirationDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 恋爱灵感 Mapper（B 端配置对象，M17）
 *
 * 功能：admin 分页/CRUD 走 BaseMapperX 通用方法 + LambdaQueryWrapperX（service 层拼条件）；
 * app 侧两池取数（selectListQuotesEnabled / selectListDateIdeasEnabled）恒按 id asc 稳定序——
 * 「今日情话」按日取模轮换依赖稳定序保证同日同句。
 *
 * @author 芋道源码
 */
@Mapper
public interface InspirationMapper extends BaseMapperX<LoveInspirationDO> {

    /**
     * 查全部启用情话（kind=1，今日情话池）
     *
     * id asc 稳定序：按日取模轮换的确定性依赖（同一天同池同序 = 同句）
     */
    default List<LoveInspirationDO> selectListQuotesEnabled() {
        return selectList(new LambdaQueryWrapper<LoveInspirationDO>()
                .eq(LoveInspirationDO::getKind, 1)
                .eq(LoveInspirationDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(LoveInspirationDO::getId));
    }

    /**
     * 查全部启用约会灵感（kind=2，约会灵感列表池）
     */
    default List<LoveInspirationDO> selectListDateIdeasEnabled() {
        return selectList(new LambdaQueryWrapper<LoveInspirationDO>()
                .eq(LoveInspirationDO::getKind, 2)
                .eq(LoveInspirationDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(LoveInspirationDO::getId));
    }

}
