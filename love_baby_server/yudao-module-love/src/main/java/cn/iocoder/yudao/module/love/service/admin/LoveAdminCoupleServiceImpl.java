package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCouplePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCoupleRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;

/**
 * 情侣关系管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 couple 组）
 *
 * 与 app 侧 CoupleServiceImpl 的私有方法结构同源（先读行、再校验、后写），
 * 但 admin 上下文没有 love_user 身份：不注入 SecurityFrameworkUtils、不走停用收口、
 * 解绑按 couple id 定位（app unbind 是按当前登录人反查，D-8/D-11 语义在 app 侧）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminCoupleServiceImpl implements LoveAdminCoupleService {

    @Resource
    private LoveCoupleMapper loveCoupleMapper;

    @Override
    public PageResult<LoveCoupleRespVO> getCouplePage(LoveCouplePageReqVO pageReqVO) {
        // join love_user 的自定义分页（status + 双方昵称筛选在 SQL 内生效），
        // 结果直接映射 RespVO（含 userANickname / userBNickname），无需 BeanUtils 转换
        IPage<LoveCoupleRespVO> page = loveCoupleMapper.selectPageWithUser(
                MyBatisUtils.buildPage(pageReqVO), pageReqVO);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public void unbindCouple(Long id) {
        // 按 couple id 定位（不走当前登录人反查，admin 上下文没有 love_user 身份，§5.3）
        LoveCoupleDO couple = loveCoupleMapper.selectById(id);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        // 物理删除 couple 行（DO 无 @TableLogic，deleteById 即物理 DELETE，D-11）；
        // todo/mood/message 数据保留（couple_id 留历史值），双方端靠轮询 couple/get 回未绑定态
        loveCoupleMapper.deleteById(id);
    }

}
