package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.moment.vo.LoveMomentRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentDO;
import cn.iocoder.yudao.module.love.dal.mysql.moment.MomentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MOMENT_NOT_EXISTS;

/**
 * 相册瞬间的 admin Service 实现类（M9）：page + delete 两件套（LoveAdminCheckinServiceImpl 同款惯例）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminMomentServiceImpl implements LoveAdminMomentService {

    @Resource
    private MomentMapper momentMapper;

    @Override
    public PageResult<LoveMomentRespVO> getMomentPage(LoveMomentPageReqVO pageReqVO) {
        PageResult<LoveMomentDO> pageResult = momentMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(pageResult, LoveMomentRespVO.class);
    }

    @Override
    public Boolean deleteMoment(Long id) {
        // 判空抛专属码（LoveAdminAnniversaryServiceImpl.java:45-53 惯例）；admin 兜底可删任何人的瞬间
        LoveMomentDO moment = momentMapper.selectById(id);
        if (moment == null) {
            throw exception(MOMENT_NOT_EXISTS);
        }
        momentMapper.deleteById(id);
        return true;
    }

}
