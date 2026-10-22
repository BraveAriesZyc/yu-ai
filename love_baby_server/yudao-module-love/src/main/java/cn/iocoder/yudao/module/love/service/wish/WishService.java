package cn.iocoder.yudao.module.love.service.wish;

import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishDeleteReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishDoneReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishRespVO;

import java.util.List;

/**
 * 恋爱清单 Service 接口（M14，INTEGRATION-DESIGN.md §5.2 wish 组）
 *
 * @author 芋道源码
 */
public interface WishService {

    /**
     * 获得当前用户所在 couple 的心愿全量列表
     *
     * 服务端按 create_time asc, id asc 稳定序返回（D-19f），「进行中在前 / 已完成沉底」
     * 分层排序由端上做；端上整体覆盖本地 WishStore（§7.4）
     *
     * @return 心愿列表
     */
    List<AppWishRespVO> getWishList();

    /**
     * 创建心愿
     *
     * @param reqVO 创建信息（仅标题，done 态由服务端固定初始未完成）
     * @return 心愿（含回填的 id / createTime）
     */
    AppWishRespVO createWish(AppWishCreateReqVO reqVO);

    /**
     * 勾选完成 / 取消勾选（done 端点单口双向）
     *
     * done=true：CAS 置位（D-19b 恰发一条庆祝消息）→ 重读真值回包；
     * done=false：清完成态三字段 → 重读真值回包
     *
     * @param reqVO 心愿编号 + 目标状态
     * @return 操作后的心愿真值（doneBy / doneTime 必为服务端真值，端上原位替换本地行）
     */
    AppWishRespVO markWishDone(AppWishDoneReqVO reqVO);

    /**
     * 删除心愿（仅创建者可删，D-19e）
     *
     * @param reqVO 心愿编号
     */
    void deleteWish(AppWishDeleteReqVO reqVO);

}
