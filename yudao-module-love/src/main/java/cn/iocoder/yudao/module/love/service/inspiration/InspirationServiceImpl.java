package cn.iocoder.yudao.module.love.service.inspiration;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.love.controller.app.inspiration.vo.AppInspirationQuoteRespVO;
import cn.iocoder.yudao.module.love.controller.app.inspiration.vo.AppInspirationRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.inspiration.LoveInspirationDO;
import cn.iocoder.yudao.module.love.dal.mysql.inspiration.InspirationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 恋爱灵感 Service 实现类（app 通道，M17）
 *
 * 全局共享运营内容：无 couple/user 归属校验、无按 id 写端点、零错误路径——
 * 池空恒走兜底文案（QUOTE_FALLBACK），不打错（端上也就无需错误码分支）；
 * 情话池恒 id asc 稳定序（Mapper 惯例），「按日取模」的确定性依赖它。
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class InspirationServiceImpl implements InspirationService {

    /**
     * 情话池空（全部停用/删除）时的兜底文案：admin 兜底可恢复，端上永不打错
     */
    private static final String QUOTE_FALLBACK = "今天也想把全世界的温柔，都悄悄塞进你口袋。";

    @Resource
    private InspirationMapper inspirationMapper;

    @Override
    public AppInspirationQuoteRespVO getTodayQuote() {
        List<LoveInspirationDO> quotes = inspirationMapper.selectListQuotesEnabled();
        if (quotes.isEmpty()) {
            return buildFallbackVO();
        }
        // 按日轮换：dateKey（epoch day）对池 size 取模 → 稳定序下标；每天自动换句，同日恒同句
        long index = LocalDate.now().toEpochDay() % quotes.size();
        return buildQuoteVO(quotes.get((int) index));
    }

    @Override
    public AppInspirationQuoteRespVO getRandomQuote() {
        List<LoveInspirationDO> quotes = inspirationMapper.selectListQuotesEnabled();
        if (quotes.isEmpty()) {
            return buildFallbackVO();
        }
        // 换一句 = 排除今日句随机（池 >1 时必换；池仅 1 条时返回它本身——语义上无句可换）。
        // M18 修复：池剩 1 条时 nextInt(0) 抛 IllegalArgumentException → 500，先早退
        if (quotes.size() == 1) {
            return buildQuoteVO(quotes.get(0));
        }
        long todayIndex = LocalDate.now().toEpochDay() % quotes.size();
        int randomIndex = ThreadLocalRandom.current().nextInt(quotes.size() - 1);
        if (randomIndex >= todayIndex) {
            randomIndex++;
        }
        return buildQuoteVO(quotes.get(randomIndex));
    }

    @Override
    public List<AppInspirationRespVO> getInspirationList() {
        List<LoveInspirationDO> list = inspirationMapper.selectListDateIdeasEnabled();
        return convertList(list, bean -> BeanUtils.toBean(bean, AppInspirationRespVO.class));
    }

    private AppInspirationQuoteRespVO buildFallbackVO() {
        return AppInspirationQuoteRespVO.builder().content(QUOTE_FALLBACK).build();
    }

    private AppInspirationQuoteRespVO buildQuoteVO(LoveInspirationDO quote) {
        return AppInspirationQuoteRespVO.builder().content(quote.getContent()).build();
    }

}
