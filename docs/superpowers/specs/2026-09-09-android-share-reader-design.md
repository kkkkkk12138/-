# Android 分享阅读首版设计

## 产品定位

Android 版“小说一键换名”通过系统分享入口接收公开网页链接，并支持打开本地 TXT、无 DRM EPUB。用户在 App 的阅读页中应用“原名 → 新名”规则，网页尽量保留原站排版，TXT 和 EPUB 使用本地阅读排版。

首版不追求修改任意第三方 App 的界面，不申请无障碍或屏幕录制权限，也不读取夸克、微信或其他浏览器的 Cookie。

## 首版范围

### 支持

- 从夸克、微信和其他 App 接收公开 `HTTP/HTTPS` 链接。
- 在 App 首页输入或粘贴公开链接。
- 通过 Android 系统文件选择器打开 TXT、EPUB。
- 网页保留原页面模式，不抽取正文。
- TXT 连续滚动阅读。
- EPUB 章节目录、上一章、下一章和阅读进度。
- 全局换名规则，供网页、TXT、EPUB 共用。
- 最近阅读、章节位置和滚动进度本地保存。
- 生成可独立安装并覆盖升级的签名 APK。

### 不支持

- 读取或复用其他 App 的登录状态、Cookie、密码和浏览记录。
- 在 WebView 中登录网站。
- 绕过网站登录、付费墙、验证码或访问控制。
- 修改夸克、微信、第三方阅读器等 App 的内部文字。
- OCR 悬浮覆盖、无障碍服务和全局屏幕识别。
- DRM EPUB、PDF、漫画图片文字和音频内容。
- 云同步、账号系统、广告、统计 SDK 和远程正文处理。
- 声称支持所有网站或所有 EPUB。

## 技术路线

采用 Jetpack Compose + Material 3 的原生 Android 外壳，统一使用受控 WebView 作为阅读渲染内核。

- Compose 负责系统分享、首页、文件选择、最近阅读、规则面板、目录、阅读工具栏和设置。
- `android.webkit.WebView` 负责公开网页以及本地 TXT/EPUB HTML 的显示。
- 现有 TypeScript 换名引擎构建为 Android assets，在三种内容中复用。
- Android 原生层不实现第二套替换算法，只管理规则、阅读会话和脚本注入时机。
- Room 保存结构化本地数据。
- Storage Access Framework 提供本地文件只读访问。
- `WebViewAssetLoader` 提供 TXT/EPUB 本地内容的安全来源。

Android 基线：

- `minSdk = 26`，覆盖 Android 8.0 及以上。
- `compileSdk` 和 `targetSdk` 使用当前稳定 Android SDK，首次实现目标为 API 36。
- Kotlin、Gradle Kotlin DSL 和 Version Catalog。
- Compose Material 3、Navigation Compose、Lifecycle、Room、Hilt 和 AndroidX WebKit。
- Android ICU `CharsetDetector` 辅助 TXT 编码检测。
- Jsoup 清理 EPUB 章节 HTML。
- 所有依赖版本集中在 `android/gradle/libs.versions.toml`。
- Android application ID 使用 `com.xiaoshuo.yijianhuanming`。
- Android 首个独立版本使用 `0.1.0 (1)`，不与 Apple 构建号绑定。

## 项目结构

Android 工程放在仓库根目录的 `android/`，不改变现有 Safari 和 WebExtension 构建。首版使用单一 `app` Gradle 模块，以下边界通过 Kotlin package、接口和测试隔离；当编译时间或团队规模确实需要时再拆为多个 Gradle 模块。

