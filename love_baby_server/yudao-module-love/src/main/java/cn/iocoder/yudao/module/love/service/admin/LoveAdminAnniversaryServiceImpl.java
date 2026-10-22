package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.anniversary.vo.LoveAnniversaryPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.anniversary.vo.LoveAnniversaryRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.anniversary.LoveAnniversaryDO;
import cn.iocoder.yudao.module.love.dal.mysql.anniversary.AnniversaryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.ANNIVERSARY_NOT_EXISTS;

/**
 * 纪念日管理 Service 实现类（admin 通道，INTEGRATION-DESIGN.md §5.3 anniversary 组，M5）
 *
 * admin 上下文没有 love_user 身份：按物理 id 定位 + 先校验存在；
 * 删除为逻辑删除（@TableLogic），端上传播走全量覆盖（app 拉取即整体覆盖 AnniversaryStore，天然生效）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class LoveAdminAnniversaryServiceImpl implements LoveAdminAnniversaryService {

    @Resource
    private AnniversaryMapper anniversaryMapper;

    @Override
    public PageResult<LoveAnniversaryRespVO> getAnniversaryPage(LoveAnniversaryPageReqVO pageReqVO) {
        PageResult<LoveAnniversaryDO> pageResult = anniversaryMapper.selectPage(pageReqVO,
                new LambdaQueryWrapperX<LoveAnniversaryDO>()
                        // title 模糊匹配（M5 冻结口径：无 coupleId/ownerId 精确项）
                        .likeIfPresent(LoveAnniversaryDO::getTitle, pageReqVO.getTitle())
                        // @TableLogic 自动附加 deleted = 0，已删纪念日不出现在分页
                        .betweenIfPresent(LoveAnniversaryDO::getCreateTime, pageReqVO.getCreateTime())
                        .orderByDesc(LoveAnniversaryDO::getId));
        return BeanUtils.toBean(pageResult, LoveAnniversaryRespVO.class);
    }

    @Override
    public void deleteAnniversary(Long id) {
        // 校验存在（admin 无 couple 上下文，行不存在即打 1_003_006_000）
        LoveAnniversaryDO anniversary = anniversaryMapper.selectById(id);
        if (anniversary == null) {
            throw exception(ANNIVERSARY_NOT_EXISTS);
        }
        // 逻辑删除（@TableLogic → UPDATE deleted = 1）
        anniversaryMapper.deleteById(id);
    }

}
