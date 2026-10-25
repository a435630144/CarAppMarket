# 开发日志

> 项目：车软星球市场（CarAppMarket）—— <后端域名> 车机软件站安卓客户端
> 时间线：2026-10-25（单日完成 v1.0.0 → v1.11.0 全部迭代）
> 约定：`versionCode` 单调递增，是升级判据；`versionName` 仅展示。

---

## v1.0.0（versionCode 1）—— 初版

**功能**：对接 <后端域名>/api 全量核心链路——软件列表（分页）、分类、热门、详情（截图/评分/下载计数）、
下载安装（进度通知→系统安装器）、邮箱验证码注册/登录（JWT）、车友评价（列表/提交/点赞）。

**关键技术决策**（奠定后续全部架构）：
- **最低兼容 Android 4.0**，但 Cloudflare 只收 TLS 1.2+：捆绑 Conscrypt 2.5.2 在 API<22 注入安全提供者，
  外加 `Tls12SocketFactory` 兜底
- **零 AndroidX**：依赖仅 OkHttp 3.12.13（最后的 API9+ LTS 线）+ Conscrypt，避免 multidex 与兼容库膨胀
- 不用系统 DownloadManager（系统进程吃不到 Conscrypt 的 TLS 1.2），自写流式下载器
- 自写 `ApkFileProvider`（content:// 安装）与 `ImageLoader`（三级缓存），不引支持库
- 车机横屏优先，`layout-land` 独立布局

**实测**：模拟器 + 线上真实数据全链路（LocalSend 46MB 下载→安装器弹出）通过。
**修复**：① 后端 downloadUrl 返回相对路径需补全域名 ② Android 13+ 通知缺 smallIcon 直接崩溃
③ 下载回调线程安全（View 只能主线程碰）。

## v1.1.0（2）—— 低版本适配加固

- minSdk 15 → **14**（覆盖字面意义全部"安卓4"），构建器确认 Conscrypt 2.5.2 支持 API 14
- 安装 Intent 双轨：API<24 用 `file://`（老 ROM PackageInstaller 对 content:// 支持差），
  API≥24 用 `content://`（规避 FileUriExposedException）
- 新增 `FallbackDns`：车机休眠唤醒后系统 DNS 常失效，OkHttp 解析失败回落 223.5.5.5/119.29.29.29 UDP 直查
- 下载断点续传（HTTP Range + .tmp 追加）+ 网络错误自动重试一次
- README 新增「适配兼容方案矩阵」（系统版本 × TLS/安装/存储/ABI/屏幕）

## v1.2.0（3）—— 更名与开源

- 品牌定名：中文「**车软星球市场**」/ 英文 **CarAppMarket**；包名 `asia.carmix.market` 保持不变（升级连续性）
- GitHub 仓库建立：`github.com/a435630144/CarAppMarket`，69 文件首次推送

## v1.3.0（4）—— 响应式网格

- 横屏右区 ListView → **GridView auto_fit**（columnWidth 272dp），列数随屏宽自适应
- 实测：914dp→2 列 / 1024×600 mdpi→3 列 / 21:9→3~4 列
- 移除 manifest `configChanges`（修复运行中旋转不重建导致的布局错乱），改 onSaveInstanceState 保状态

## v1.3.1（5）—— 侧栏热门双列

- 侧栏热门推荐改双列迷你卡（该版本随后被 v1.4.0 的整体重构取代）

## v1.4.0（6）—— 车机市场风格重构（当前 UI 骨架）

- **左侧分类导航**：热门（伪分类，走 `/software/hot`）/ 全部 / 动态分类，选中高亮；竖屏自动回退 chips
- **顶部排序栏**：更新/下载量/评分，单击▼高到低、双击▲低到高、三击还原（客户端排序）
- **合集多版本**：合集卡片「多版本」徽章；详情页版本 chips 横滑切换（重载成员详情）+ 更新日志
- 修复：后端字段 "null"/"NULL" 字面量清洗（`vnull · NULL` 显示问题）

## v1.4.1（7）—— 收录日期

- 卡片短日期（同年 MM-dd / 跨年 yyyy-MM-dd）、详情页完整「收录 yyyy-MM-dd」

## v1.5.0（8）—— 下载管理中心

- `ApkDownloader` 重构出 **Task 注册表**（进度/500ms 速度采样/取消/完成出册）
- 卡片下载按钮 → `DownloadStarter` 统一入口 → **抛物线飞行动画**汇入下载中心图标
- 下载中心入口：Canvas 矢量图标（安卓4 无 SVG/VectorDrawable）+ 红点角标（运行中任务数）
- 下载管理页：进行中任务（进度条/实时速度/取消）+ 已落盘安装包（安装/删除带确认）

## v1.5.2（10）—— 下载去重命名 + 卡片改版