```text
android/
├── app/
│   └── src/main/
│       ├── java/com/xiaoshuo/yijianhuanming/
│       │   ├── intake/          系统分享、链接输入、文件选择
│       │   ├── library/         最近阅读、阅读进度、历史管理
│       │   ├── reader/          阅读页、目录、规则面板、设置
│       │   ├── content/web/     网页加载、导航和登录风险拦截
│       │   ├── content/txt/     编码识别、文本解析、本地 HTML
│       │   ├── content/epub/    EPUB 校验、解包、目录和章节
│       │   └── data/            Room、文件 URI、缓存和设置
│       └── assets/web-runtime/  TypeScript 换名运行时构建产物
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

package 不得反向依赖 UI：

- `intake`、`library`、`reader` 可以依赖内容接口和本地数据接口。
- `content` 不能依赖 Compose 页面。
- `assets/web-runtime` 只包含可注入 JavaScript，不感知 Android UI。
- `data` 不解析网页或 EPUB。

## 页面结构

### 首页

首页是紧凑的工具入口，不做内容平台：

- “打开网页链接”：输入或粘贴公开链接。
- “打开 TXT / EPUB”：调用系统文件选择器。
- 最近阅读：显示标题、内容类型、最后位置和最后打开时间。
- 隐私说明：不登录网站、不读取其他浏览器、不上传内容。

App 被系统分享唤起时，验证输入后直接进入阅读页。输入无效时返回首页并显示原因。

### 阅读页

- 中央为 WebView 阅读区域。
- 顶部工具栏包含返回、标题、刷新和更多。
- 底部浮动工具栏包含规则、目录和阅读设置。
- 网页保留原页面排版。
- TXT 使用本地连续滚动页面。
- EPUB 支持目录和章节切换。
- Android 系统返回手势先返回 WebView 历史；无历史时退出阅读页。
- 手机使用单页阅读和底部抽屉；平板、折叠屏可使用目录或规则侧栏。

### 规则面板

- 显示全部规则摘要和数量。
- 点击单条规则后仅该行原地编辑。
- 新规则添加到末尾并自动聚焦。
- 删除最后一条后保留“全部生效”，用于恢复原文。
- 应用失败时保留输入、编辑行和错误状态。
- 所有触控目标不小于 `48dp`。

规则交互与 Safari 版保持一致，但 UI 使用 Android 原生组件。

### 设置

- 清除临时网页数据。
- 清除最近阅读。
- 删除全部规则。
- 清理 EPUB 缓存。
- 查看隐私说明和版本信息。

## 核心接口

### 输入

```kotlin
sealed interface ReaderInput {
    data class WebUrl(val uri: Uri) : ReaderInput
    data class TxtDocument(val uri: Uri) : ReaderInput
    data class EpubDocument(val uri: Uri) : ReaderInput
}

interface InputResolver {
    suspend fun resolve(intent: Intent): Result<ReaderInput>
}
```

`InputResolver` 只识别类型、验证 URI 和返回标准输入，不加载正文。

### 内容源

```kotlin
interface ReaderContentSource {
    suspend fun open(): Result<ReaderDocument>
    suspend fun close()
}
```

`ReaderDocument` 描述渲染入口、标题、内容类型和可选目录，不暴露具体解析器。

### 页面换名运行时

Android 使用独立、无浏览器权限依赖的 TypeScript 入口，不直接注入完整 WebExtension content script：

```ts
window.__NAME_REPLACER__ = {
  install(): void,
  applyRules(rules: ReplaceRule[]): ApplyResult,
  restoreOriginalText(): void,
  dispose(): void
}
```

- `install()` 建立文本快照和动态内容监听。
- `applyRules()` 从原始文本计算并应用当前规则。
- `restoreOriginalText()` 在删除全部规则时恢复原文。
- `dispose()` 取消观察器并释放页面状态。
- API 不访问 `chrome`、`browser`、网络、Cookie、文件或原生对象。
- Android 使用 `evaluateJavascript` 调用 API，并使用回调结果判断成功或失败。

### 规则

```kotlin
data class ReplaceRule(
    val id: String,
    val source: String,
    val target: String,
    val order: Int
)

interface RuleRepository {
    val rules: Flow<List<ReplaceRule>>
    suspend fun replaceAll(rules: List<ReplaceRule>)
}
```

规则应用顺序稳定；空白或不完整规则在保存前归一化。

### 阅读会话

```kotlin
data class ReaderSession(
    val sourceId: String,
    val contentType: ReaderContentType,
    val title: String,
    val location: ReaderLocation
)
```

阅读会话只保存定位信息，不保存网页正文或完整书籍内容。

## 数据流

### 公开网页

```text
外部 App 分享 URL
→ ACTION_SEND
→ InputResolver
→ URL 安全校验
→ WebContentSource
→ WebView 加载
→ 页面提交完成
→ 注入换名运行时
→ 注入本地规则
→ 显示替换结果
```

页面动态新增内容由换名运行时的 `MutationObserver` 处理。规则变化后先恢复页面原文，再按新规则计算。

### TXT

```text
系统文件选择器
→ 只读 content URI
→ MIME 与文件头校验
→ 编码检测
→ 文本读取与 HTML 转义
→ 本地 HTML
→ WebViewAssetLoader
→ 注入换名运行时与规则
```

自动编码检测至少覆盖 UTF-8、UTF-16LE、UTF-16BE 和 GB18030。检测置信不足时让用户选择编码，不以乱码结果继续阅读。

### EPUB

```text
系统文件选择器
→ 只读 content URI
→ ZIP/EPUB 校验
→ 安全解包到 App 私有缓存
→ 解析 container.xml 与 OPF
→ 生成书脊和目录
→ 清理章节 HTML 并忽略作者脚本
→ WebViewAssetLoader 加载章节
→ 注入换名运行时与规则
```

章节切换时保存当前章节与滚动进度。关闭书籍后释放解析器；缓存按空间策略清理。

### 规则更新

```text
规则面板修改
→ 归一化与校验
→ Room 事务保存
→ StateFlow 发出新规则
→ WebView 恢复原文
→ 重新应用规则
→ 保存结果状态
```

任何一步失败都不得清空用户编辑中的规则。

## WebView 安全设计

### 导航

- 默认允许 `https://`。
- `http://` 需明确提示连接未加密，用户确认后仅用于当前链接。
- 拦截 `file://`、外部 `content://`、`javascript:`、`intent:` 和未知自定义协议。
- 拨号、支付、外部 App 唤起和下载不自动执行。
- 页面新窗口仍在受控 WebView 中验证后打开。
- 正式 APK 禁用 WebView 调试。

