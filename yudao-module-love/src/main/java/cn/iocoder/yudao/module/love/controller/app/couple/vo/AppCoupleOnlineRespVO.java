package cn.iocoder.yudao.module.love.controller.app.couple.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 伴侣在线状态 Response VO（GET /love/couple/online）")
@Data
public class AppCoupleOnlineRespVO {

    @Schema(description = "伴侣是否在线（以 WS 内存会话表为准：对方名下任一在线连接即 true；websocket 关闭或未桥接端恒 false，渐进降级；无 lastSeen，「最近在线时间」显式不做，D-18）", example = "true")
    private Boolean partnerOnline;

}
