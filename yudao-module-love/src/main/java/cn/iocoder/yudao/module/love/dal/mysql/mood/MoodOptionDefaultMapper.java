package cn.iocoder.yudao.module.love.dal.mysql.mood;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodOptionDefaultDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 心情默认选项模板 Mapper（B 端管理对象，M3.5）
 *
 * 功能：admin 分页/CRUD 走 BaseMapperX 通用方法 + LambdaQueryWrapperX（service 层拼条件）；
 * selectListEnabledForSeed 供绑定播种取启用行（MoodOptionService.seedDefaults 调用方 = CoupleServiceImpl.bind）。
 *
 * @author 芋道源码
 */
@Mapper
public interface MoodOptionDefaultMapper extends BaseMapperX<LoveMoodOptionDefaultDO> {

    /**
     * 查全部启用模板行（播种源；绑定事务内读）
     *
     * 排序 sort asc, id asc：播种行逐行复制时保持模板序，落库后 love_mood_option 排序口径一致
     */
    default List<LoveMoodOptionDefaultDO> selectListEnabledForSeed() {
        return selectList(new LambdaQueryWrapper<LoveMoodOptionDefaultDO>()
                .eq(LoveMoodOptionDefaultDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(LoveMoodOptionDefaultDO::getSort)
                .orderByAsc(LoveMoodOptionDefaultDO::getId));
    }

}
