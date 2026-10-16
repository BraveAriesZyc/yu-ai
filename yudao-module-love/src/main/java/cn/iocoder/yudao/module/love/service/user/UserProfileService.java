package cn.iocoder.yudao.module.love.service.user;

import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileRespVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileUpdateReqVO;

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

}
