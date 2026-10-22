package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.message.vo.LoveMessagePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.message.vo.LoveMessageRespVO;

/**
 * 悄悄话消息管理 Service 接口（admin 通道，INTEGRATION-DESIGN.md §5.3 message 组）
 *
 * 首期不做用户侧撤回/删除（D-12），admin 删除为唯一删除入口。
 *
 * @author 芋道源码
 */
public interface LoveAdminMessageService {

    /**
     * 获得悄悄话消息分页
     *
     * @param pageReqVO 分页查询（coupleId/kind 精确 + content 关键字模糊 + createTime 段，审计四件套）
     * @return 消息分页
     */
    PageResult<LoveMessageRespVO> getMessagePage(LoveMessagePageReqVO pageReqVO);

    /**
     * 删除消息（逻辑删除；已拉取消息在端上残留，lastId 只增不减，D-12 显式接受）
     *
     * @param id 消息编号
     */
    void deleteMessage(Long id);

}
