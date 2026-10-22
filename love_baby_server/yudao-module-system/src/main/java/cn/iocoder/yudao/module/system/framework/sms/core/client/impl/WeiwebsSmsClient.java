package cn.iocoder.yudao.module.system.framework.sms.core.client.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.sms.SmsTemplateMapper;
import cn.iocoder.yudao.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO;
import cn.iocoder.yudao.module.system.framework.sms.core.client.dto.SmsSendRespDTO;
import cn.iocoder.yudao.module.system.framework.sms.core.client.dto.SmsTemplateRespDTO;
import cn.iocoder.yudao.module.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum;
import cn.iocoder.yudao.module.system.framework.sms.core.property.SmsChannelProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网建(weiwebs)短信客户端实现类
 *
 * 协议对齐官网示例 http.jar（HttpSender）的默认行为：POST form-urlencoded + UTF-8 +
 * 密码时间戳 MD5 加密（encrypt=true 语义：pswd = MD5(account + pswd + ts)，ts 单独传参）。
 * 账号/密码/签名与 buyer-nest third-party-service、buyer global.php 同源同值。
 *
 * 配置映射（渠道行）：apiKey = 网建账号（LoginName），apiSecret = 网建密码，
 * signature = 已审核签名；callbackUrl 留空 = 默认生产域名，非空 = 覆盖为自定义网关
 * 地址（联调 mock / 中转用）。
 *
 * 与阿里云等客户端「模板预注册到厂商」不同，网建是内容免审通道：发送时按 apiTemplateId
 * 查本系统 sms_template 表取文案，渲染 {参数} 后前置【签名】发送——文案在管理后台
 * 可改，即时生效。
 *
 * @author 不准偷吃贡品
 */
@Slf4j
public class WeiwebsSmsClient extends AbstractSmsClient {

    /**
     * 网建生产域名（callbackUrl 留空时使用）
     */
    private static final String DEFAULT_BASE_URL = "http://www.weiwebs.cn";

    /**
     * 发送路径（网建 HttpSendSM）
     */
    private static final String SEND_PATH = "/msg/HttpSendSM";

    /**
     * HTTP 超时（毫秒）
     */
    private static final int TIMEOUT_MS = 10_000;

    /**
     * 密码是否使用时间戳 MD5 加密。官网示例默认 true，但实测本账号该模式返回 102（密码错误）
     * ——账号未开通时间戳鉴权；nest 生产同款明文模式鉴权通过，故恒走明文
     */
    private static final boolean ENCRYPT = false;

    /**
     * 时间戳格式（官网示例同款）
     */
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public WeiwebsSmsClient(SmsChannelProperties properties) {
        super(properties);
    }

    @Override
    public SmsSendRespDTO sendSms(Long sendLogId, String mobile, String apiTemplateId,
                                  List<KeyValue<String, Object>> templateParams) throws Throwable {
        // 1. 渲染内容：文案查 sms_template 表（按 apiTemplateId），替换 {参数} 后前置【签名】
        String content = renderContent(apiTemplateId, templateParams);
        if (content == null) {
            return new SmsSendRespDTO().setSuccess(false).setApiCode("-1")
                    .setApiMsg("未知短信模板：" + apiTemplateId);
        }
        String fullMsg = "【" + properties.getSignature() + "】" + content;

        // 2. 组装表单：参数名与官网示例一致（product/extno 官方示例传空串）
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("account", properties.getApiKey());
        if (ENCRYPT) {
            String ts = LocalDateTime.now().format(TS_FORMAT);
            form.put("ts", ts);
            form.put("pswd", SecureUtil.md5(properties.getApiKey() + properties.getApiSecret() + ts));
        } else {
            form.put("pswd", properties.getApiSecret());
        }
        form.put("mobile", mobile);
        form.put("msg", fullMsg);
        form.put("needstatus", "true");
        form.put("product", "");
        form.put("extno", "");
        form.put("resptype", "json");

        // 3. 执行请求：POST form-urlencoded（UTF-8），对齐官网 HttpSender 的 PostMethod 行为
        String responseText = HttpRequest.post(getSendUrl())
                .form(form)
                .timeout(TIMEOUT_MS)
                .execute().body();

        // 4. 解析回包：{msgid, result, ts}（成功时含 msgid；nest 生产日志同款），成功判定 result === '0'
        Map<?, ?> raw = JsonUtils.parseObject(responseText, Map.class);
        String result = String.valueOf(raw.get("result"));
        boolean success = "0".equals(result);
        return new SmsSendRespDTO()
                .setSuccess(success)
                .setApiCode(result)
                .setApiMsg(success ? "发送成功" : "发送失败（result=" + result + "）")
                .setSerialNo(StrUtil.blankToDefault((String) raw.get("msgid"), IdUtil.fastSimpleUUID()));
    }

    @Override
    public List<SmsReceiveRespDTO> parseSmsReceiveStatus(String text) throws Throwable {
        throw new UnsupportedOperationException("网建短信渠道，暂时不支持解析接收回执！");
    }

    @Override
    public SmsTemplateRespDTO getSmsTemplate(String apiTemplateId) throws Throwable {
        // 文案即本系统 sms_template 表中的数据（发送时也是查它渲染），视为已审核
        SmsTemplateDO template = getTemplateByApiTemplateId(apiTemplateId);
        return new SmsTemplateRespDTO()
                .setId(apiTemplateId)
                .setContent(template != null ? template.getContent() : "")
                .setAuditStatus(SmsTemplateAuditStatusEnum.SUCCESS.getStatus());
    }

    /**
     * 渲染模板内容，模板不存在返回 null
     *
     * 工厂在 Spring 上下文就绪前就会创建占位客户端，所以这里运行时才取 Mapper
     */
    private String renderContent(String apiTemplateId, List<KeyValue<String, Object>> templateParams) {
        SmsTemplateDO template = getTemplateByApiTemplateId(apiTemplateId);
        if (template == null) {
            return null;
        }
        String content = template.getContent();
        for (KeyValue<String, Object> param : templateParams) {
            content = content.replace("{" + param.getKey() + "}", String.valueOf(param.getValue()));
        }
        return content;
    }

    private SmsTemplateDO getTemplateByApiTemplateId(String apiTemplateId) {
        return SpringUtils.getBean(SmsTemplateMapper.class)
                .selectOne(SmsTemplateDO::getApiTemplateId, apiTemplateId);
    }

    /**
     * 发送地址：callbackUrl 留空 = 生产域名，非空 = 自定义网关（联调 mock / 中转用）
     */
    private String getSendUrl() {
        return StrUtil.blankToDefault(properties.getCallbackUrl(), DEFAULT_BASE_URL) + SEND_PATH;
    }

}
