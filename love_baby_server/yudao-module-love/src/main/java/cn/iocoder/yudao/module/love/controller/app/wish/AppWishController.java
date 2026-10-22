package cn.iocoder.yudao.module.love.controller.app.wish;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishDeleteReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishDoneReqVO;
import cn.iocoder.yudao.module.love.controller.app.wish.vo.AppWishRespVO;
import cn.iocoder.yudao.module.love.service.wish.WishService;
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

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 恋爱清单的用户 App 接口（INTEGRATION-DESIGN.md §5.2 wish 组，M14）
 *
 * 写端点统一 POST（core NetworkModule 仅 GET/POST，send-wishes 先例）；
 * coupleId / userId 一律服务端反查（SecurityFrameworkUtils），不收客户端传入（§5.2 表头强约束）；
 * done 回包 = 操作后真值对象（D-19h：端上原位替换本地行），恒为对象形态
 * （端上 HttpClient toOk 只分派对象/数组，标量落空对象——M11 冻结口径）。
 */
@Tag(name = "用户 App - 恋爱清单")
@RestController
@RequestMapping("/love/wish")
@Validated
public class AppWishController {

    @Resource
    private WishService wishService;

    @GetMapping("/list")
    @Operation(summary = "获得心愿全量列表",
            description = "couple 共享（双方互见），create_time asc, id asc 稳定序返回；" +
                    "端上整体覆盖本地 WishStore（§7.4），「进行中在前 / 已完成沉底」分层排序端上做（D-19f）")
    public CommonResult<List<AppWishRespVO>> getWishList() {
        return success(wishService.getWishList());
    }

    @PostMapping("/create")
    @Operation(summary = "创建心愿",
            description = "userId = 当前登录人（创建人，删除权限依据 D-19e），done 固定初始未完成")
    public CommonResult<AppWishRespVO> createWish(@Valid @RequestBody AppWishCreateReqVO reqVO) {
        return success(wishService.createWish(reqVO));
    }

    @PostMapping("/done")
    @Operation(summary = "勾选完成 / 取消勾选心愿",
            description = "done=true：CAS 置位，每次「未完成→完成」跃迁自动发一条庆祝消息到悄悄话（D-19b/c），" +
                    "重复勾选幂等（doneBy/doneTime 不变，消息不重发）；done=false：清完成态（不发消息）；" +
                    "回包 = 操作后库真值（D-19h）")
    public CommonResult<AppWishRespVO> markWishDone(@Valid @RequestBody AppWishDoneReqVO reqVO) {
        return success(wishService.markWishDone(reqVO));
    }

    @PostMapping("/delete")
    @Operation(summary = "删除心愿",
            description = "仅创建者可删（D-19e，1_003_009_001）；逻辑删除；端上传播走全量覆盖")
    public CommonResult<Boolean> deleteWish(@Valid @RequestBody AppWishDeleteReqVO reqVO) {
        wishService.deleteWish(reqVO);
        return success(true);
    }

}
