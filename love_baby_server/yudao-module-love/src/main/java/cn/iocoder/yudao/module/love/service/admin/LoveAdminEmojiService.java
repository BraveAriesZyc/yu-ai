package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiRespVO;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiSaveReqVO;

/**
 * 聊天表情包 Service 接口（admin 通道，M25）
 *
 * @author 芋道源码
 */
public interface LoveAdminEmojiService {

    /**
     * 获得表情包分页
     */
    PageResult<LoveEmojiRespVO> getEmojiPage(LoveEmojiPageReqVO pageReqVO);

    /**
     * 新增表情，返回带自增 id 的 VO
     */
    LoveEmojiRespVO createEmoji(LoveEmojiSaveReqVO reqVO);

    /**
     * 更新表情（全字段保存式，null 字段保持原值）
     */
    void updateEmoji(LoveEmojiSaveReqVO reqVO);

    /**
     * 删除表情（逻辑删除）
     */
    void deleteEmoji(Long id);

}
