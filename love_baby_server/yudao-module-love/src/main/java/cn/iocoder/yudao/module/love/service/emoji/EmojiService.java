package cn.iocoder.yudao.module.love.service.emoji;

import cn.iocoder.yudao.module.love.controller.app.emoji.vo.AppEmojiRespVO;

import java.util.List;

/**
 * 聊天表情包 Service 接口（app 通道，M25）
 *
 * @author 芋道源码
 */
public interface EmojiService {

    /**
     * 获得表情包全量列表（启用行，sort asc, id asc 稳定序；端上分类聚合）
     */
    List<AppEmojiRespVO> getEmojiList();

}
