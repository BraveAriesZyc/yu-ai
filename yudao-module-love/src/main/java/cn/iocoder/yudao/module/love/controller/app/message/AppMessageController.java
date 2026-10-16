package cn.iocoder.yudao.module.love.controller.app.message;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageRespVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendWishesReqVO;
import cn.iocoder.yudao.module.love.service.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 悄悄话/心愿消息的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户由 service 层经 SecurityFrameworkUtils 反查；无客户端传 id 的入口（列表按自己 couple、
 * 发送方恒当前用户、接收方恒 couple 对方），天然防 IDOR。
 * 端上 lastId 游标只增不减（D-12）：admin 删除已拉取消息在端上残留，显式接受。
 */
@Tag(name = "用户 App - 悄悄话/心愿消息")
@RestController
@RequestMapping("/love/message")
@Validated
public class AppMessageController {

    @Resource
    private MessageService messageService;

    @GetMapping("/list")
    @Operation(summary = "增量拉取消息列表（恒升序返回）",
            description = "空参 lastId = 首屏取最近一页（id 倒序取 limit 条后正序返回）；有 lastId = 走 idx_couple_time 顺序扫 id > lastId 升序返回；limit 默认 50、钳制上限 200（§5.2）")
    public CommonResult<List<AppMessageRespVO>> getMessageList(
            @Parameter(description = "上次拉取的最大消息 id（空 = 首屏取最近一页）", example = "1024")
            @RequestParam(value = "lastId", required = false) Long lastId,
            @Parameter(description = "每页条数（默认 50、上限 200）", example = "50")
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(messageService.getMessageList(lastId, limit));
    }

    @GetMapping("/wish-list")
    @Operation(summary = "心愿时间线（kind=2 只读视图，按 id 倒序最新在前）",
            description = "与聊天流解耦的心愿查看入口：聊天 lastId 游标只进不退 + 首屏截断，历史心愿无法回溯；"
                    + "本端点单开 kind=2 过滤视图，快照四字段（content/wishKey/color/icon）全量回传；"
                    + "limit 默认 50、钳制上限 200（§5.2）；无游标，v1 取最近 limit 条，将来补 beforeId 倒序游标平滑升级")
    public CommonResult<List<AppMessageRespVO>> getWishList(
            @Parameter(description = "条数上限（默认 50、上限 200）", example = "200")
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(messageService.getWishList(limit));
    }

    @PostMapping("/send")
    @Operation(summary = "发送文本悄悄话（kind=1）",
            description = "trim 后非空、≤500；发送方 = 当前用户、接收方 = couple 对方快照，audit_status 恒写 1（§12.3）；回包 = 落库后的完整消息 VO（含 id，供端上乐观替换与 lastId 推进，§7.4）")
    public CommonResult<AppMessageRespVO> sendMessage(@RequestBody @Valid AppMessageSendReqVO reqVO) {
        return success(messageService.sendMessage(reqVO));
    }

    @PostMapping("/send-wishes")
    @Operation(summary = "发送心愿消息（kind=2，快照式）",
            description = "wishKey/text/color/icon 四字段整体快照入 love_message，服务端不校验 wish 归属（无 wish 实体，文案字典留端上），两设备还原靠快照字段而非字典版本（§5.2）；回包语义同 send")
    public CommonResult<AppMessageRespVO> sendWishes(@RequestBody @Valid AppMessageSendWishesReqVO reqVO) {
        return success(messageService.sendWishes(reqVO));
    }

}
