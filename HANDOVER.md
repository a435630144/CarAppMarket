# 交接文档

> 面向接手本项目的开发者。读完本文档应能独立完成：环境搭建 → 构建 → 发版 → 后端对接 → 常见问题排查。
> 配套文档：[README.md](README.md)（功能与兼容矩阵）、[DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md)（版本历史）。

## 1. 项目概览

| 项 | 内容 |
|---|---|
| 应用 | 车软星球市场（CarAppMarket），<后端域名> 车机软件站的安卓客户端 |
| 包名 | `asia.carmix.market` |
| 仓库 | https://github.com/a435630144/CarAppMarket（GPL-3.0） |
| 后端仓库 | `<后端仓库目录>`（NestJS + TypeORM + MySQL/Redis + R2/本地存储），生产部署 <后端域名>，API 前缀 `/api` |
| 技术边界 | **minSdk 14（Android 4.0）/ targetSdk 28 / 零 AndroidX**；依赖仅 OkHttp 3.12.13 + Conscrypt 2.5.2 |
| 主场景 | 车机横屏大屏（800×480 ~ 21:9），竖屏手机兼容 |

**为什么这么选型**（不要轻易推翻，每条都有踩坑背景）：
- 安卓4 上 Cloudflare 只收 TLS 1.2+ → 必须 Conscrypt 注入（见 `App.java`）+ `Tls12SocketFactory` 兜底
- 系统 DownloadManager 跑在系统进程，吃不到应用注入的 Conscrypt → 4.x 上连不上站点 → 必须应用内下载
- 零 AndroidX：避开 multidex、支持库方法数与 minSdk 冲突；自写了 FileProvider / 图片加载 / 矢量图标 / 昼夜模式等替代件

## 2. 代码地图

```
app/src/main/java/asia/carmix/market/
├── App.java                  # Conscrypt 注入(API<22)、主题模式存取、onTrimMemory
├── Session.java              # JWT 登录态（SharedPreferences，对齐 Web 端 community_token）
├── api/Api.java              # 全部接口封装（同步方法，必须后台线程调）；401 统一清 Session
├── model/                    # SoftwareItem（含合集/版本/日志解析）/ Review / User
├── provider/ApkFileProvider  # content:// 安装通道（API≥24 用）
├── download/                 # （无独立包）下载相关都在 util/
└── util/
    ├── ApkDownloader.java    # 核心：Task 注册表（进度/速度采样/取消/文件名预留）+ 通知 + 续传重试
    ├── DownloadStarter.java  # 统一下载入口（计数取链→启动）
    ├── DownloadIconDrawable  # Canvas 矢量图标（安卓4 无 SVG）
    ├── FallbackDns.java      # 系统 DNS 失败回落公共 DNS（UDP 直查）
    ├── ImageLoader.java      # 三级缓存图片加载（内存 LruCache/磁盘 64MB 上限/采样解码）
    ├── LetterDrawable.java   # 无图标时首字占位图（按名字缓存复用）
    ├── Tls12SocketFactory    # API16~20 TLS1.2 兜底
    ├── Visitor.java          # 匿名访客 UUID（升级反馈幂等用）
    └── Task/Formatters       # 后台任务包装 / 展示格式化
app/src/main/java/asia/carmix/market/ui/
    BaseActivity              # 昼夜模式注入（attachBaseContext 覆盖 uiMode），全部页面继承
    MainActivity              # 首页：导航/网格/排序/搜索/错误态/离线缓存/自更新横幅
    DetailActivity            # 详情：版本切换/评价/截图/页内错误态
    DownloadManagerActivity   # 下载管理：任务（进度/速度/取消/重试/清除）+ 安装包
    Login/RegisterActivity    # 账号
res/
    layout-land/              # 车机横屏布局（主场景）
    values-night/ drawable-night/  # 深色资源
```

## 3. 构建环境

本机（Windows）已配好，新机器按此搭：

| 项 | 路径/版本 |
|---|---|
| JDK | `<JDK17 目录>`（Temurin 17） |
| Gradle | `<Gradle 目录>\bin\gradle.bat` |
| Android SDK | `<SDK 目录>`（platform 35 / build-tools 34） |

```bat
set JAVA_HOME=<JDK17 目录>
set ANDROID_HOME=<SDK 目录>
<Gradle 目录>\bin\gradle.bat assembleRelease
```

- 依赖走阿里云镜像（`settings.gradle`），国内网络可直接构建
- 项目路径含中文 → `gradle.properties` 已有 `android.overridePathCheck=true`；aapt 检查 APK 需先拷到 ASCII 路径
- GitHub 直连常被墙：`git -c http.proxy=http://127.0.0.1:7890 push`（本机 Clash）

