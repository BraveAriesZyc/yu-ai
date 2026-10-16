package cn.iocoder.yudao.module.love.dal.mysql.user;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * C 端用户 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveUserMapper extends BaseMapperX<LoveUserDO> {

    default LoveUserDO selectByMobile(String mobile) {
        return selectOne(LoveUserDO::getMobile, mobile);
    }

    /**
     * 按邀请码查询其属主
     *
     * 注意：love_user.idx_invite_code 是普通索引（非唯一），正常情况一码一主；
     * 极端撞码时用 selectFirstOne 取首条，避免 selectOne 多行报错（M2 couple 组使用）
     */
    default LoveUserDO selectByInviteCode(String inviteCode) {
        return selectFirstOne(LoveUserDO::getInviteCode, inviteCode);
    }

}
