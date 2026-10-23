package cn.iocoder.yudao.module.love.service.emoji;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.love.controller.app.emoji.vo.AppEmojiRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.emoji.LoveEmojiDO;
import cn.iocoder.yudao.module.love.dal.mysql.emoji.LoveEmojiMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 聊天表情包 Service 实现类（app 通道，M25）
 *
 * 全局共享运营内容：无 couple/user 归属校验、无按 id 写端点、零错误路径——
 * 池空恒回空数组（端上走内置兜底字典），不打错（端上也就无需错误码分支）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class EmojiServiceImpl implements EmojiService {

    @Resource
    private LoveEmojiMapper loveEmojiMapper;

    @Override
    public List<AppEmojiRespVO> getEmojiList() {
        List<LoveEmojiDO> list = loveEmojiMapper.selectListEnabled();
        return convertList(list, bean -> BeanUtils.toBean(bean, AppEmojiRespVO.class));
    }

}
