package cn.iocoder.yudao.module.love.controller.app.moment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

/**
 * 相册瞬间 - 发布瞬间的 App 端请求 VO（M9；M15 多图扩展）
 *
 * imageUrl = infra 文件服务上传返回的完整 URL（端上先 pickAndUpload 拿到 url 再发本端点）；
 * M15 起新版 App 传 imageUrls（≤9 张，可空）——服务端归一：imageUrls 空 → [imageUrl]
 * （老版 App 只传单图的兼容出口）；image_url 列恒落首图冗余（D-20a）。
 * text 配文可空（空串按无配文处理）。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 发布瞬间 Request VO")
@Data
public class AppMomentCreateReqVO {

    @Schema(description = "首图完整 URL（infra 文件服务上传返回；imageUrls 非空时恒为其第一个元素）", requiredMode = Schema.RequiredMode.REQUIRED, example = "http://192.168.1.4:48080/admin-api/infra/file/4/get/xxx.jpg")
    @NotBlank(message = "图片不能为空")
    @Length(max = 512, message = "图片地址过长")
    private String imageUrl;

    @Schema(description = "多图完整 URL 列表（M15，≤9 张，可空；老版 App 不传）", example = "[\"http://192.168.1.4:48080/admin-api/infra/file/4/get/a.jpg\"]")
    @Size(max = 9, message = "最多发布 9 张图片")
    private List<String> imageUrls;

    @Schema(description = "配文（可选，≤64 字）", example = "今天的晚霞")
    @Length(max = 64, message = "配文不能超过 64 字")
    private String text;

    @Schema(description = "首图压缩后宽（px，M24，EXIF 矫正；可空/0 = 未知，端上瀑布流降级定高）", example = "1080")
    private Integer imageWidth;

    @Schema(description = "首图压缩后高（px，M24，EXIF 矫正；可空/0 = 未知）", example = "810")
    private Integer imageHeight;

}
