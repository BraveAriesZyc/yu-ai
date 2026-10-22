package cn.iocoder.yudao.module.love.dal.mysql.moment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentPageReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

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
     * admin 分页：eqIfPresent coupleId / userId + createTime 区间 + orderByDesc(id)
     * （LoveAdminCheckinServiceImpl 同款惯例）
     *
     * M18 对抗审查修正：createTime 入参是 LocalDate[]（yyyy-MM-dd 口径）而 createTime 列是
     * DATETIME——MP 直接 between 会把结束日隐式补成「当日 00:00:00」，结束日全天数据被静默排除。
     * 故区间单独展开：下界 ge 当日零点、上界 lt 次日零点（含结束日全天）；单边语义与
     * betweenIfPresent 一致。checkin 的 checkin_date 是 DATE 列无此问题，不适用本修正
     */
    default PageResult<LoveMomentDO> selectPage(LoveMomentPageReqVO reqVO) {
        LambdaQueryWrapperX<LoveMomentDO> wrapper = new LambdaQueryWrapperX<LoveMomentDO>()
                .eqIfPresent(LoveMomentDO::getCoupleId, reqVO.getCoupleId())
                .eqIfPresent(LoveMomentDO::getUserId, reqVO.getUserId())
                .orderByDesc(LoveMomentDO::getId);
        LocalDate[] range = reqVO.getCreateTime();
        if (range != null && range.length == 2) {
            if (range[0] != null) {
                wrapper.ge(LoveMomentDO::getCreateTime, range[0].atStartOfDay());
            }
            if (range[1] != null) {
                wrapper.lt(LoveMomentDO::getCreateTime, range[1].plusDays(1).atStartOfDay());
            }
        }
        return selectPage(reqVO, wrapper);
    }

}
