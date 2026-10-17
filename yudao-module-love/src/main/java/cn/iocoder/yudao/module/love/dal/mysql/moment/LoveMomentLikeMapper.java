package cn.iocoder.yudao.module.love.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.moment.LoveMomentLikeDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 相册瞬间点赞 Mapper（M15）
 *
 * 全显式 SQL（D-20b 口径）：
 * - unlike 必须物理 DELETE：uk_moment_user 与逻辑删除冲突（墓碑行会永久占 uk 位，重新点赞
 *   撞 1062），显式 SQL 不经 MP @TableLogic 注入，天然物理删；couple_id 条件 = 归属第二道闸
 *   （WishMapper.markDone 同款注释口径）；
 * - 本表 unlike 物理删 → deleted 恒 0，故查询不带 deleted 过滤（WishMapper 注解 SQL 同款）；
 * - 计数/liked 组装均为批量（一页瞬间一次 SQL），couple 恒 2 人、行数 ≤ 2×页大小，
 *   Java 侧 groupingBy 即可，无聚合 SQL。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveMomentLikeMapper extends BaseMapperX<LoveMomentLikeDO> {

    /**
     * 点赞（幂等）
     *
     * INSERT IGNORE 走 uk_moment_user 吸收并发重复（重复点赞 rows=0，幂等收敛不打错）；
     * 只插业务三列，BaseDO 五列全走表默认值（行物理删，审计无意义——MP 不会为注解 SQL
     * 补 creator/create_time 填充，靠 DB DEFAULT）。
     *
     * @return 影响行数（0 = 已赞过，幂等）
     */
    @Insert("INSERT IGNORE INTO love_moment_like (couple_id, moment_id, user_id) "
            + "VALUES (#{coupleId}, #{momentId}, #{userId})")
    int insertIgnore(@Param("coupleId") Long coupleId, @Param("momentId") Long momentId,
                     @Param("userId") Long userId);

    /**
     * 取消点赞（物理 DELETE，幂等）
     *
     * @return 影响行数（0 = 本就未赞，幂等）
     */
    @Delete("DELETE FROM love_moment_like WHERE moment_id = #{momentId} AND user_id = #{userId} "
            + "AND couple_id = #{coupleId}")
    int deleteByMomentIdAndUserId(@Param("momentId") Long momentId, @Param("userId") Long userId,
                                  @Param("coupleId") Long coupleId);

    /**
     * 批量取一页瞬间的全部赞行（likeCount 组装源，Java 侧 groupingBy 计数）
     *
     * 调用方保证 momentIds 非空（getMomentList 空 moments 早退，避免 IN () 语法错）
     */
    @Select("<script>SELECT * FROM love_moment_like WHERE moment_id IN "
            + "<foreach collection='momentIds' item='mid' open='(' separator=',' close=')'>#{mid}</foreach>"
            + "</script>")
    List<LoveMomentLikeDO> selectListByMomentIds(@Param("momentIds") List<Long> momentIds);

    /**
     * 批量取「我赞过的」一页瞬间（liked 组装源）
     *
     * 调用方保证 momentIds 非空（同上）
     */
    @Select("<script>SELECT * FROM love_moment_like WHERE user_id = #{userId} AND moment_id IN "
            + "<foreach collection='momentIds' item='mid' open='(' separator=',' close=')'>#{mid}</foreach>"
            + "</script>")
    List<LoveMomentLikeDO> selectListByMomentIdsAndUserId(@Param("momentIds") List<Long> momentIds,
                                                          @Param("userId") Long userId);

}
