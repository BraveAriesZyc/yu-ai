package cn.iocoder.yudao.module.love.dal.mysql.dailyquestion;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyQuestionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 每日一问题库 Mapper（M32，B 端配置对象）
 *
 * 功能：admin 分页/CRUD 走 BaseMapperX 通用方法 + LambdaQueryWrapperX（service 层拼条件）；
 * app 侧唯一取数口 = selectListEnabled（轮换池），恒按 id asc 稳定序——
 * 「当日题」按日取模轮换的确定性依赖它（InspirationMapper.selectListQuotesEnabled 同款口径）。
 *
 * @author 芋道源码
 */
@Mapper
public interface DailyQuestionMapper extends BaseMapperX<LoveDailyQuestionDO> {

    /**
     * 查全部启用题目（每日一问轮换池）
     *
     * id asc 稳定序：按日取模轮换的确定性依赖（同一天同池同序 = 同题）；
     * 停用行退出轮换池，已出的 day 快照行不受影响
     */
    default List<LoveDailyQuestionDO> selectListEnabled() {
        return selectList(new LambdaQueryWrapper<LoveDailyQuestionDO>()
                .eq(LoveDailyQuestionDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(LoveDailyQuestionDO::getId));
    }

}
