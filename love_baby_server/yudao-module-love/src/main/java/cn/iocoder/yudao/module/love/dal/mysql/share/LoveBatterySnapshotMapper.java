package cn.iocoder.yudao.module.love.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveBatterySnapshotDO;
import org.apache.ibatis.annotations.Mapper;

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

}
