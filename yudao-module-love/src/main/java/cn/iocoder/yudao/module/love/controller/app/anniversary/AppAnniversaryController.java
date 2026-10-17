package cn.iocoder.yudao.module.love.controller.app.anniversary;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.anniversary.vo.AppAnniversaryRespVO;
import cn.iocoder.yudao.module.love.service.anniversary.AnniversaryService;
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
 * 纪念日的 App 端接口：全部需登录（无 @PermitAll，走 anyRequest().authenticated()），
 * 当前用户/couple 由 service 层经 SecurityFrameworkUtils 反查，不接受客户端传入 coupleId / userId；
 * 按 id 写操作在 service 层强制 couple 归属校验（跨 couple 返回 1_003_006_001，防 IDOR，§5.2）。
 *
 * 写端点统一 POST 而非 RESTful 的 PUT/DELETE（M2 实施修正，§5.2 user 组注记）：
 * Kuikly core 2.29 NetworkModule 仅支持 GET/POST（反编译 AAR 核实），delete 的 body 传 id 同理；
 * 无 update 端点（D-17：删了重加， anniversary 无状态位可改）。
 */
@Tag(name = "用户 App - 纪念日")
@RestController
@RequestMapping("/love/anniversary")
@Validated
public class AppAnniversaryController {

    @Resource
    private AnniversaryService anniversaryService;

    @GetMapping("/list")
    @Operation(summary = "获得当前 couple 的全量纪念日列表",
            description = "按 anniv_date asc, id asc 排序，端上整体覆盖 AnniversaryStore（§7.4）；" +
                    "展示序（倒数升序 + 已过沉底）由端上纯函数重排（D-17）；未绑定 couple 返回 1_003_004_003")
    public CommonResult<List<AppAnniversaryRespVO>> getAnniversaryList() {
        return success(anniversaryService.getAnniversaryList());
    }

    @PostMapping("/create")
    @Operation(summary = "创建纪念日",
            description = "owner_id = 当前用户（双方均可见可操作）；repeatType 值域 1/2，其余打全局 BAD_REQUEST；" +
                    "annivDate 经 @Pattern + LocalDate.parse 双重校验（2026-02-30 类打 1_003_006_002）；" +
                    "回包含服务端 id/ownerId/createTime，端上回填 AnniversaryStore")
    public CommonResult<AppAnniversaryRespVO> createAnniversary(@RequestBody @Valid AppAnniversaryCreateReqVO reqVO) {
        return success(anniversaryService.createAnniversary(reqVO));
    }

    @PostMapping("/delete")
    @Operation(summary = "删除纪念日（body 传 id）",
            description = "LoveAnniversaryDO 继承 BaseDO 走逻辑删除；admin 删除的端上传播靠 list 全量覆盖，天然生效（§5.3）；" +
                    "先查再校验 couple 归属，跨 couple 返回 1_003_006_001（防 IDOR）")
    public CommonResult<Boolean> deleteAnniversary(@RequestBody @Valid AppAnniversaryIdReqVO reqVO) {
        anniversaryService.deleteAnniversary(reqVO);
        return success(true);
    }

}
