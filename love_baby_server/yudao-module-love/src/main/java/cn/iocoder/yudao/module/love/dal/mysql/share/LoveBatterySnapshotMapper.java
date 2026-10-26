package cn.iocoder.yudao.module.love.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveBatterySnapshotDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 电量快照 Mapper
 *
 * 功能：按用户查快照行（upsert 读侧 + partner 组装读侧，M26 share 域）。
 * 写侧（insert / updateById）走 BaseMapperX 通用方法，upsert 冲突重试在 service 层收口。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveBatterySnapshotMapper extends BaseMapperX<LoveBatterySnapshotDO> {

    /**
     * 查某用户的电量快照行（uk_user_id 一人一行，selectOne 安全；@TableLogic 自动附加 deleted = 0）
     */
    default LoveBatterySnapshotDO selectByUserId(Long userId) {
        return selectOne(LoveBatterySnapshotDO::getUserId, userId);
    }

    /**
     * 物理清除某用户的电量快照行（M33 账号注销）
     *
     * ⚠ 手写 @Delete 物理 SQL（BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删，注销的
     * 隐私删除必须物理）——LoveLocationSnapshotMapper.purgeBefore 同款先例
     */
    @Delete("DELETE FROM love_battery_snapshot WHERE user_id = #{userId}")
    int deletePhysicalByUserId(@Param("userId") Long userId);

}