## 4. 签名管理（最高优先级事项）

- 正式 keystore：`<安全目录>\carmix-market.jks`
  （别名 `carmix-market`，密码见备份说明文件（与密钥一同存放））
- 凭据文件：项目根 `keystore.properties`（**已 gitignore**，指向外部 keystore）
- 备份：`<备份目录>\`（jks + 说明）。**还需要一份离线备份（U 盘）**
- ⚠️ 丢失 = 应用永远无法覆盖升级；泄露 = 任何人可签同签名恶意更新覆盖安装到用户车机
- ⚠️ 仓库 git 历史中残留旧 debug.keystore（已作废）：它签过的历史测试包与新签名互不兼容，
  装过 v1.10.0 及之前测试包的设备升级需卸载重装
- CI 自动发版（如需）：keystore 转 base64 存 GitHub Secrets，Actions 解密签名——**不要**把 keystore 提回仓库

## 5. 发版流程（Step by Step）

1. 改版本：`app/build.gradle` 的 `versionCode`（**必须 +1 以上，单调递增**）与 `versionName`
2. 构建：`gradle assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
3. 计算 sha256（发给后端用）：`certutil -hashfile app-release.apk SHA256`
4. 上传 APK：调后端现有上传接口（管理员身份），拿 `downloadUrl`
5. 注册元数据（**需后端部署 app-update 模块**）：
   ```bash
   curl -X POST https://<后端域名>/api/app/release \
     -H "Authorization: Bearer <管理员token>" -H "Content-Type: application/json" \
     -d '{"versionCode":18,"versionName":"1.12.0","downloadUrl":"https://<后端域名>/api/uploads/download/xxx",
          "sha256":"<第3步结果>","changelog":"更新内容","forceUpdate":false,"minSupportedCode":14}'
   ```
6. 老版本 App 冷启动即看到升级横幅（1 小时节流）；強更阈值由 `minSupportedCode` 控制

## 6. 后端对接现状（重要）

| 能力 | 状态 |
|---|---|
| 软件列表 `keyword` 服务端搜索 | **已改代码、已提交、待部署**。未部署时 App 本地过滤兜底，功能可用但只搜已加载分页 |
| 商店自更新 `/api/app/latest` + `/api/app/release` | **已改代码、已提交、待部署**。未部署时 App 横幅隐藏（优雅降级） |

部署后无需改 App 任何代码，两个能力自动生效。

## 7. 测试要点与模拟器注意事项

- 测试模拟器装有 **OneUIDesktop 车机桌面**（HOME 应用 + 看门狗，会抢焦点吞 adb 点击）：
  自动化测试前 `am force-stop com.example.oneuidesktop`（必要时 `pm disable-user --user 0`，
  测完 `pm enable` + `cmd package set-home-activity com.example.oneuidesktop/.MainActivity` 还原）
