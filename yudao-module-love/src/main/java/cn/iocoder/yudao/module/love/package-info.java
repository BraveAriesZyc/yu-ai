/**
 * love 模块下，我们放情侣互动业务，支撑 C 端 App。
 * 例如说：C 端用户、情侣关系、待办、心情、悄悄话等等
 *
 * 1. Controller URL：以 /love/ 开头，避免和其它 Module 冲突；
 *    app-api（供 App）与 admin-api（供管理后台）分别由 controller/app 与 controller/admin 承载
 * 2. DataObject 表名：以 love_ 开头，方便在数据库中区分
 */
package cn.iocoder.yudao.module.love;
