package cn.iocoder.yudao.module.love.dal.mysql.wishtemplate;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.wishtemplate.LoveWishTemplateDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 心愿墙挑选模板 Mapper（B 端配置对象，M25）
 *
 * 功能：admin 分页/CRUD 走 BaseMapperX 通用方法 + LambdaQueryWrapperX（service 层拼条件）；
 * app 侧取数 selectListEnabled() 按 sort asc, id asc 稳定序——端上按回包顺序渲染不重排。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveWishTemplateMapper extends BaseMapperX<LoveWishTemplateDO> {

    /**
     * 查全部启用模板（App 只读端点取数）
     *
     * sort asc, id asc 稳定序：种子行按 sort 落位，后台新加同 sort 行 id 大者靠后
     */
    default List<LoveWishTemplateDO> selectListEnabled() {
        return selectList(new LambdaQueryWrapper<LoveWishTemplateDO>()
                .eq(LoveWishTemplateDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(LoveWishTemplateDO::getSort)
                .orderByAsc(LoveWishTemplateDO::getId));
    }

}
