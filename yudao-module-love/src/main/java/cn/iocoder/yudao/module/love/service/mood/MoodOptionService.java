package cn.iocoder.yudao.module.love.service.mood;

import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionCreateReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionIdReqVO;
import cn.iocoder.yudao.module.love.controller.app.mood.vo.AppMoodOptionRespVO;

import java.util.List;

/**
 * 心情选项字典 Service（couple 私有，M3.5 / D-14）
 *
 * 用法：app 侧三个端点（list/create/delete）的落点 + 绑定播种钩子 seedDefaults（CoupleServiceImpl.bind
 * 事务内调用）；全部以「当前登录用户」反查 couple（防 IDOR），不接受客户端传入 coupleId。
 *
 * 事件：seedDefaults 参与调用方（bind）事务——本接口不另标 @Transactional，
 * 播种失败随绑定整体回滚（D-8 事务语义，不单独吞错）。
 *
 * @author 芋道源码
 */
public interface MoodOptionService {

    /**
     * 查当前 couple 的心情选项列表（sort asc, id asc，端上整表覆盖 MoodStore.moodOptions）
     */
    List<AppMoodOptionRespVO> getMoodOptionList();

    /**
     * 新增心情选项（用户新增恒 sort=999 落尾）
     *
     * 校验：绑定态 / couple 内重名（1_003_002_003）/ 上限 20（1_003_002_002）
     *
     * @return 新建选项（含服务端 id，端上按 String 解析）
     */
    AppMoodOptionRespVO createMoodOption(AppMoodOptionCreateReqVO reqVO);

    /**
     * 删除心情选项（逻辑删除；历史月历靠 love_mood_record 快照列渲染，不受影响）
     *
     * 校验：归属当前 couple（跨 couple 按 1_003_002_001 拒绝，IDOR 收口同 todo 组）
     */
    void deleteMoodOption(AppMoodOptionIdReqVO reqVO);

    /**
     * 绑定播种：复制全部启用模板行（love_mood_option_default，status=启用）到指定 couple
     *
     * 模板空 → 字典空（端上有空态自愈路径，不报错）；参与 bind 事务，失败整体回滚
     */
    void seedDefaults(Long coupleId);

}