- **安卓12+ shell 不能 `am start` 非 exported Activity**（Permission Denial），深页面要走 UI 点击路径
- 断网测试：`adb shell cmd connectivity airplane-mode enable/disable`
- 中文路径：aapt 直接读会 Illegal byte sequence，先拷到 `<临时目录>\` 再检查
- 回归清单：列表/分类切换/搜索/排序三态/详情版本切换/下载进度速度/取消/失败重试清除/离线横幅/昼夜切换/升级反馈
- 截图存档惯例：`<临时目录>\*.png`

## 8. 已知限制与待办

**已知限制**（设计取舍，非缺陷）：
- 搜索在旧后端下只能匹配已加载分页（本地名称+介绍过滤）；部署 keyword 后端后为全库搜索
- API 14~16 无昼夜深色（缺 `createConfigurationContext`），回退浅色
- ARMv6（armeabi）设备无 Conscrypt 原生库，TLS 1.2 无解，属盲区
- 无差量更新（非 Play 渠道，全量 ~3.7MB 可接受）、无 root 静默安装

**待办（按价值排序，用户未指派）**：
1. 已安装管理/升级提醒：后端软件表加 `packageName` + `versionCode` 数字字段，App 扫描本机对比展示「可升级」
2. 车机 DPAD/旋钮焦点适配（焦点态样式 + 焦点记忆）
3. <后端域名> 证书锁定（OkHttp CertificatePinner）
4. values-en 多语言
5. 后端发版管理 UI（目前 curl 发版）

## 9. 关键设计决策记录（ADR 摘要）

| 决策 | 理由 |
|---|---|
| 零 AndroidX，自写组件 | 安卓4 + 方法数 + 支持库停更风险；自写成本可控（FileProvider/图片/图标/昼夜） |
| Conscrypt 捆绑 | 安卓4 系统无/默认关 TLS 1.2，Cloudflare 硬要求 |
| 不用系统 DownloadManager | 系统进程无法用应用内 Conscrypt |
| 请求代际令牌防竞态 | 切分类/翻页快速操作时旧响应覆盖新响应的实际 bug |
| 离线=干净空态（首页）/ 页内错误态（详情） | 用户明确产品语义；无数据就不能给二级入口 |
| SiteConfig 存自更新元数据 | versionCode 需数字字段；复用软件体系会两处状态 |
| minSdk 14 / targetSdk 28 | 覆盖安卓4全系；28 免分区存储且满足安卓14 侧载最低线 |

<!-- 以下章节自 README 迁入（2026-10-29，README 改为用户使用视角） -->

## 老系统兼容要点（为什么这么写）

| 问题 | 方案 |
|------|------|
| Cloudflare 最低要求 TLS 1.2，而 Android 4.0 无 TLS 1.2、4.1~4.4 默认不启用 | 捆绑 [Conscrypt](https://github.com/google/conscrypt) 并在 API<22 时插到安全提供者首位；加载失败再退回 `Tls12SocketFactory` 强启 TLS 1.2 |
| OkHttp 4.x 需要 API 21+ | 锁定 OkHttp 3.12.13（LTS，支持 API 9+） |
| Picasso/Glide 新版依赖受限 | 自写 130 行图片加载器（内存 LruCache + 磁盘缓存 + 采样解码） |
| Android 7.0+ 禁止 `file://` URI 安装 | 自写极简 `ApkFileProvider`（ContentProvider），避免引入整个 AndroidX |
| 系统 DownloadManager 在 4.x 连不上 TLS 1.2-only 站点（运行在系统进程，吃不到本进程注入的 Conscrypt） | 自写 OkHttp 流式下载器 |
| targetSdk ≥29 分区存储 / ≥24 file URI 限制 / <23 被 Android 14 拒装 | targetSdk 28 的平衡点；存储权限仅在 API≤18 需要（已声明 `maxSdkVersion=18`） |
| AndroidX/支持库方法数超限触发 multidex | 零 AndroidX 依赖，APK 极小 |

## 项目结构

```
app/src/main/java/asia/carmix/market/
├── App.java                     # Conscrypt 提供者注入（API<22）
├── Session.java                 # 登录态（SharedPreferences）
├── api/Api.java                 # 全部接口封装（同步方法，后台线程调用）
├── api/ApiException.java
├── model/                       # SoftwareItem / Review / User
├── provider/ApkFileProvider.java# APK content:// 提供者
├── util/
│   ├── ApkDownloader.java       # OkHttp 流式下载 + 进度通知 + 安装 Intent
│   ├── ImageLoader.java         # 自实现图片加载
│   ├── Tls12SocketFactory.java  # API16-20 TLS1.2 兜底
│   ├── Task.java                # 后台任务→主线程回调
│   └── Formatters.java
└── ui/                          # MainActivity / DetailActivity / Login / Register / Adapter
```

## 接口清单（对接 <后端域名>/api）

| 功能 | 接口 |
|------|------|
| 列表 | `GET /software?page=&limit=&category=` |
| 分类 | `GET /software/categories` |
| 热门 | `GET /software/hot` |
| 详情 | `GET /software/:id`（含 `downloadDirectUrl` CDN 直链） |
| 下载计数+取链 | `POST /software/:id/download` → `{downloadUrl, downloadFileName}` |
| 浏览计数 | `POST /software/:id/view` |
| 评价列表 | `GET /software/:id/reviews`（游客可看，登录带点赞态） |
| 提交/修改评价 | `POST /software/:id/reviews` `{value, content, keepOriginal}` |
| 评价点赞 | `POST /software/reviews/:reviewId/like` |
| 登录 | `POST /auth/user/login` `{email, password}` |
| 注册 | `POST /auth/user/register` `{email, username, password, verificationCode}` |
| 发验证码 | `POST /auth/user/send-verification-code` `{email}` |

服务端地址集中在 `api/Api.java` 顶部的 `SITE`/`BASE` 常量，换域名改一处即可。

## 适配兼容方案（矩阵）

### 系统版本覆盖

