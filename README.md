# CarAppMarket · 车软星球市场

> GitHub：<https://github.com/a435630144/CarAppMarket>

车软星球市场（CarAppMarket）——车机软件站 **<后端域名>** 的安卓客户端，**主要面向汽车车机横屏大屏**，同时兼容手机。
最低支持 **Android 4.0（API 14）**，可在最新版 Android 上正常安装运行（targetSdk 28，Android 14+ 允许侧载）。

后端接口：`https://<后端域名>/api`（NestJS，仓库见 `<后端仓库>`）。

## 开源协议

本项目基于 **GPL-3.0** 协议开源，完整文本见 [LICENSE](LICENSE)。

## 发版与自更新（维护者须知）

- **正式签名**：keystore 在仓库外（`<开发目录>\keystores\carmix-market.jks`），
  凭据走根目录 `keystore.properties`（已 gitignore）。**该文件与备份（OneDrive\keystore-backup\）绝对不能丢失或泄露**——
  丢失=永远无法覆盖升级；泄露=任何人可签同签名恶意更新。仓库历史中的旧 debug.keystore 已作废，仅用于历史测试包。
- **发版流程**：改 `app/build.gradle` 版本号 → `gradle assembleRelease` → 用后端现有上传接口传 APK
  → `POST /api/app/release`（admin，body: versionCode/versionName/downloadUrl/sha256/changelog）→
  老版本 App 启动时自动检查 `GET /api/app/latest` 弹横幅提示升级
- **自更新规则**：versionCode 单调递增是唯一判据；低于 `minSupportedCode` 强更（不可关闭弹窗）；
  下载完成后 sha256 校验，不匹配即删包

## 功能

- **首页（v1.4.0 重构，对齐车机市场风格）**：左侧分类导航（热门/全部/各分类，选中高亮），
  右侧响应式卡片网格 + 顶部快捷排序（更新/下载量/评分；单击高→低▼、双击低→高▲、三击还原），
  按名称本地搜索；竖屏手机为顶部分类 chips + 单列横条
- **合集/多版本**：合集卡片带「多版本」徽章，详情页可横滑切换历史版本（版本 chip 含大小），
  并展示合集更新日志（数据来自后端 `software` 表 collection 折叠与 `collection` 负载）
- **收录日期**：网格卡片显示短日期（同年 MM-dd / 跨年 yyyy-MM-dd），详情页完整「收录 yyyy-MM-dd」
- **下载管理（v1.5.0）**：卡片右下角下载按钮，点击后飞行动画汇入顶部下载中心图标（Canvas
  矢量绘制，安卓4 无 SVG 支持），运行中任务数红点角标；下载管理页展示进行中任务
  （进度条/实时速度/已下载/取消）与已落盘安装包（大小/日期/安装/删除），全部实时刷新；
  重复下载自动加 (1)(2) 后缀，不覆盖不遗留
- **更新请求（v1.6.0）**：详情页二级菜单「需要升级 · N 人」按钮，对接后端
  `GET/POST /software/:id/update-request`，登录用户与游客（本地 UUID visitorId）均可幂等反馈，
  点击后实时显示「已反馈 · N 人」
- **标识体系（v1.6.0）**：卡片与详情页显示「车机版/手机版」描边标签（后端 type 字段）；
  官方应用使用「★ 官方」实心主色徽章提高辨识度；排序（更新/下载量/评分）与下载中心
  入口左对齐于内容区顶部
- **车机横屏优先布局**：横屏为「左侧栏（搜索/登录/热门）+ 右侧**响应式网格**」——`GridView auto_fit`
  按剩余宽度自动算列数（约 660dp→2 列、1000dp→3 列、1600dp+→5 列，21:9 带鱼屏自动更多列），
  小卡片含图标/名称/版本/评分/下载量；竖屏手机为单列横条列表；详情页横屏左右双栏、竖屏纵向单列
- **详情**：图标/版本/作者/评分/下载量、应用介绍、截图横滑 + 点击全屏、系统要求；图标缺失时按应用名首字生成彩色字母占位图
- **下载安装**：走 OkHttp（Conscrypt TLS）流式下载 + 进度通知，完成后一键唤起系统安装器；
  下载前调用 `POST /software/:id/download` 完成计数并获取直链（自动补全后端返回的相对路径）
- **账号**：邮箱验证码注册、登录、退出（JWT Bearer，对齐 Web 端 `community_token`）
- **评价**：查看评分汇总与全部车友评价、写评价（0.5~5 星 + 文字，重复提交即修改）、评价点赞

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

## 构建

本机已具备：`<开发目录>\jdk`（Temurin 17）、`<开发目录>\gradle-8.9`、
SDK（platform 35 / build-tools 34）。

```bat
cd /d E:\OneDrive\家里台式\桌面\temp\CarAppMarket
set JAVA_HOME=<开发目录>\jdk
set ANDROID_HOME=<开发目录>\Sdk
<开发目录>\gradle-8.9\bin\gradle.bat assembleDebug
```

产物：
- `app\build\outputs\apk\debug\app-debug.apk`
- `app\build\outputs\apk\release\app-release.apk`（`assembleRelease`，当前用 `debug.keystore` 签名，可直接安装；正式发布请换成自己的签名并从 `.gitignore` 排除）

依赖经阿里云 Maven 镜像下载（`settings.gradle`），国内网络可直接构建。

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

## 已知限制

- 列表接口无服务端关键字搜索，App 内搜索只过滤已加载的分页数据
- 极老 ARMv6（armeabi）Android 4.0 设备：Conscrypt 无对应原生库，TLS 1.2 无法启用（Cloudflare 站点不可达），属已知盲区
- 若站点 Cloudflare 开启了 TLS 1.3-only 或较新的证书链，4.x 设备仍受限于 Conscrypt 对应版本的能力

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

