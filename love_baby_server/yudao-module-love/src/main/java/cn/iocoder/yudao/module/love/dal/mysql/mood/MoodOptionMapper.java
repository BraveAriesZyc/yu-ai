package cn.iocoder.yudao.module.love.dal.mysql.mood;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodOptionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 心情选项 Mapper（情侣私有字典，M3.5）
 *
 * 功能：app 侧 couple 字典列表（INTEGRATION-DESIGN.md D-14）；
 * 按 id 单条读写与逻辑删除走 BaseMapperX 通用方法（selectById / insert / deleteById），
 * couple 归属校验在 service 层收口（防 IDOR，同 TodoMapper 范式）。
 *
 * @author 芋道源码
 */
@Mapper
public interface MoodOptionMapper extends BaseMapperX<LoveMoodOptionDO> {

    /**
     * 按 couple 查心情选项列表（GET /love/mood/option/list，端上整表覆盖 MoodStore.moodOptions）
     *
     * 排序 sort asc, id asc：播种行（sort 1..5，admin 可调）在前、用户新增（恒 999）按创建序落尾，
     * id 兜底同 sort 行的稳定性
     */
    default List<LoveMoodOptionDO> selectListByCoupleIdOrderBySort(Long coupleId) {
        return selectList(new LambdaQueryWrapper<LoveMoodOptionDO>()
                .eq(LoveMoodOptionDO::getCoupleId, coupleId)
                .orderByAsc(LoveMoodOptionDO::getSort)
                .orderByAsc(LoveMoodOptionDO::getId));
    }

    /**
     * 按 couple + label 精确查重名行（service create 查重用；@TableLogic 自动附加 deleted = 0，
     * 已删行不参与重名判断——删了再加回同名是合法操作）
     */
    default LoveMoodOptionDO selectByCoupleIdAndLabel(Long coupleId, String label) {
        return selectOne(new LambdaQueryWrapper<LoveMoodOptionDO>()
                .eq(LoveMoodOptionDO::getCoupleId, coupleId)
                .eq(LoveMoodOptionDO::getLabel, label));
    }

    /**
     * 按 couple 统计现有选项数（service create 上限校验用，上限 20）
     */
    default Long selectCountByCoupleId(Long coupleId) {
        return selectCount(new LambdaQueryWrapper<LoveMoodOptionDO>()
                .eq(LoveMoodOptionDO::getCoupleId, coupleId));
    }

}
