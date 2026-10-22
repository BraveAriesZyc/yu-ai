package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.todo.vo.LoveTodoPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.todo.vo.LoveTodoRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.todo.LoveTodoDO;
import cn.iocoder.yudao.module.love.dal.mysql.todo.TodoMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.TODO_NOT_EXISTS;

/**
 * 待办管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 todo 组）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在；
 * 删除为逻辑删除（@TableLogic），端上传播走全量覆盖（app 拉取即整体覆盖 TodoStore，天然生效）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminTodoServiceImpl implements LoveAdminTodoService {

    @Resource
    private TodoMapper todoMapper;

    @Override
    public PageResult<LoveTodoRespVO> getTodoPage(LoveTodoPageReqVO pageReqVO) {
        PageResult<LoveTodoDO> pageResult = todoMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveTodoDO>()
                        .eqIfPresent(LoveTodoDO::getCoupleId, pageReqVO.getCoupleId())
                        .eqIfPresent(LoveTodoDO::getOwnerId, pageReqVO.getOwnerId())
                        // @TableLogic 自动附加 deleted = 0，已删待办不出现在分页
                        .betweenIfPresent(LoveTodoDO::getCreateTime, pageReqVO.getCreateTime())
                        .orderByDesc(LoveTodoDO::getId));
        return BeanUtils.toBean(pageResult, LoveTodoRespVO.class);
    }

    @Override
    public void deleteTodo(Long id) {
        // 校验存在（跨 couple 的 id 传入也统一按不存在拒绝，不暴露他 couple 待办的存在性）
        LoveTodoDO todo = todoMapper.selectById(id);
        if (todo == null) {
            throw exception(TODO_NOT_EXISTS);
        }
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）
        todoMapper.deleteById(id);
    }

}
