package cn.iocoder.yudao.module.love.service.moment;

import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.moment.vo.AppMomentRespVO;

import java.util.List;

/**
 * 相册瞬间 Service 接口（M9）
 *
 * @author 芋道源码
 */
public interface MomentService {

    /**
     * 发布瞬间
     *
     * @param reqVO 发布信息（imageUrl 必填，text 可空）
     * @return 瞬间（含服务端 id/createTime，供端上乐观落态，todo create 先例）
     */
    AppMomentRespVO createMoment(AppMomentCreateReqVO reqVO);

    /**
     * 获得 couple 维度瞬间列表（游标分页，id 倒序新→旧）
     *
     * @param beforeId 游标：取 id 小于该值的下一页，null/0 = 首页（最新一页）
     * @param limit 每页条数（service 层钳制：默认 30，上限 100）
     * @return 瞬间列表
     */
    List<AppMomentRespVO> getMomentList(Long beforeId, Integer limit);

    /**
     * 删除瞬间（仅上传者本人）
     *
     * @param id 瞬间编号
     * @return 是否成功（恒 true，失败以业务异常抛出）
     */
    Boolean deleteMoment(Long id);

}
