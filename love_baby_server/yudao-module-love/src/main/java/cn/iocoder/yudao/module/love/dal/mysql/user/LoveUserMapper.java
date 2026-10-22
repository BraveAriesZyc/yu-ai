package cn.iocoder.yudao.module.love.dal.mysql.user;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 只进不退推进聊天已读游标（M11 POST /love/message/read）
     *
     * 注解 SQL 不经 MP 注入逻辑：显式 WHERE deleted = 0（love_user 逻辑删，bit(1) 比较同 CheckinMapper
     * 先例）、显式 update_time = NOW()（MP 自动填充不作用于注解 SQL）；GREATEST 保证并发/迟到标记
     * 不回退，影响行数 0（用户行不存在/已删）静默忽略——读游标自伤无动机，不校验消息归属（从简，§5.2）
     */
    @Update("UPDATE love_user SET chat_read_cursor = GREATEST(chat_read_cursor, #{lastMessageId}), "
            + "update_time = NOW() WHERE id = #{userId} AND deleted = 0")
    int updateChatReadCursorForward(@Param("userId") Long userId, @Param("lastMessageId") Long lastMessageId);

}
