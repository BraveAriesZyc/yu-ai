package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.message.vo.LoveMessagePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.message.vo.LoveMessageRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import cn.iocoder.yudao.module.love.dal.mysql.message.MessageMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MESSAGE_NOT_EXISTS;

/**
 * 悄悄话消息管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 message 组）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在；
 * 删除为逻辑删除（@TableLogic），已拉取消息在端上残留（lastId 只增不减，D-12 显式接受）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminMessageServiceImpl implements LoveAdminMessageService {

    @Resource
    private MessageMapper messageMapper;

    @Override
    public PageResult<LoveMessageRespVO> getMessagePage(LoveMessagePageReqVO pageReqVO) {
        PageResult<LoveMessageDO> pageResult = messageMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveMessageDO>()
                        .eqIfPresent(LoveMessageDO::getCoupleId, pageReqVO.getCoupleId())
                        .eqIfPresent(LoveMessageDO::getKind, pageReqVO.getKind())
                        // content 关键字（审计四件套之一，§5.3）：文本悄悄话与心愿文案快照同列
                        .likeIfPresent(LoveMessageDO::getContent, pageReqVO.getContent())
                        // @TableLogic 自动附加 deleted = 0，已删消息不出现在分页
                        .betweenIfPresent(LoveMessageDO::getCreateTime, pageReqVO.getCreateTime())
                        .orderByDesc(LoveMessageDO::getId));
        return BeanUtils.toBean(pageResult, LoveMessageRespVO.class);
    }

    @Override
    public void deleteMessage(Long id) {
        // 校验存在（跨 couple 的 id 传入也统一按不存在拒绝，不暴露他 couple 消息的存在性）
        LoveMessageDO message = messageMapper.selectById(id);
        if (message == null) {
            throw exception(MESSAGE_NOT_EXISTS);
        }
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）；端上已拉取的该消息残留（D-12）
        messageMapper.deleteById(id);
    }

}
