package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiRespVO;
import cn.iocoder.yudao.module.love.controller.admin.emoji.vo.LoveEmojiSaveReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.emoji.LoveEmojiDO;
import cn.iocoder.yudao.module.love.dal.mysql.emoji.LoveEmojiMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.EMOJI_NOT_EXISTS;

/**
 * 聊天表情包 Service 实现类（admin 通道，M25）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在（1_003_013_000），
 * LoveAdminInspirationServiceImpl 同款口径；表情即时生效（App 只读，无缓存层）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminEmojiServiceImpl implements LoveAdminEmojiService {

    @Resource
    private LoveEmojiMapper loveEmojiMapper;

    @Override
    public PageResult<LoveEmojiRespVO> getEmojiPage(LoveEmojiPageReqVO pageReqVO) {
        PageResult<LoveEmojiDO> pageResult = loveEmojiMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveEmojiDO>()
                        .likeIfPresent(LoveEmojiDO::getCategory, pageReqVO.getCategory())
                        .eqIfPresent(LoveEmojiDO::getStatus, pageReqVO.getStatus())
                        .betweenIfPresent(LoveEmojiDO::getCreateTime, pageReqVO.getCreateTime())
                        // @TableLogic 自动附加 deleted = 0，已删表情不出现在分页；id desc 新添加在前
                        .orderByDesc(LoveEmojiDO::getId));
        return BeanUtils.toBean(pageResult, LoveEmojiRespVO.class);
    }

    @Override
    public LoveEmojiRespVO createEmoji(LoveEmojiSaveReqVO reqVO) {
        // status 未传走列默认 0 启用（MP 插入忽略 null 字段）；id 忽略（create 语义，防端上误传）
        LoveEmojiDO entity = BeanUtils.toBean(reqVO, LoveEmojiDO.class).setId(null);
        loveEmojiMapper.insert(entity);
        // insert 后 id（AUTO 回填）与 createTime（insertFill）已在 DO 上，直接转 VO
        return BeanUtils.toBean(entity, LoveEmojiRespVO.class);
    }

    @Override
    public void updateEmoji(LoveEmojiSaveReqVO reqVO) {
        // 校验存在（admin 按物理 id 定位）
        validateExists(reqVO.getId());
        // 全字段保存式：null 字段保持原值（MP updateById 忽略 null；B 端表单恒全量提交）
        loveEmojiMapper.updateById(BeanUtils.toBean(reqVO, LoveEmojiDO.class));
    }

    @Override
    public void deleteEmoji(Long id) {
        // 校验存在
        validateExists(id);
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）；表情为 TEXT 链快照，删历史消息渲染零影响
        loveEmojiMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (loveEmojiMapper.selectById(id) == null) {
            throw exception(EMOJI_NOT_EXISTS);
        }
    }

}