### 登录

首版不提供网站登录：

- 常见登录路径如 `/login`、`/signin`、`/passport` 进入风险拦截。
- 页面出现密码输入框时显示“不支持网站登录”提示。
- 密码表单提交被阻止。
- 页面退出时清除 Cookie、Web Storage、表单数据和网页缓存。
- 不提供保存密码、自动填充或 Cookie 导入。

这些措施只能降低误登录风险，不能保证识别所有网站的自定义登录流程。产品文案必须提示用户不要在 App 网页中输入账号密码。

### JavaScript

- 不使用 `addJavascriptInterface`。
- 网页不能调用原生文件、规则库或系统能力。
- 原生层仅通过单向 `evaluateJavascript` 注入受控运行时和序列化规则。
- 注入内容必须经过 JSON 序列化，不拼接未转义用户文本。
- 本地 TXT/EPUB 页面设置严格 CSP。

## 文件安全

### 通用

- 使用系统文件选择器和只读 URI 权限。
- 不遍历用户文件系统。
- 不修改原始 TXT/EPUB。
- 用户删除历史时不删除原文件。
- EPUB 为支持随机访问可复制到 App 私有临时缓存；该缓存不是用户原文件，关闭会话或清理缓存时可删除。

### TXT

- 文本内容必须 HTML 转义。
- 不将 TXT 内容作为 HTML 解析。
- 对超大文件采用分块读取和渲染，避免一次性占满内存。
- TXT 原文件上限为 `20 MiB`；超过限制时拒绝打开而不是崩溃。

### EPUB

- 防止 ZIP 路径穿越，解包路径必须位于会话缓存目录内。
- EPUB 压缩文件上限为 `100 MiB`。
- EPUB 解压总量上限为 `500 MiB`，条目数上限为 `10,000`。
- 单个资源上限为 `50 MiB`，单条目压缩比上限为 `100:1`。
- 禁止执行书内 JavaScript。
- 移除表单、iframe、object、embed 和事件处理属性。
- 禁止章节访问远程网络资源。
- 只允许章节引用解包目录内的图片。
- 首版不加载作者 CSS，统一使用 App 内置阅读主题，避免 `@import`、远程字体和外部 `url()`。
- 拒绝加密或 DRM 资源，不尝试破解。
- 任一限制触发后立即停止解析，并删除本次会话产生的全部缓存。

## 数据与隐私

Room 保存：

- 全局换名规则。
- 最近阅读元数据。
- 持久化文件 URI。
- EPUB 章节位置。
- 网页和本地内容滚动进度。
- 本地设置。

不持久保存到数据库或长期业务目录：

- 网站账号密码。
- 其他浏览器 Cookie。
- 网页正文副本。
- 完整 TXT/EPUB 内容；EPUB 随机访问所需副本仅存在于可清理的私有临时缓存。
- 用户阅读内容的远程日志。

App 不申请通讯录、相册、位置、无障碍和屏幕录制权限。首版不集成广告、分析和远程崩溃收集 SDK。

## 错误处理

