# 小说一键换名 Safari 原生宿主工程设计

## 目标

将现有 WebExtension 封装为 Apple 官方 Safari Web Extension 工程，覆盖 iPhone、iPad 和 Mac。普通用户从 App Store 安装一次，在 Safari 中启用扩展后，继续在原有阅读网页中使用换名功能。

本次工作只增加 Apple 平台的安装、启用和分发外壳，不重写换名引擎。

## 用户体验

宿主 App 采用系统极简 SwiftUI 界面。首次打开只呈现：

- 产品用途说明
- Safari 扩展启用状态
- 三步启用提示
- 前往 Safari 设置或打开 Safari 的主操作
- 简短的本地处理与隐私说明

用户完成一次启用后，日常操作全部发生在 Safari 阅读页面。宿主 App 不承担规则管理、小说导入或独立阅读功能。

## 工程架构

新增 `apple/小说一键换名/`，由 Apple 官方 `safari-web-extension-packager` 从 `build/safari-upload/` 生成。

工程包含：

- iOS、iPadOS、macOS 通用 SwiftUI 宿主 App
- Safari Web Extension target
- 由 WebExtension 构建产物同步的扩展资源
- Apple 平台图标与基础元数据

现有 TypeScript 项目继续作为业务源码源头：

```text
src/
  ↓ npm run build
build/safari-upload/
  ↓ npm run apple:sync
apple/小说一键换名/ 中的扩展资源
  ↓ Xcode
iPhone / iPad / Mac App + Safari Extension
```

WebExtension 是唯一业务实现。SwiftUI 宿主层不复制规则存储、DOM 扫描或文本替换逻辑。

## 生成方式

使用 Xcode 26.6 自带的 `safari-web-extension-packager`：

- App 名称：`小说一键换名`
- Swift 语言
- 同时生成 iOS 与 macOS target
- 将 WebExtension 资源复制进工程
- 生成过程不自动打开 Xcode，便于脚本化验证

生成后的工程纳入 Git 管理，后续不要求开发者每次重新执行 Packager。扩展源码更新时只同步资源，避免覆盖已定制的 SwiftUI 页面和 Xcode 配置。

## 宿主界面

### 首屏

首屏使用 Apple 系统组件，不引入第三方 UI 依赖。信息结构为：

1. 产品图标与名称
2. 一句话用途说明
3. 三步启用说明
4. 主操作按钮
5. 本地处理说明

建议文案：

- 标题：`小说一键换名`
- 说明：`在 Safari 看小说时，把角色名换成你想看的名字。`
- 提示：`启用一次后，日常使用都在 Safari 阅读页面中完成。`

### 平台差异

- iPhone、iPad：引导用户进入系统中的 Safari 扩展设置；如果系统不允许直接跳转，则显示准确的手动路径。
- Mac：提供打开 Safari 的操作，并提示进入 `Safari > 设置 > 扩展` 启用。

平台差异集中在原生导航辅助层，页面视觉和文案保持一致。

## 扩展行为

沿用现有能力：

- `原名 → 新名` 规则编辑
- 当前网页即时替换
- 动态加载内容持续替换
- 修改规则后按原始文本重算
- 跳过输入框、按钮、代码块等非正文区域
- 保持原网页字体、字号、颜色和 DOM 样式继承

扩展继续使用 Safari 支持的 WebExtension API。平台差异通过现有 `browserApi` 适配层处理。

## 权限与数据

- 扩展只在用户授权的网站上工作
- 换名规则保存在 Safari 扩展本地存储
- 小说正文只在设备本地扫描和替换
- 不要求账号
- 不上传正文
- 不做跨站追踪

宿主 App 不申请与核心功能无关的系统权限。

## 构建与同步

新增命令：

- `npm run apple:generate`：首次生成 Apple 工程
- `npm run apple:sync`：构建 WebExtension 并同步资源到现有 Xcode 工程
- `npm run apple:build:ios`：无签名编译 iOS Simulator target
- `npm run apple:build:macos`：无签名编译 macOS target
- `npm run apple:verify`：运行 WebExtension 测试、构建和两个 Apple target 的基础验证

同步脚本只更新 Safari Extension 资源，不覆盖：

- SwiftUI 宿主代码
- Xcode signing 设置
- Bundle Identifier
- App 图标配置
- 项目级发布配置

## 标识与签名

默认 Bundle Identifier 使用可替换占位前缀：

- App：`com.xiaoshuo.yijianhuanming`
- Extension：`com.xiaoshuo.yijianhuanming.Extension`

首次生成阶段关闭自动签名依赖，以便本地和 CI 做模拟器编译。真机、TestFlight 和 App Store 构建时，再由项目所有者选择 Apple Developer Team 并确认最终 Bundle Identifier。

仓库不得提交：

- 开发者证书
- Provisioning Profile
- Apple 账号信息
- App Store Connect 密钥

## 错误处理

- 未生成 `build/safari-upload/` 时，生成与同步脚本应先执行 WebExtension 构建。
- Apple 工程不存在时，`apple:sync` 应明确提示先运行 `apple:generate`。
- Xcode 或 Packager 不可用时，脚本应输出缺失依赖，而不是生成半成品目录。
- 资源同步失败时保留原工程，避免清空已工作的扩展资源。
- 无签名编译失败时保留完整日志，并区分代码错误、SDK 缺失和签名问题。

## 测试

### 自动验证

- 现有 23 项 WebExtension 测试继续通过
- WebExtension 构建成功
- 资源同步前后关键文件一致
- iOS Simulator target 无签名编译通过
- macOS target 无签名编译通过
- Bundle Identifier、显示名称和扩展元数据一致

### 手工验证

- iPhone Simulator 启动宿主 App
- iPad Simulator 布局无截断
- Mac 启动宿主 App
- Safari 能识别扩展 target
- 未启用扩展时引导文案准确
- 启用并授权网站后，真实阅读网页可换名

真机签名、TestFlight 和 App Store 上传不属于本轮自动执行范围，因为需要项目所有者的 Apple Developer Team。

## 仓库范围

纳入版本管理：

- Apple Xcode 工程
- SwiftUI 宿主源码
- Safari Extension 资源
- 构建与同步脚本
- 测试和开发文档

忽略：

- Xcode DerivedData
- 用户级 Xcode 设置
- 签名资料
- Archive 和导出安装包
- 临时构建日志

## 完成标准

本轮完成时应满足：

- 仓库中存在可打开的 Xcode 工程
- iPhone、iPad、Mac 共用同一套 WebExtension 核心
- 宿主 App 为系统极简 SwiftUI 界面
- 两个平台 target 均能无签名构建
- 一条命令可以同步后续 WebExtension 更新
- 不引入账号、服务器或独立阅读器
