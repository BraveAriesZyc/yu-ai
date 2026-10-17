package cn.iocoder.yudao.module.love.service.couple;

import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleBindReqVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleCreateInviteRespVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleOnlineRespVO;
import cn.iocoder.yudao.module.love.controller.app.couple.vo.AppCoupleRespVO;

/**
 * 情侣关系 Service 接口
 *
 * 邀请码存储于 love_user（invite_code / invite_expire_time 内嵌字段，惰性校验，见 INTEGRATION-DESIGN.md D-8）；
 * 绑定 / 解绑均在事务内操作 love_couple，解绑为物理删除（D-11），uk_user_a / uk_user_b 唯一键兜底并发。
 *
 * @author 芋道源码
 */
public interface CoupleService {

    /**
     * 生成/刷新我的邀请码：已有未过期邀请码则复用（不续期），否则重新生成 + 有效期 7 天
     *
     * @return 邀请码与过期时间
     */
    AppCoupleCreateInviteRespVO createInvite();

    /**
     * 凭邀请码绑定情侣关系（事务内，步骤见 INTEGRATION-DESIGN.md D-8）
     *
     * @param reqVO 绑定请求（对方邀请码）
     * @return 绑定后的情侣关系（含伴侣信息）
     */
    AppCoupleRespVO bind(AppCoupleBindReqVO reqVO);

    /**
     * 获得我的情侣关系与伴侣信息（昵称/头像回查 love_user）
     *
     * @return 情侣关系；未绑定时抛 1_003_004_003，端上据此切回绑定引导态
     */
    AppCoupleRespVO getCouple();

    /**
     * 查询伴侣在线状态（M13）：以 WS 内存会话表为准（sender-type=local 单实例，D-18），
     * 对方名下任一在线连接即 true；websocket 关闭/未桥接端恒 false（渐进降级）。
     * 无 lastSeen，「最近在线时间」显式不做；端上仅聊天页停留期间 5s 轮询
     *
     * @return 在线状态 VO（对象形态——端上 HttpClient toOk 标量落空对象，禁改回标量回包）
     */
    AppCoupleOnlineRespVO getCoupleOnline();

    /**
     * 解绑情侣关系：双方任一可发起，事务内物理删除 couple 行（D-11）
     *
     * todo/mood/message 数据保留（couple_id 留历史值），重绑产生新 couple_id
     */
    void unbind();

}
