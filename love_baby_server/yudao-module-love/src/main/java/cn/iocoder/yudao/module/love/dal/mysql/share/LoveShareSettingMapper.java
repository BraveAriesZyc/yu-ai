package cn.iocoder.yudao.module.love.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveShareSettingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 共享开关 Mapper
 *
 * 功能：按用户查开关行（懒创建读侧 + partner 组装读侧，M26 share 域）。
 * 写侧（insert / updateById）走 BaseMapperX 通用方法。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveShareSettingMapper extends BaseMapperX<LoveShareSettingDO> {

    /**
     * 查某用户的开关行（uk_user_id 一人一行，selectOne 安全；@TableLogic 自动附加 deleted = 0；
     * 行不存在 = 懒创建未落行，service 按默认值 batteryShare=true / locationShare=false 返回）
     */
    default LoveShareSettingDO selectByUserId(Long userId) {
        return selectOne(LoveShareSettingDO::getUserId, userId);
    }

}