| 场景 | 处理 |
|---|---|
| 分享内容不是 URL | 返回首页并提示支持链接、TXT、EPUB |
| URL 需要登录 | 停止加载，提示导入 TXT/EPUB |
| URL 无法访问 | 显示重试、复制链接和返回 |
| HTTP 链接 | 显示未加密警告，单次确认 |
| TXT 编码不确定 | 让用户选择编码并预览 |
| 文件权限失效 | 重新调用系统文件选择器 |
| EPUB 损坏 | 显示无法解析，不保留半成品缓存 |
| EPUB 加密或 DRM | 明确说明不支持 |
| EPUB 资源越界 | 中止解析并删除会话缓存 |
| 规则应用失败 | 保留规则输入并允许重试 |
| 存储空间不足 | 停止解包，清理本次缓存并提示 |

## Android 体验

- 使用 Jetpack Compose、Material 3、edge-to-edge 和系统动态配色。
- 支持预测性返回手势。
- 阅读页工具栏在滚动时可收起，但不得遮挡正文。
- 不使用悬浮窗权限。
- 不仿制 iOS 导航和控件。
- 字体缩放、TalkBack 和横竖屏切换可用。
- 手机主操作位于拇指可达区域。
- 平板和折叠屏使用自适应双栏，不简单拉伸手机界面。

## 测试策略

### JVM 单元测试

- `InputResolver` 的分享链接和文件类型识别。
- URL 协议、登录路径和危险跳转校验。
- TXT 编码检测和 HTML 转义。
- EPUB 路径归一化、压缩限制和目录解析。
- Room 规则排序、归一化和事务更新。
- 阅读位置序列化。

### 共享规则契约测试

使用同一组 fixtures 同时验证：

- Safari/TypeScript 换名引擎输出。
- Android 注入运行时输出。
- 规则顺序、重叠名称、动态内容和恢复原文一致。

Android 不允许出现与 Safari 不同的规则语义。

### Android 仪器测试

- `ACTION_SEND` 从外部 App 进入阅读页。
- 系统文件选择 TXT、EPUB。
- WebView 加载、返回历史和页面刷新。
- 登录风险提示和危险协议拦截。
- 规则面板新增、编辑、删除和应用。
- 进程重建、旋转和深色模式恢复。
- TalkBack 标签和 `48dp` 触控区域。

### 真机测试

- 夸克分享公开链接。
- 微信分享公开链接。
- 不同编码 TXT。
- 标准 EPUB、多章节 EPUB、带图片 EPUB。
- 损坏、加密和恶意路径 EPUB。
- Android 8、主流当前版本和最新 Android。
- 手机、平板或折叠屏至少各一个窗口尺寸。

## 构建与分发

- Debug APK 使用开发签名，仅供本地测试。
- Release APK 使用独立、长期保管的发布密钥。
- 后续版本必须使用同一密钥才能覆盖升级。
- GitHub Releases 可分发签名 APK 和 SHA-256。
- 正式应用市场可使用 AAB，但 GitHub/网盘直接安装需提供 APK。
- 发布密钥、密码和签名配置不得提交 Git。

首个可安装版本产物：

```text
小说一键换名-android-0.1.0.apk
小说一键换名-android-0.1.0.apk.sha256
```

## 实施分期

### 阶段一：工程与共享运行时

- 建立 Compose 工程、模块和 CI 构建。
- 建立 TypeScript 换名运行时的 Android 构建产物。
- 完成规则契约测试。

### 阶段二：公开网页分享

- 完成分享入口、URL 校验和 WebView 阅读。
- 完成规则面板、规则注入和动态页面替换。
- 完成登录风险与危险导航拦截。

阶段二结束时应能从夸克分享公开链接并完成换名。

### 阶段三：TXT

- 完成系统文件选择、编码检测、分块阅读和进度保存。

### 阶段四：EPUB

- 完成安全解包、目录、章节、资源加载和进度保存。

### 阶段五：发布

- 完成签名 APK、升级验证、安装文档和 GitHub Release。

## 验收标准

首版完成需同时满足：

- 从夸克分享无需登录的网页后，App 可直接打开并应用换名规则。
- 网页字体、颜色和排版由原页面保留，动态加载正文继续替换。
- App 不读取夸克 Cookie，不提供网站登录。
- TXT 和无 DRM EPUB 可通过系统文件选择器打开并应用同一规则。
- EPUB 目录、章节切换和阅读进度可用。
- 删除全部规则并应用后，当前内容恢复原文。
- 规则、历史和进度在 App 重启后保留。
- 恶意或损坏 EPUB 不越界写文件、不执行脚本、不导致 App 崩溃。
- App 不申请无障碍、屏幕录制、通讯录、相册或位置权限。
- Release APK 可安装、重启、覆盖升级，并通过 SHA-256 校验。
