package cn.iocoder.yudao.module.love.dal.mysql.anniversary;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.anniversary.LoveAnniversaryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 纪念日 Mapper
 *
 * 功能：app 侧全量列表查询（INTEGRATION-DESIGN.md §5.2 anniversary 组）；
 * 按 id 单条读写走 BaseMapperX 通用方法（selectById / deleteById），
 * couple 归属校验在 service 层收口（防 IDOR，§5.2）。
 *
 * @author 芋道源码
 */
@Mapper
public interface AnniversaryMapper extends BaseMapperX<LoveAnniversaryDO> {

    /**
     * 按 couple 查全量纪念日列表（§5.2 GET /love/anniversary/list，端上整体覆盖 AnniversaryStore）
     *
     * 排序 anniv_date asc, id asc：贴近「按日期排列」的直觉序；端上展示序（倒数升序 + 已过沉底）
     * 由端上纯函数重排（D-17），服务端只提供稳定序，id 兜底同日多条的稳定性
     */
    default List<LoveAnniversaryDO> selectListByCoupleId(Long coupleId) {
        return selectList(new LambdaQueryWrapper<LoveAnniversaryDO>()
                .eq(LoveAnniversaryDO::getCoupleId, coupleId)
                .orderByAsc(LoveAnniversaryDO::getAnnivDate)
                .orderByAsc(LoveAnniversaryDO::getId));
    }

}
