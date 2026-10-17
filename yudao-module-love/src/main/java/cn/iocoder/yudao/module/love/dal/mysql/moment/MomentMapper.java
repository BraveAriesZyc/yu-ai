package cn.iocoder.yudao.module.love.dal.mysql.moment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentPageReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 相册瞬间 Mapper（M9）
 *
 * @author 芋道源码
 */
@Mapper
public interface MomentMapper extends BaseMapperX<LoveMomentDO> {

    /**
     * couple 维度游标分页：id 倒序（新→旧），beforeId 为空取最新一页，非空取 id &lt; beforeId 的下一页。
     * 走 idx_couple_id(couple_id, id) 索引；limit 由调用方钳制（默认 30 上限 100），此处直接拼 LIMIT
     */
    default java.util.List<LoveMomentDO> selectListByCoupleIdBefore(Long coupleId, Long beforeId, int limit) {
        LambdaQueryWrapper<LoveMomentDO> wrapper = new LambdaQueryWrapper<LoveMomentDO>()
                .eq(LoveMomentDO::getCoupleId, coupleId)
                .orderByDesc(LoveMomentDO::getId)
                .last("LIMIT " + limit);
        if (beforeId != null && beforeId > 0) {
            wrapper.lt(LoveMomentDO::getId, beforeId);
        }
        return selectList(wrapper);
    }

    /**
     * admin 分页：eqIfPresent coupleId / userId + betweenIfPresent createTime + orderByDesc(id)
     * （LoveAdminCheckinServiceImpl 同款惯例）
     */
    default PageResult<LoveMomentDO> selectPage(LoveMomentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<LoveMomentDO>()
                .eqIfPresent(LoveMomentDO::getCoupleId, reqVO.getCoupleId())
                .eqIfPresent(LoveMomentDO::getUserId, reqVO.getUserId())
                .betweenIfPresent(LoveMomentDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(LoveMomentDO::getId));
    }

}
