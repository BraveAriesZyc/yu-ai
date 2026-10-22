-- ----------------------------------------------------------------------------
-- love-server-config.sql —— 服务器新库上线配置：短信验证码（网建）+ 阿里云 OSS（2026-10-19）
--
-- 适用：全新 yudao 库。执行顺序 = 官方 ruoyi-vue-pro.sql（含 QRTZ_ 表）→ love.sql（业务表）→ 本脚本。
-- 原则：DELETE + INSERT 显式 id 整行覆盖，任意次数重放安全；值为 dev 库实况（2026-10-19 终验通过态）。
-- 依赖：① 运行 jar 必须包含 WeiwebsSmsClient（SmsChannelEnum.WEIWEBS + factory 注册），
--         旧 jar 无此类时该渠道发码报错——用最新代码重打包；
--       ② 验证码区间 9999→6 位随机是 YAML 配置（application-prod.yaml / application-dev.yaml
--         已覆盖 yudao.sms-code.begin-code/end-code），无需 SQL；
--       ③ FileConfigService 有本地客户端缓存，执行完本脚本建议重启服务再验证。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- ① 短信验证码：网建直连渠道 + 报备模板
--    ⚠ 网建按报备模板匹配内容，模板 content 严禁改动——改文案先去网建后台报备
--      （本账号报备文案 =「验证码：{code}，五分钟内有效。」，4/6 位验证码都过）。
-- ============================================================================

-- 短信渠道：WEIWEBS 网建直连（api_key/api_secret = 网建账号，鉴权用明文 pswd，勿加密）
DELETE FROM `system_sms_channel` WHERE `code` = 'WEIWEBS';
INSERT INTO `system_sms_channel` (`id`, `signature`, `code`, `status`, `remark`, `api_key`, `api_secret`, `callback_url`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12, '品鑫佳国内贸易', 'WEIWEBS', 0, '网建直连渠道（协议对齐 buyer-nest third-party-service，同账号同签名）', 'MXT802921', 'Mxc802921', NULL, '1', NOW(), '1', NOW(), b'0');

-- 短信模板：user-sms-login（App 注册/短信登录/重置密码 scene=1 共用本模板，channel_id 对应上行 id=12）
DELETE FROM `system_sms_template` WHERE `code` = 'user-sms-login';
INSERT INTO `system_sms_template` (`id`, `type`, `status`, `code`, `name`, `content`, `params`, `remark`, `api_template_id`, `channel_id`, `channel_code`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(8, 1, 0, 'user-sms-login', '前台用户短信登录', '验证码：{code}，五分钟内有效。', '["code"]', '', 'user-sms-login', 12, 'WEIWEBS', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- ② 阿里云 OSS 文件存储（infra_file_config，storage=20 S3 协议）
--    值 = dev 库实况（id=25，2026-10-19 16:28 配置并切 master）。⚠ endpoint 见文末「自测」注记。
--    语义：enablePublicAccess=true 要求 OSS bucket 为【公共读】ACL，否则文件 URL 403，
--          需改 bucket 权限或把 enablePublicAccess 改 false 走 24h 签名 URL。
-- ============================================================================

-- 先摘掉全部 master 标记（官方基线默认 master 是 id=22 七牛示例行）
UPDATE `infra_file_config` SET `master` = b'0' WHERE `deleted` = b'0';
DELETE FROM `infra_file_config` WHERE `id` = 25;
INSERT INTO `infra_file_config` (`id`, `name`, `storage`, `remark`, `master`, `config`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(25, '阿里云存储', 20, '阿里云 OSS（值迁自 dev 库，love-baby 新库上线）', b'1',
 '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig","accessKey":"LTAI5t6F98KiRTLeYsja4WwN","accessSecret":"CfHfLGGnagdN0UyUzqVbGCVOeuqTp0","bucket":"shattered-dream","domain":"","enablePathStyleAccess":false,"enablePublicAccess":true,"endpoint":"oss-cn-beijing.aliyuncs.com/love_baby","region":null}',
 '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- ③ 渠道编码字典补行（admin 列表「渠道」列用 system_sms_channel_code 字典渲染，
--    官方基线无 WEIWEBS 行 → 列表显示空、编辑表单下拉无此项；纯显示层，不影响发码）
-- ============================================================================
DELETE FROM `system_dict_data` WHERE `dict_type` = 'system_sms_channel_code' AND `value` = 'WEIWEBS';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5, '网建', 'WEIWEBS', 'system_sms_channel_code', 0, 'primary', '', '网建短信（WeiwebsSmsClient 自定义渠道）', '1', NOW(), '1', NOW(), b'0');

-- ============================================================================
-- 验证（肉眼核对即可）
-- ① 应各 1 行：channel=WEIWEBS、template=user-sms-login 且 channel_code=WEIWEBS；
-- ② 应恰 1 行 master=1 且 id=25。
-- ============================================================================
SELECT id, signature, code, status, deleted FROM system_sms_channel WHERE code = 'WEIWEBS';
SELECT id, code, content, channel_id, channel_code, status, deleted FROM system_sms_template WHERE code = 'user-sms-login';
SELECT id, name, storage, master, deleted FROM infra_file_config WHERE master = b'1' AND deleted = b'0';
SELECT id, label, value, status, deleted FROM system_dict_data WHERE dict_type = 'system_sms_channel_code' AND value = 'WEIWEBS';

-- ----------------------------------------------------------------------------
-- ⚠ endpoint 注记（上线前自测一次）：
-- dev 行 endpoint = "oss-cn-beijing.aliyuncs.com/love_baby"（带 /love_baby 路径后缀）。
-- 标准 OSS 用法 endpoint 应为纯域名（oss-cn-beijing.aliyuncs.com），bucket 单独放 bucket 字段；
-- 带路径后缀不是受支持姿势：S3FileClient.buildDomain() 会拼出
--   https://shattered-dream.oss-cn-beijing.aliyuncs.com/love_baby/{文件路径}
-- 上传对象 key 却不含 love_baby 前缀，两者是否对得上取决于 SDK 对 override path 的处理——
-- 【自测】：后台「基础设施 → 文件管理」上传一张图，直接打开回显 URL。
--   能打开 = 保持现状不动；404/报错 = 把 endpoint 改为纯域名
--   "oss-cn-beijing.aliyuncs.com" 再传一次验证。
--   （若本意是想让文件收进 love_baby/ 前缀，正路是 OSS 控制台单建一个专用 bucket，
--     或改服务端上传 path 生成逻辑，不在 endpoint 里塞路径。）
-- ----------------------------------------------------------------------------
