package cn.iocoder.yudao.module.love.controller.app.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileRespVO;
import cn.iocoder.yudao.module.love.controller.app.user.vo.AppUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.love.service.user.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户资料的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 userId（防 IDOR）。
 */
@Tag(name = "用户 App - 用户资料")
@RestController
@RequestMapping("/love/user")
@Validated
public class AppUserController {

    @Resource
    private UserProfileService userProfileService;

    @GetMapping("/get")
    @Operation(summary = "获得当前登录用户的个人信息",
            description = "昵称/头像一律由此接口回查 love_user（MEMBER 上下文 buildUserInfo 返回空 Map，见 INTEGRATION-DESIGN.md M-6）")
    public CommonResult<AppUserProfileRespVO> getUser() {
        return success(userProfileService.getUser());
    }

    @PostMapping("/update")
    @Operation(summary = "更新当前登录用户的昵称/头像",
            description = "M2 实施修正：app-api 用 POST 而非 RESTful 的 PUT —— Kuikly core 2.29 NetworkModule 仅支持 GET/POST（反编译 AAR 核实），M3 的 todo update/delete 同理；admin-api 不受限仍 RESTful")
    public CommonResult<Boolean> updateUser(@RequestBody @Valid AppUserProfileUpdateReqVO reqVO) {
        userProfileService.updateUser(reqVO);
        return success(true);
    }

}
