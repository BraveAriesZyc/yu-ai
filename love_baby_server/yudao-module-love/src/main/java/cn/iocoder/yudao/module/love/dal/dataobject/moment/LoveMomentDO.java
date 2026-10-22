package cn.iocoder.yudao.module.love.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 相册瞬间表（love_moment，INTEGRATION-DESIGN.md §12.4 预留落位，M9）
 *
 * 用法（INTEGRATION-DESIGN.md §5.2 moment 组）：
 * 1. 图 + 可选一句话：image_url 为 infra 文件服务上传返回的完整 URL（媒资复用 infra 文件服务，
 *    本表只存 url 字符串不建存储，§12.4 落位表口径）；text 配文可空（DEFAULT ''），≤64 字；
 * 2. 归属 = couple 共享：列表按 couple_id 过滤返回双方瞬间；每条瞬间只挂发布者 user_id；
 * 3. 删除仅上传者本人（1_003_008_001 用户维度校验，防误删伴侣的瞬间）；admin 兜底可删任何；
 *    无 update 端点（删了重发，D-17 口径）。
 *
 * 依赖：继承 BaseDO 走【逻辑删除】（deleted + @TableLogic），与 love_checkin 同路线；
 * 逻辑删后 love_moment 无 uk（同人可发多条），不存在「删行占 uk 位」问题，
 * 用户重新发布恒走 insert 新行；图片本体留 infra_file 不级联删（D-12 残留策略同款哲学）。
 *
 * 功能：字段名与列名 snake_case 严格驼峰对应（coupleId→couple_id、imageUrl→image_url）。
 *
 * @author 芋道源码
 */
@TableName("love_moment")
@KeySequence("love_moment_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@TenantIgnore // 双保险：租户配置漂移被误开时，跳过 love_moment 的 tenant_id 过滤（LoveUserDO 同款，§10 M-1）
public class LoveMomentDO extends BaseDO {

    /**
     * 瞬间编号
     */
    @TableId
    private Long id;
    /**
     * 情侣关系编号
     *
     * couple 共享，列表按此过滤；idx_couple_id 普通索引；解绑重绑后旧瞬间留旧 couple_id（历史归属不刷新，
     * 照 love_todo 归属口径）
     */
    private Long coupleId;
    /**
     * 发布用户编号
     *
     * 仅上传者本人可删（1_003_008_001），伴侣端展示「TA」标识
     */
    private Long userId;
    /**
     * 图片完整 URL（首图）
     *
     * infra 文件服务（POST /app-api/infra/file/upload）返回的 URL，@PermitAll 匿名可读，
     * 端上 <img>/painter 直连；M15 起语义收敛为「首图冗余」（恒 = imageUrls 第一个元素），
     * 保留本列使 admin LoveMomentRespVO / web 管理页零改动（多图瞬间 admin 显示首图）
     */
    private String imageUrl;
    /**
     * 多图 URL JSON 数组字符串（M15）
     *
     * 如 ["u1","u2"]（≤9 元素）；service 落库前已归一（老版 App 只传 imageUrl → 归一为
     * [imageUrl]），恒非空；image_url 恒为首图冗余。读取走 service 层 parseImageUrls
     * （JSON 损坏/空一律兜底 [imageUrl]，与存量单图同一出口），不直接出参
     */
    private String imageUrls;
    /**
     * 首图压缩后宽（px，M24）
     *
     * 端上 Glide 压缩（centerInside + EXIF 自动矫正）后 Bitmap 的实际宽，随发布请求透传落库；
     * 0 = 未知（存量行 / 老版 App）→ 端上瀑布流卡片降级 140dp 定高；不校验范围
     */
    private Integer imageWidth;
    /**
     * 首图压缩后高（px，M24）
     *
     * 语义同 imageWidth（首图 = imageUrls[0] = image_url 冗余列所指那张）
     */
    private Integer imageHeight;
    /**
     * 配文（可选，≤64 字，DEFAULT ''）
     */
    private String text;

}
