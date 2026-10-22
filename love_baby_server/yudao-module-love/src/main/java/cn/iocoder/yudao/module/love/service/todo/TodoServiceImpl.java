package cn.iocoder.yudao.module.love.service.todo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoRespVO;
import cn.iocoder.yudao.module.love.controller.app.todo.vo.AppTodoUpdateReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.todo.LoveTodoDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.todo.TodoMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.TODO_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.TODO_NO_PERMISSION;

/**
 * 待办 Service 实现类
 *
 * 与 couple 组同一套私有方法结构：getCurrentUserId / getValidatedCurrentUser（停用收口，§6.5）
 * + getValidatedCurrentCouple（未绑定拒绝，1_003_004_003）+ validateTodoBelongsToCouple（IDOR 防范）。
 * couple 由当前登录人反查（SecurityFrameworkUtils），不接受客户端传入 coupleId / userId，
 * 按 id 写操作再校验 todo.coupleId == 当前 coupleId，双重收口防 IDOR（§5.2 表头强约束）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class TodoServiceImpl implements TodoService {

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private TodoMapper todoMapper;

    @Override
    public List<AppTodoRespVO> getTodoList() {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        // 未绑定 couple → 1_003_004_003（todo 归属 = couple 共享，无 couple 无列表）
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 排序 create_time asc, id asc 由 mapper 收口（端上覆盖 TodoStore，§7.4）
        return BeanUtils.toBean(todoMapper.selectListByCoupleId(couple.getId()), AppTodoRespVO.class);
    }

    @Override
    public AppTodoRespVO createTodo(AppTodoCreateReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // owner_id = 当前用户（仅记录归属，权限依据 = couple 归属校验）；done 固定初始 false；
        // tag/color 只存字符串不校验取值（客户端字典，铁律⑥），长度上限由 VO @Length 挡在入口；
        // tag/color VO 可空，对 null 归一空串（love_todo 列 NOT NULL DEFAULT ''，M3 实施修正：
        // 防 null 直插严格模式报错；端上恒传值、admin 页只读，空串缺省语义正确）
        String tag = reqVO.getTag() == null ? "" : reqVO.getTag();
        String color = reqVO.getColor() == null ? "" : reqVO.getColor();
        LoveTodoDO todo = new LoveTodoDO()
                .setCoupleId(couple.getId())
                .setOwnerId(user.getId())
                .setTitle(reqVO.getTitle())
                .setDone(false)
                .setTag(tag)
                .setColor(color);
        todoMapper.insert(todo);
        // insert 后 id（MySQL AUTO 回填）与 createTime（BaseDO insertFill 回填）已在 DO 上，直接转 VO
        return BeanUtils.toBean(todo, AppTodoRespVO.class);
    }

    @Override
    public void updateTodo(AppTodoUpdateReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 先查 todo 再校验 couple 归属（防 IDOR，§5.2 表头强约束）
        LoveTodoDO todo = validateTodoBelongsToCouple(reqVO.getId(), couple.getId());
        // 全字段保存式（§5.2：含 toggle done）：title/tag/color/done 整体落库；
        // done 为 null 时 updateById 忽略该字段、保留原值（全字段提交是端上约定，见 VO 类注释）；
        // tag/color 与 create 同口径对 null 归一空串（love_todo 列 NOT NULL DEFAULT ''，M3 实施修正）
        String tag = reqVO.getTag() == null ? "" : reqVO.getTag();
        String color = reqVO.getColor() == null ? "" : reqVO.getColor();
        todoMapper.updateById(new LoveTodoDO().setId(todo.getId())
                .setTitle(reqVO.getTitle())
                .setDone(reqVO.getDone())
                .setTag(tag)
                .setColor(color));
    }

    @Override
    public void deleteTodo(AppTodoIdReqVO reqVO) {
        // §6.5 停用账号收口
        LoveUserDO user = getValidatedCurrentUser();
        LoveCoupleDO couple = getValidatedCurrentCouple(user.getId());
        // 归属校验同 update（防 IDOR）
        LoveTodoDO todo = validateTodoBelongsToCouple(reqVO.getId(), couple.getId());
        // LoveTodoDO 继承 BaseDO 启用 @TableLogic，deleteById 即逻辑删除（D-11：couple 解绑后 todo 数据保留）
        todoMapper.deleteById(todo.getId());
    }

    /**
     * 获得当前登录用户编号
     *
     * 理论不可达为 null：controller 未标 @PermitAll，未登录请求已被 anyRequest().authenticated() 拦截；
     * 万一穿透（token 被并发移除等），按 401 让端上走刷新/回 Gate 通道（CoupleServiceImpl 同款）
     */
    private Long getCurrentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 校验当前用户可用（§6.5 停用账号收口）：行不存在（被 admin 删除）与停用统一按
     * 「账号已停用」（1_003_005_002）处理，端上清会话回 Gate，行为一致
     *
     * @return 当前用户
     */
    private LoveUserDO getValidatedCurrentUser() {
        return getValidatedCurrentUser(getCurrentUserId());
    }

    private LoveUserDO getValidatedCurrentUser(Long userId) {
        LoveUserDO user = loveUserMapper.selectById(userId);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return user;
    }

    /**
     * 校验当前用户存在有效 couple 关系（未绑定抛 1_003_004_003）
     *
     * couple 由当前登录人反查（LoveCoupleMapper.selectByUserId，无客户端传参），天然防 IDOR；
     * couple 行物理删除、行存在即绑定态（D-11），查询口径与 couple 组一致
     *
     * @return 当前用户所在的 couple
     */
    private LoveCoupleDO getValidatedCurrentCouple(Long userId) {
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(userId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        return couple;
    }

    /**
     * 按 id 查 todo 并强制校验 couple 归属（§5.2 表头强约束，防 IDOR）
     *
     * 不存在抛 TODO_NOT_EXISTS（1_003_001_000）；存在但不属于当前 couple 抛
     * TODO_NO_PERMISSION（1_003_001_001，§11 IDOR 用例对 todo 组的指定错误码）
     *
     * @return 归属校验通过的 todo
     */
    private LoveTodoDO validateTodoBelongsToCouple(Long todoId, Long coupleId) {
        LoveTodoDO todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw exception(TODO_NOT_EXISTS);
        }
        if (!Objects.equals(todo.getCoupleId(), coupleId)) {
            throw exception(TODO_NO_PERMISSION);
        }
        return todo;
    }

}
