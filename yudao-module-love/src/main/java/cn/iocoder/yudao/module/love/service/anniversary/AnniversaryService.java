package cn.iocoder.yudao.module.love.service.anniversary;

import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryRespVO;

import java.util.List;

/**
 * 纪念日 Service 接口（M5）
 *
 * 归属 = couple 共享 + owner 仅记录创建人（INTEGRATION-DESIGN.md §5.2 anniversary 组 / §12.4 预留）：
 * 列表对 couple 双方可见，所有按 id 写操作在 service 层强制 couple 归属校验（防 IDOR，§5.2 表头强约束）。
 * 全部方法入口做停用账号收口（§6.5），未绑定 couple 抛 COUPLE_NOT_EXISTS（1_003_004_003）。
 *
 * @author 芋道源码
 */
public interface AnniversaryService {

    /**
     * 获得当前 couple 的全量纪念日列表（§5.2 GET /love/anniversary/list）
     *
     * 排序 anniv_date asc, id asc（mapper 收口），端上整体覆盖 AnniversaryStore（§7.4），
     * admin 删除的端上传播同样走全量覆盖，天然生效（§5.3）
     *
     * @return 纪念日列表
     */
    List<AppAnniversaryRespVO> getAnniversaryList();

    /**
     * 创建纪念日：owner_id = 当前用户，couple_id 取当前用户所在 couple
     *
     * repeatType 值域收口 1/2（其余打全局 BAD_REQUEST）；annivDate 经 LocalDate.parse
     * 二次校验日历合法性（1_003_006_002）；emoji 只存字符串不校验取值（客户端字典，铁律⑥）
     *
     * @param reqVO 创建请求（title/annivDate/repeatType/emoji）
     * @return 创建后的纪念日（含服务端 id / ownerId / createTime，端上回填 AnniversaryStore）
     */
    AppAnniversaryRespVO createAnniversary(AppAnniversaryCreateReqVO reqVO);

    /**
     * 删除纪念日（body 传 id）
     *
     * LoveAnniversaryDO 继承 BaseDO 走逻辑删除（与 love_couple 物理删除路线相反，D-11：
     * couple 解绑后数据保留）；先查再校验归属，跨 couple 抛 ANNIVERSARY_NO_PERMISSION（1_003_006_001，防 IDOR）
     *
     * @param reqVO 删除请求（id）
     */
    void deleteAnniversary(AppAnniversaryIdReqVO reqVO);

}