- 重复下载自动 `name(1).apk` 后缀，绝不覆盖旧文件（管理无死角）
- 卡片改版：左方形 logo / 右上信息 / 右下「下载」文字按钮（替代图标按钮）
- 下载中心图标内部线条收小（不顶轮廓）

## v1.6.0（11）—— 升级反馈 + 标识体系

- 对接后端 `GET/POST /software/:id/update-request`：详情页二级菜单「需要升级 · N 人」，
  游客（本地 UUID visitorId）与登录用户均可幂等反馈
- 卡片/详情页显示「车机版/手机版」描边标签；官方徽章升级「★ 官方」实心主色
- 排序栏与下载中心入口移到内容区左上角（对齐车机市场参考稿）

## v1.7.0（12）—— 昼夜模式

- 三态：跟随系统 / 浅色 / 深色，菜单即时切换
- 实现：`BaseActivity.attachBaseContext` 用 `createConfigurationContext` 覆盖 uiMode（API 17+）→
  `values-night`/`drawable-night` 资源自动解析；深色切 `AppThemeDark`（Holo/Material 暗色基底）
- 车机版=蓝 / 手机版=绿 描边配色区分；卡片去「热门」角标（热门已是标签页）
- 注意：API 14~16 无 `createConfigurationContext`，回退浅色

## v1.8.0（13）—— 离线错误态重设计（产品语义确立）

**用户明确的产品语义：离线 = 干净空态，无任何二级详情入口，标签取不到就没有。**

- 列表加载失败立即清空数据：中央「服务器异常，连接服务器超时，请稍后重试」+ 重新加载按钮 + 每 15 秒自动重试
- 固定导航「热门/全部」不再依赖分类接口；分类接口成功才追加真实分类
- 飞行模式实测：断网冷启动错误态完整、恢复网络后 15 秒内零操作自动回填
- 新增 GPL-3.0 LICENSE 与 README 协议章节

## v1.8.1（14）—— 自审查修复 9 项确认 BUG

自审查（代码级核查 + 模拟器复现）发现并修复：
1. MainActivity 漏注册下载中心监听 → 角标从不更新
2. 合集切版本未清 `mDownloadedFile` → 可能安装旧版本包
3. Android 13+ 运行时未申请 `POST_NOTIFICATIONS` → 通知静默丢失
4. 分类切换双重竞态（分页守卫吞请求 + 旧响应覆盖）→ 请求代际令牌
5. 401 无拦截 → 清 Session 引导重登
6. ImageLoader 失败分支连接泄漏 → finally 关闭
7. 旋转后搜索框与列表过滤不一致 → 回填搜索框
8. 用户取消下载误报「下载失败」→ 区分文案
9. 并发同名下载互写 tmp → 文件名预留集

## v1.9.0（15）—— 健壮性补全

1. **详情页页内错误态 + 重试**（不再 toast+退出，不再 15~25s DNS 空白挂起）
2. 管理页失败任务「重试/清除」（Task 记录原始直链）
3. **服务端搜索**：后端 `/software` 新增 `keyword`（名称/介绍 LIKE，缓存键含 keyword，**待部署**）；
   App 400ms 防抖下发 + 本地名称/介绍二次过滤（新旧后端双兼容）
4. **列表磁盘缓存**：断网冷启动展示缓存 + 橙色「离线缓存」横幅，15 秒自动重试换回在线数据
5. 图片磁盘缓存 64MB 上限（最旧优先清理至 3/4）

## v1.10.0（16）—— 车机低配性能优化

- 图标位图解码 400→256px；LetterDrawable 实例缓存；GridView/ListView 关 scrollingCache
- `onTrimMemory(UI_HIDDEN+)` 逐出位图 LruCache（实测后台 COMPLETE 接受、无崩溃）
- 诚实结论：当前线上数据图标全空（字母占位），位图优化属前瞻保护，实测 PSS 持平；
  Java Heap ~2MB，Native 13.5MB 为 Conscrypt 固有成本

## v1.11.0（17）—— 正式签名 + 商店自更新

- **签名迁移**：release 改用仓库外正式 keystore（`keystore.properties` 凭据，gitignored），
  仓库内 debug.keystore 删除作废。原因：旧密钥连密码已公开在 GitHub，任何人可签同签名恶意更新——
  自更新通道建立前必须换
- 后端新增 `app-update` 模块：`GET /api/app/latest`（公开）+ `POST /api/app/release`（admin，versionCode 防降级）；
  元数据存 SiteConfig，APK 走现有上传/CDN（**待部署**）
- App：启动 1h 节流检查（失败静默）→ 橙色横幅「发现新版本」→ 复用下载器 → sha256 校验（不匹配删包）→ 安装；
  低于 `minSupportedCode` 不可关闭强更；生产未配置时优雅降级（实测）