| Android 版本 | API | 支持情况 | 关键适配点 |
|---|---|---|---|
| 4.0 ~ 4.0.2 | 14 | ✅（v1.1.0 起） | minSdk 14；Conscrypt 2.5.2 官方支持 API 14 |
| 4.0.3 ~ 4.3 | 15~18 | ✅ | TLS：注入 Conscrypt 提供 TLS 1.2/1.3；写应用外部目录需 WRITE_EXTERNAL_STORAGE（manifest 声明 `maxSdkVersion=18`，安装时授予） |
| 4.4 ~ 6.x | 19~23 | ✅ | TLS：Conscrypt（API<22），16~20 另有 Tls12SocketFactory 兜底；API≥19 应用目录免存储权限 |
| 7.0 ~ 13 | 24~33 | ✅ | 安装走自写 ContentProvider 的 content://；API≥26 通知走渠道；未知来源安装引导 |
| 14 ~ 15 | 34~35 | ✅（模拟器实测） | targetSdk 28 ≥ 23 满足侧载最低要求；API≥26 REQUEST_INSTALL_PACKAGES 授权引导 |

### 网络与 TLS（安卓4.x 上 Cloudflare 的关键）

1. **Conscrypt 注入**（`App.java`）：API<22 时 `Security.insertProviderAt(Conscrypt.newProvider(), 1)`，
   使 OkHttp 的 SSLContext 具备 TLS 1.2/1.3 —— 4.0.x 系统原生无 TLS 1.2，此为唯一出路
2. **Tls12SocketFactory 兜底**（`util/Tls12SocketFactory.java`）：Conscrypt 加载失败时，API 16~20
   强制启用系统自带的 TLS 1.2
3. **不走系统 DownloadManager**：它运行在系统进程，享受不到本进程注入的 Conscrypt → 4.x 上必挂；下载全部走应用内 OkHttp
4. **DNS 回落**（`util/FallbackDns.java`）：车机休眠唤醒后系统解析器常失效，OkHttp Dns 失败时
   直接 UDP 查 223.5.5.5 / 119.29.29.29（仅解析 IP，证书校验/SNI 不降级）
5. **断点续传 + 自动重试**（`util/ApkDownloader.java`）：网络类失败自动重试一次，从 `.tmp`
   以 HTTP `Range` 续传；服务器不支持 Range（返回 200）则整体重下

### 安装路径（各版本 ROM 兼容）

- **API ≥ 24**：`content://` + 自写 `ApkFileProvider`（targetSdk≥24 用 file:// 会 FileUriExposedException）
- **API < 24**：`file://` 直装（老 ROM 的 PackageInstaller 对 content:// 支持不稳；该检查只存在于 7.0+）
- API ≥ 26 触发系统「安装未知应用」授权页，授权一次后续装免确认

### ABI / 屏幕适配

- **ABI**：armeabi-v7a（主流老车机）、arm64-v8a、x86、x86_64 全覆盖（Conscrypt 原生库四架构齐备）
  ⚠️ 极老 ARMv6（armeabi）设备 Conscrypt 无对应 .so，TLS 1.2 无解，属已知盲区
- **横屏车机**（主场景）：`layout-land/` 专属布局——首页左侧栏（搜索/登录/热门）+ 右侧响应式网格
  （auto_fit 自动列数）；详情页左右双栏
- **竖屏手机**：常规单列列表布局，`layout/` 提供
- 常见车机分辨率 800x480 / 1024x600 / 1280x480 / 1920x720 / 21:9 带鱼屏均按 dp 自适应
  （实测 914dp→2 列、1024dp→3 列、1280dp(21:9)→3~4 列）

### 真机验证建议

模拟器已覆盖：列表/详情/横竖屏/下载/安装全链路（API 35）。以下需真机确认：
- 安卓 4.2~4.4 真机（或云真机）：Conscrypt 与 Cloudflare 的 TLS 1.2 握手
- 具体车机 ROM：安装器唤起、横屏比例、外置存储路径（`getExternalFilesDir` 在部分车机映射到
  USB/SD 分区，行为一致但路径不同，无需改代码）

## 实测记录（2026-10-25，API 35 模拟器）

- 竖屏/横屏首页、详情、字母占位图、评分、徽章：✅ 正常（线上真实数据）
- 响应式网格：914dp→2 列 / 1024dp（1024x600 mdpi 车机）→3 列 / 1280dp（21:9）→3~4 列：✅ 自动适配
- 下载 → 进度通知 → 46MB APK 落盘 → 自动唤起系统安装器（content:// + 自写 FileProvider）：✅ 全链路通过（v1.0.0 与 v1.1.0 均已回归）
- 修复过的问题：下载 URL 为相对路径需补全域名；Android 13+ 通知缺 smallIcon 会崩溃；下载回调线程安全
- 模拟器装有 OneUIDesktop 车机桌面（HOME 应用，带看门狗自动重启、会周期性抢焦点），
  自动化测试时需 `am force-stop com.example.oneuidesktop` 或临时 `pm disable-user`
