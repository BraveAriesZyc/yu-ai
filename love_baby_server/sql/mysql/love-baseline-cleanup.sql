-- ----------------------------------------------------------------------------
-- love-baseline-cleanup.sql — love.sql 基线重做前的数据清理(2026-10-19)
--
-- 目的:清掉联调/冒烟产生的业务数据、测试上传文件与日志噪声,使随后导出的全量
--       dump = 「表结构 + yudao 框架基线数据 + love 种子数据」,可整库重放做基线。
-- 原则:只动 yudao(system_/infra_)与 love_ 系;同库共存的买家系约 106 张表
--       (admin/salesman/task/pricing_/system_bank 等)与 QRTZ_/yudao_demo 一概不碰。
-- 用法:任意次数重放均安全(纯 DELETE/UPDATE,无 DDL);执行完即可导出新基线。
-- 导出范围(73 张):love_* 全部 14 + ruoyi-vue-pro.sql 官方 48 + QRTZ_* 11;
--       排除买家系 106 张——注意其中 25 张顶着 system_ 前缀(system_bank/
--       system_msm_config/system_withdrawal_config 等),Navicat 按前缀勾选会误入。
-- ----------------------------------------------------------------------------

-- ---------- ① love 业务数据(user/couple/message/todo/mood 当前已为 0,其余为孤儿残留) ----------
DELETE FROM love_user;
DELETE FROM love_couple;
DELETE FROM love_todo;
DELETE FROM love_mood_record;
DELETE FROM love_mood_option;      -- 情侣私有字典,新绑定事务自动从 default 重播种
DELETE FROM love_message;
DELETE FROM love_anniversary;
DELETE FROM love_checkin;
DELETE FROM love_moment;
DELETE FROM love_moment_like;
DELETE FROM love_wish;

-- ---------- ② 种子表只清冒烟残留,种子本体保留 ----------
DELETE FROM love_inspiration WHERE content LIKE '%冒烟测试%';   -- M17 冒烟残留(49=48 种子+1)

-- ---------- ③ 测试上传文件(37 个:元数据 infra_file + 二进制 infra_file_content) ----------
DELETE FROM infra_file;
DELETE FROM infra_file_content;

-- ---------- ④ 短信/日志/令牌噪声(基线不带;短信渠道/模板/字典保留,新库可直接发码) ----------
DELETE FROM system_sms_code;
DELETE FROM system_sms_log;
DELETE FROM system_login_log;
DELETE FROM system_operate_log;
DELETE FROM infra_api_error_log;
DELETE FROM infra_api_access_log;
DELETE FROM infra_job_log;
DELETE FROM system_oauth2_access_token;   -- 会踢掉后台当前登录态,重登即可
DELETE FROM system_oauth2_refresh_token;
DELETE FROM system_oauth2_code;

-- ---------- ⑤ 菜单 icon 修复:「心情默认选项」原 ep:mood 非 Element Plus 有效图标名,
--               渲染为空白;换 fa:smile-o(笑脸,与父目录 fa:heart 同 collection) ----------
UPDATE system_menu SET icon = 'fa:smile-o'
WHERE component_name = 'LoveMoodOptionDefault' AND deleted = 0 AND icon = 'ep:mood';

-- ---------- 验证(应全为 0;inspiration 应为 48;菜单 icon 应已更新) ----------
SELECT 'love_user' t, COUNT(*) n FROM love_user
UNION ALL SELECT 'love_couple', COUNT(*) FROM love_couple
UNION ALL SELECT 'love_message', COUNT(*) FROM love_message
UNION ALL SELECT 'love_moment', COUNT(*) FROM love_moment
UNION ALL SELECT 'love_inspiration', COUNT(*) FROM love_inspiration
UNION ALL SELECT 'love_mood_option_default(应5)', COUNT(*) FROM love_mood_option_default
UNION ALL SELECT 'love_config(应1)', COUNT(*) FROM love_config
UNION ALL SELECT 'infra_file', COUNT(*) FROM infra_file
UNION ALL SELECT 'sms_log', COUNT(*) FROM system_sms_log;
SELECT id, name, icon FROM system_menu WHERE component_name = 'LoveMoodOptionDefault' AND deleted = 0;
