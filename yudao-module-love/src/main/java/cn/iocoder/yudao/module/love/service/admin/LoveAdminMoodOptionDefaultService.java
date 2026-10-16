package cn.iocoder.yudao.module.love.service.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultPageReqVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultRespVO;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodOptionDefaultSaveReqVO;

/**
 * 心情默认选项模板 Service（admin 通道，M3.5 / D-14 / §5.3）
 *
 * 用法：B 端「心情默认选项」管理页四按钮（query/create/update/delete）的落点；
 * 模板改动只影响【之后的新绑定】（播种即复制），不回灌已绑定情侣（显式声明）。
 *
 * ⚠ 命名铁律：实现类与 @Resource 注入字段名都必须带 LoveAdmin 前缀——@Resource 按字段名注入，
 * 与 system 模块同名 bean 撞名会 BeanNotOfRequiredTypeException（M3 wave2 实测教训）。
 *
 * @author 芋道源码
 */
public interface LoveAdminMoodOptionDefaultService {

    /**
     * 分页查默认模板（label 模糊 / status 精确，排序 sort asc, id asc——效果序即管理序）
     */
    PageResult<LoveMoodOptionDefaultRespVO> getMoodOptionDefaultPage(LoveMoodOptionDefaultPageReqVO pageReqVO);

    /**
     * 新增默认模板（status 未传时落库默认启用；sort 必填）
     *
     * @return 新建行（含服务端 id 与 createTime，B 端无需二次刷新）
     */
    LoveMoodOptionDefaultRespVO createMoodOptionDefault(LoveMoodOptionDefaultSaveReqVO reqVO);

    /**
     * 更新默认模板（按 id 定位，不存在抛 1_003_002_004；null 字段保持原值——MP updateById 忽略 null）
     */
    void updateMoodOptionDefault(LoveMoodOptionDefaultSaveReqVO reqVO);

    /**
     * 删除默认模板（逻辑删除；不影响任何已绑定 couple 的私有字典——播种即复制，D-14）
     */
    void deleteMoodOptionDefault(Long id);

}
