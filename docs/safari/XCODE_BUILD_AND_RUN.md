# Xcode 构建与运行

## 环境要求

- macOS
- Xcode 16 或更高版本
- Node.js 18 或更高版本
- npm 9 或更高版本

项目文件：

```text
apple/小说一键换名/小说一键换名.xcodeproj
```

## 准备工程

在仓库根目录执行：

```bash
npm install
npm run apple:sync
open "apple/小说一键换名/小说一键换名.xcodeproj"
```

`apple:sync` 会重新构建 WebExtension，并将 `build/safari-upload/` 同步到 Xcode 工程的 Extension Resources。修改网页扩展代码后，应再次执行该命令。

## Scheme

Xcode 提供两个可运行 scheme：

- `小说一键换名 (iOS)`：用于 iPhone、iPad 和 iOS Simulator
- `小说一键换名 (macOS)`：用于 Mac

本地无签名验证：

```bash
npm run apple:verify
```

该命令依次运行 Vitest、同步扩展资源，并完成 iOS Simulator 与 macOS 构建。

## 签名与 Bundle Identifier

真机运行、归档或上传 TestFlight 前：

1. 在 Xcode 项目导航器中选择项目 `小说一键换名`
2. 依次选择 iOS/macOS 的 App 和 Extension targets
3. 打开 **Signing & Capabilities**
4. 为每个 target 选择自己的 Apple Developer Team
5. 确认 Xcode 能自动管理签名

当前 Bundle Identifier：

- App：`com.xiaoshuo.yijianhuanming`
- Extension：`com.xiaoshuo.yijianhuanming.Extension`

若这些标识已被其他开发者账号占用，应同时调整关联 targets，并确保宿主 App 与 Extension 的配置保持一致。

## 运行与启用扩展

### iPhone / iPad

1. 在 Xcode 中选择 `小说一键换名 (iOS)`
2. 选择模拟器或已签名的真机，点击 Run
3. 打开系统设置中的 **Apps > Safari > Extensions**
4. 选择“小说一键换名”并启用
5. 在 Safari 打开阅读网页，通过页面菜单中的扩展入口使用
6. 按需允许扩展访问当前网站

### Mac

1. 在 Xcode 中选择 `小说一键换名 (macOS)`
2. 选择 **My Mac**，点击 Run
3. 打开 Safari 的 **设置 > 扩展**
4. 勾选“小说一键换名”
5. 在阅读网页中通过 Safari 工具栏或页面菜单打开扩展
6. 按需允许扩展访问当前网站

## 发布安全

证书、描述文件和 App Store Connect API Key 都属于本地或 CI 密钥，不得提交到 Git：

- 不提交签名证书或其导出文件
- 不提交 `*.mobileprovision`
- 不提交 App Store Connect 私钥（如 `AuthKey_*.p8`）
- 不提交包含密钥、密码或会话令牌的本地配置
- 不提交 `xcuserdata/`、DerivedData、archive、IPA 或 dSYM 等本地产物

发布凭据应存放在 Keychain、Xcode 账号配置或 CI 的加密 Secret 中。
