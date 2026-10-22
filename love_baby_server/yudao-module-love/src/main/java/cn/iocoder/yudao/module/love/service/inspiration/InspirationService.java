package cn.iocoder.yudao.module.love.service.inspiration;

import cn.iocoder.yudao.module.love.controller.app.inspiration.vo.AppInspirationQuoteRespVO;
import cn.iocoder.yudao.module.love.controller.app.inspiration.vo.AppInspirationRespVO;

import java.util.List;

/**
 * 恋爱灵感 Service 接口（app 通道，M17）
 *
 * @author 芋道源码
 */
public interface InspirationService {

    /**
     * 获得今日情话
     *
     * 按日轮换：LocalDate.now().toEpochDay() 对启用情话池 size 取模，id asc 稳定序下
     * 同一天恒同句；池空返回兜底文案（不打错）
     */
    AppInspirationQuoteRespVO getTodayQuote();

    /**
     * 随机换一句情话
     *
     * 随机取启用情话池一条，【排除今日句】保证「换一句」必换；池 ≤1 条返回今日句本身
     */
    AppInspirationQuoteRespVO getRandomQuote();

    /**
     * 获得约会灵感全量列表（kind=2 启用行，id asc 稳定序；端上分类筛选 + 换一换打乱）
     */
    List<AppInspirationRespVO> getInspirationList();

}
