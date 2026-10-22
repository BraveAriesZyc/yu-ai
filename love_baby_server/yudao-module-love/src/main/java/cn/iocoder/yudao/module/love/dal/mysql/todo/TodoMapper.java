package cn.iocoder.yudao.module.love.dal.mysql.todo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.todo.LoveTodoDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 待办 Mapper
 *
 * 功能：app 侧全量列表查询（INTEGRATION-DESIGN.md §5.2 todo 组）；
 * 按 id 单条读写走 BaseMapperX 通用方法（selectById / updateById / deleteById），
 * couple 归属校验在 service 层收口（防 IDOR，§5.2）。
 *
 * @author 芋道源码
 */
@Mapper
public interface TodoMapper extends BaseMapperX<LoveTodoDO> {

    /**
     * 按 couple 查全量待办列表（§5.2 GET /love/todo/list，端上整体覆盖 TodoStore）
     *
     * 排序 create_time asc, id asc：与端上本地假数据顺序一致（创建序），
     * id 兜底同一秒内多条记录的稳定性
     */
    default List<LoveTodoDO> selectListByCoupleId(Long coupleId) {
        return selectList(new LambdaQueryWrapper<LoveTodoDO>()
                .eq(LoveTodoDO::getCoupleId, coupleId)
                .orderByAsc(LoveTodoDO::getCreateTime)
                .orderByAsc(LoveTodoDO::getId));
    }

}
