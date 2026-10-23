package cn.iocoder.yudao.module.love.service.wishtemplate;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.love.controller.app.wishtemplate.vo.AppWishTemplateRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.wishtemplate.LoveWishTemplateDO;
import cn.iocoder.yudao.module.love.dal.mysql.wishtemplate.LoveWishTemplateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 心愿墙挑选模板 Service 实现类（app 通道，M25）
 *
 * 全局共享运营内容：无 couple/user 归属校验、无按 id 写端点、零错误路径——
 * 池空恒回空数组（端上走内置兜底字典），不打错（端上也就无需错误码分支）。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class WishTemplateServiceImpl implements WishTemplateService {

    @Resource
    private LoveWishTemplateMapper loveWishTemplateMapper;

    @Override
    public List<AppWishTemplateRespVO> getWishTemplateList() {
        List<LoveWishTemplateDO> list = loveWishTemplateMapper.selectListEnabled();
        return convertList(list, bean -> BeanUtils.toBean(bean, AppWishTemplateRespVO.class));
    }

}
