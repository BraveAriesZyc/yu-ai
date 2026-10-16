package cn.iocoder.yudao.module.love.dal.mysql.couple;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCouplePageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.couple.vo.LoveCoupleRespVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 情侣关系 Mapper
 *
 * 注意：LoveCoupleDO 未启用 @TableLogic（物理删除路线，见 DO 类注释），
 * 因此 selectOne / deleteById 均直接作用于物理行，deleteById 即物理 DELETE。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveCoupleMapper extends BaseMapperX<LoveCoupleDO> {

    default LoveCoupleDO selectByUserIdA(Long userIdA) {
        return selectOne(LoveCoupleDO::getUserAId, userIdA);
    }

    default LoveCoupleDO selectByUserIdB(Long userIdB) {
        return selectOne(LoveCoupleDO::getUserBId, userIdB);
    }

    /**
     * 按任一方 userId 查询其所在的情侣关系
     *
     * uk_user_a / uk_user_b 唯一键保证一方同时最多处于一条关系，selectOne 安全；
     * 行存在即处于绑定态（解绑为物理删除，无逻辑删除残留），查询 / 解绑 / 绑定校验共用
     */
    default LoveCoupleDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<LoveCoupleDO>()
                .and(w -> w.eq(LoveCoupleDO::getUserAId, userId)
                        .or()
                        .eq(LoveCoupleDO::getUserBId, userId)));
    }

    /**
     * 按任一方 userId 查询其所在的情侣关系（FOR UPDATE 当前读，须在事务内调用）
     *
     * bind 锁内校验专用：RR 隔离下普通 selectByUserId 是一致性读，快照在事务首个 selectById 处
     * 建立、早于加锁，看不到加锁前并发事务已提交的 couple 行；A/B 互绑产出 (user_a=B,user_b=A)
     * 与 (user_a=A,user_b=B) 两行、撞不到 uk_user_a/uk_user_b 任何一个，此当前读是互绑竞态的
     * 唯一防线（唯一键仅终审同邀请方多受邀方 / 同一受邀方多邀请方两类）
     */
    default LoveCoupleDO selectByUserIdForUpdate(Long userId) {
        return selectOneForUpdate(new LambdaQueryWrapper<LoveCoupleDO>()
                .and(w -> w.eq(LoveCoupleDO::getUserAId, userId)
                        .or()
                        .eq(LoveCoupleDO::getUserBId, userId)));
    }

    /**
     * 管理后台 couple 分页：LEFT JOIN love_user 带出双方昵称（INTEGRATION-DESIGN.md §5.3 GET /love/couple/page）
     *
     * 1. 筛选：status 精确 + 双方昵称模糊（SQL 内直接生效）+ createTime 段；
     * 2. 结果直接映射 {@link LoveCoupleRespVO}（含 userANickname / userBNickname），
     *    不为 join 结果另建 dal DTO；MyBatis 依据方法返回值 IPage&lt;LoveCoupleRespVO&gt; 的泛型推断
     *    resultType，下划线列别名自动驼峰映射（mapUnderscoreToCamelCase）；
     * 3. ua / ub 的 ON 条件带 deleted = 0：love_user 走逻辑删除，不把已删用户昵称 join 出来
     *    （正常情况下 admin 删用户会级联物理解绑 couple，此处双保险）；
     * 4. 分页：IPage 入参由 PaginationInnerInterceptor 自动拼 count + limit；
     * 5. 邀请码存于 love_user（invite_code / invite_expire_time），不作本页筛选字段（§5.3）。
     */
    @Select("<script>" +
            "SELECT c.id, c.user_a_id, c.user_b_id, c.status, c.bind_time, c.create_time," +
            " ua.nickname AS user_a_nickname, ub.nickname AS user_b_nickname" +
            " FROM love_couple c" +
            " LEFT JOIN love_user ua ON ua.id = c.user_a_id AND ua.deleted = 0" +
            " LEFT JOIN love_user ub ON ub.id = c.user_b_id AND ub.deleted = 0" +
            " <where>" +
            "  <if test='reqVO.status != null'> AND c.status = #{reqVO.status}</if>" +
            "  <if test=\"reqVO.userANickname != null and reqVO.userANickname != ''\">" +
            "   AND ua.nickname LIKE CONCAT('%', #{reqVO.userANickname}, '%')</if>" +
            "  <if test=\"reqVO.userBNickname != null and reqVO.userBNickname != ''\">" +
            "   AND ub.nickname LIKE CONCAT('%', #{reqVO.userBNickname}, '%')</if>" +
            "  <if test='reqVO.createTime != null and reqVO.createTime.length > 0'>" +
            "   AND c.create_time &gt;= #{reqVO.createTime[0]}</if>" +
            "  <if test='reqVO.createTime != null and reqVO.createTime.length > 1'>" +
            "   AND c.create_time &lt; #{reqVO.createTime[1]}</if>" +
            " </where>" +
            " ORDER BY c.id DESC" +
            "</script>")
    IPage<LoveCoupleRespVO> selectPageWithUser(IPage<LoveCoupleRespVO> page,
                                               @Param("reqVO") LoveCouplePageReqVO reqVO);

}
