# 予爱 上架材料与清单(M33,2026-10-26)

本目录是上架准备材料(2026-10-27 起迁入仓库随源码维护)。配套代码改动已入库:M33 首启隐私 Gate / 账号注销 / targetSdk 34 / release 禁明文。

## 一、本目录内容

| 文件 | 用途 |
| --- | --- |
| privacy.html | 隐私政策全文(草稿)——**先替换全部【请填写】占位** |
| agreement.html | 用户协议全文(草稿)——同上 |

## 二、待办清单

### 1. 协议页上传服务器(我方 nginx)

两份 HTML 放到 love.shattered-dream.top 站点根目录,使下面两个 URL 可访问:

- `https://love.shattered-dream.top/agreement.html`
- `https://love.shattered-dream.top/privacy.html`

⚠️ 这两个 URL 已写死在 App 里(首启弹窗 + 「我的」页入口),换域名/路径要同步改 `PrivacyGate.kt` / `MinePage.kt`。

### 2. 高德 key 补「发布版安全码 SHA1」(关键,防地图白屏)——✅ 2026-10-27 已填入控制台

release 签名与调试签名 SHA1 不同,当前 key 若只绑了调试 SHA1,release 包地图瓦片会空白。查 release SHA1:

```bash
"D:\Application\DevDependencies\JavaJdk\jdk-17.0.20.1\bin\keytool.exe" -list -v -keystore love_baby-release.jks
```

(keystore 在 `Desktop\app\` 或仓库 `love_baby/androidApp/`,输 keystore 密码)→ 复制 `SHA1` 值 → 高德开放平台 → 应用 → 予爱的 Key → 「发布版安全码 SHA1」填入保存。装 release 包开位置页验证瓦片正常。

**release SHA1 已提取(2026-10-27,alias=love_baby)**:

```text
63:51:C9:DD:08:95:D8:70:81:AB:E7:C0:70:96:DF:09:6A:5F:65:3A
```

→ 高德控制台该 Key 的「发布版安全码 SHA1」栏粘贴保存即可(即时生效,无需重新打包;Web 服务 key 不绑签名不受影响)。

### 3. 商店资质(只能你来办)

- [ ] 软件著作权证书(大部分商店必传;名称「予爱」,建议与上架名一致)
- [ ] 工信部 App 备案(域名 love.shattered-dream.top 需已 ICP 备案;App 备案在接入商/云服务商处提交,要填包名 com.zyc.love_baby、公钥 SHA1、上架平台)
- [ ] 各商店开发者账号(个人实名)

### 4. 商店表单材料

- [ ] 应用简介、分类(生活/情侣类)、截图(建议 3-6 张)
- [ ] **隐私政策 URL** = privacy.html 的地址;用户协议 URL 同理
- [ ] **测试账号**:审核员要能登录,手机号+验证码需真实下发——表单备注里说明「验证码将实时告知」或审校期间人工配合转发;提前注册一个测试号并绑定好情侣(让审核能看到完整功能)
- [ ] 版本号:versionCode 1 / versionName 1.0.0(首次上架)

### 5. 上架前真机回归(新装 release 包走一遍)

- [x] 首启弹隐私弹窗 → 点「不同意并退出」→ App 退出;重进 → 同意 → 进登录页
- [x] 同意前期间无任何权限弹窗(通知权限在同意后才申请)
- [x] 登录 → 主功能正常 → 位置页地图瓦片正常(验证第 2 步生效)
- [x] 「我的」页:用户协议与隐私政策行可打开;注销账号 → 确认 → 回登录页;原手机号可重新注册
- [x] 注销后换号确认:解绑生效、原账号无法再登录

## 三、APK 信息

- 产物:`love_baby/androidApp/build/outputs/apk/release/androidApp-release.apk`(已签名)
- 包名 `com.zyc.love_baby`,versionCode 1,versionName 1.0.0,targetSdk 34,minSdk 23
- 权限 9 项全部运行时按需申请:INTERNET / POST_NOTIFICATIONS / RECORD_AUDIO / READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_VISUAL_USER_SELECTED / READ_EXTERNAL_STORAGE(≤32) / ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
