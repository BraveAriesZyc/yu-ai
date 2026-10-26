package cn.iocoder.yudao.module.love.service.user;

import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileRespVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserStatsRespVO;

/**
 * 用户资料 Service 接口
 *
 * 个人信息一律回查 love_user（MEMBER 上下文 buildUserInfo 返回空 Map，见 INTEGRATION-DESIGN.md M-6），
 * 消费方 = MinePage 资料展示与编辑（§7.7）。
 *
 * @author 芋道源码
 */
public interface UserProfileService {

    /**
     * 获得当前登录用户的个人信息（id / mobile / nickname / avatar）
     *
     * @return 个人信息
     */
    AppUserProfileRespVO getUser();

    /**
     * 更新当前登录用户的昵称/头像（仅更新提交的非空字段）
     *
     * @param reqVO 更新请求
     */
    void updateUser(AppUserProfileUpdateReqVO reqVO);

    /**
     * 获得当前登录用户的恋爱统计：在一起天数（couple.bind_time 自然日含当天）
     * 与当前 couple 的悄悄话/待办/心情计数；未绑定时四项全 0
     *
     * @return 恋爱统计
     */
    AppUserStatsRespVO getStats();

    /**
     * 注销当前登录账号（M33 上架合规，商店硬性要求）
     *
     * 口径：解绑（物理删 couple）+ 个人敏感数据物理清除（位置轨迹/地点记录/电量快照/共享开关）
     * + mobile 改写释放 uk_mobile（原手机号可重新注册）+ 逻辑删除用户；couple 共同业务数据
     * 保留不抹（D-11「解绑留数据」同精神，注销不抹掉对方一侧的历史）。token 撤销由 controller
     * 在本方法成功后执行
     */
    void cancelAccount();

}
