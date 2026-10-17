package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.checkin.vo.LoveCheckinPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.checkin.vo.LoveCheckinRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.checkin.LoveCheckinDO;
import cn.iocoder.yudao.module.love.dal.mysql.checkin.CheckinMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.CHECKIN_NOT_EXISTS;

/**
 * 每日打卡管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 checkin 组，M8）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在；
 * 删除为逻辑删除（@TableLogic），端上传播走整月覆盖/汇总重拉（app 拉取即整体覆盖，天然生效）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminCheckinServiceImpl implements LoveAdminCheckinService {

    @Resource
    private CheckinMapper checkinMapper;

    @Override
    public PageResult<LoveCheckinRespVO> getCheckinPage(LoveCheckinPageReqVO pageReqVO) {
        PageResult<LoveCheckinDO> pageResult = checkinMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveCheckinDO>()
                        // userId 精确匹配（M8 冻结口径：无 coupleId 精确项）
                        .eqIfPresent(LoveCheckinDO::getUserId, pageReqVO.getUserId())
                        // @TableLogic 自动附加 deleted = 0，已删记录不出现在分页
                        .betweenIfPresent(LoveCheckinDO::getCheckinDate, pageReqVO.getCheckinDate())
                        .orderByDesc(LoveCheckinDO::getId));
        return BeanUtils.toBean(pageResult, LoveCheckinRespVO.class);
    }

    @Override
    public void deleteCheckin(Long id) {
        // 校验存在（admin 无 couple 上下文，行不存在即打 1_003_007_000，LoveAdminAnniversaryServiceImpl 同款）
        LoveCheckinDO checkin = checkinMapper.selectById(id);
        if (checkin == null) {
            throw exception(CHECKIN_NOT_EXISTS);
        }
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）
        checkinMapper.deleteById(id);
    }

}
