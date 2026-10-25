# 交接文档

> 面向接手本项目的开发者。读完本文档应能独立完成：环境搭建 → 构建 → 发版 → 后端对接 → 常见问题排查。
> 配套文档：[README.md](README.md)（功能与兼容矩阵）、[DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md)（版本历史）。

## 1. 项目概览

| 项 | 内容 |
|---|---|
| 应用 | 车软星球市场（CarAppMarket），<后端域名> 车机软件站的安卓客户端 |
| 包名 | `asia.carmix.market` |
| 仓库 | https://github.com/a435630144/CarAppMarket（GPL-3.0） |
| 后端仓库 | `E:\OneDrive\自学编程\MimoDesktop\appshare\appshare`（NestJS + TypeORM + MySQL/Redis + R2/本地存储），生产部署 <后端域名>，API 前缀 `/api` |
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
| JDK | `<开发目录>\jdk`（Temurin 17） |
| Gradle | `<开发目录>\gradle-8.9\bin\gradle.bat` |
| Android SDK | `<开发目录>\Sdk`（platform 35 / build-tools 34） |

```bat
set JAVA_HOME=<开发目录>\jdk
set ANDROID_HOME=<开发目录>\Sdk
<开发目录>\gradle-8.9\bin\gradle.bat assembleRelease
```

- 依赖走阿里云镜像（`settings.gradle`），国内网络可直接构建
- 项目路径含中文 → `gradle.properties` 已有 `android.overridePathCheck=true`；aapt 检查 APK 需先拷到 ASCII 路径
- GitHub 直连常被墙：`git -c http.proxy=http://127.0.0.1:7890 push`（本机 Clash）

## 4. 签名管理（最高优先级事项）

- 正式 keystore：`<开发目录>\keystores\carmix-market.jks`
  （别名 `carmix-market`，密码见 OneDrive 备份说明文件）
- 凭据文件：项目根 `keystore.properties`（**已 gitignore**，指向外部 keystore）
- 备份：`E:\OneDrive\keystore-backup\`（jks + 说明）。**还需要一份离线备份（U 盘）**
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
