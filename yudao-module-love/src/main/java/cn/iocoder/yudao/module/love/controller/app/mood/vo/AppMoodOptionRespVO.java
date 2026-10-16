package cn.iocoder.yudao.module.love.controller.app.mood.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 心情选项 Response VO（GET /love/mood/option/list 数组元素与 create 回包统一使用，M3.5）
 *
 * id 为服务端 Long，经 NumberSerializer 超长转字符串，App 端 DTO 一律按 String 解析（§5.1）；
 * emoji/color 原样字符串透传（端上 colorFromHex 映射并兜底，铁律⑥）。
 */
@Schema(description = "用户 App - 心情选项 Response VO，couple 私有字典（双方共享一份）")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppMoodOptionRespVO {

    @Schema(description = "心情选项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "心情名", requiredMode = Schema.RequiredMode.REQUIRED, example = "元气满满")
    private String label;

    @Schema(description = "表情（utf8mb4 文本原样透传）", example = "😄")
    private String emoji;

    @Schema(description = "颜色 hex（原样透传）", example = "#FFB93C")
    private String color;

    @Schema(description = "显示顺序（播种行在前、用户新增落尾，端上按回包顺序渲染即可不消费此值）", example = "1")
    private Integer sort;

}
