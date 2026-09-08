# Safari Web Extension 上传说明

## 当前发布路径

项目已经生成并维护完整 Xcode 工程，因此 TestFlight 使用 Xcode Archive 上传，不再把 `build/safari-upload/` 直接上传到网页打包器。

`build/safari-upload/` 仍是 Safari 扩展资源的标准输入目录。运行 `npm run apple:sync` 后，这些资源会复制到 Xcode 工程，再由 iOS App 和 Safari Extension 两个 target 一起签名、归档和上传。

## 账号准备

1. 加入 Apple Developer Program。
2. 在 Xcode 的 Settings → Accounts 中登录付费开发者账号。
3. 在 iOS App 和 iOS Extension 两个 target 的 Signing & Capabilities 中选择付费 Team。
4. 确认 Bundle ID：
   - App：`com.xiaoshuo.yijianhuanming`
   - Extension：`com.xiaoshuo.yijianhuanming.Extension`

免费 Personal Team 只能真机开发测试，不能上传 TestFlight。

## App Store Connect

1. 在 App Store Connect 创建新 App。
2. 平台选择 iOS。
3. 名称填写“小说一键换名”。
4. Bundle ID 选择 `com.xiaoshuo.yijianhuanming`。
5. SKU 可使用 `xiaoshuo-yijian-huanming-ios`。
6. 在 App Privacy 中按 `docs/safari/TESTFLIGHT_BETA.md` 填写“数据未收集”。
7. 填写公开支持邮箱和隐私政策 URL。

首次 TestFlight 先只上传 iOS。macOS 版本在 iPhone/iPad 内测稳定后再添加，减少第一次审核变量。

## 本地预检

```bash
npm install
npm run apple:verify
npm run testflight:preflight
```

预检会验证：

- Xcode 与 WebExtension 版本一致。
- 构建号为统一正整数。
- 1024 App Store 图标为无 Alpha 的 PNG。
- 隐私政策和 TestFlight 文案存在。
- iOS App 已声明不使用非豁免加密。

## Xcode 归档

1. 执行 `npm run apple:sync`。
2. 打开 `apple/小说一键换名/小说一键换名.xcodeproj`。
3. Scheme 选择“小说一键换名 (iOS)”。
4. 运行目标选择“Any iOS Device (arm64)”。
5. 执行 Product → Archive。
6. 在 Organizer 中选择归档，点击 Distribute App。
7. 选择 App Store Connect → Upload。
8. 保持自动管理签名，完成验证并上传。

也可以在付费 Team 配置完成后使用命令行生成归档：

```bash
xcodebuild \
  -project "apple/小说一键换名/小说一键换名.xcodeproj" \
  -scheme "小说一键换名 (iOS)" \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath "build/archive/小说一键换名.xcarchive" \
  archive
```

上传动作仍建议首次通过 Xcode Organizer 完成，便于直接查看签名和 App Store 校验错误。

## TestFlight 配置

构建处理完成后：

1. 在 TestFlight 页面选择新构建。
2. 填写 `docs/safari/TESTFLIGHT_BETA.md` 中的 Beta 描述、测试重点和审核说明。
3. 先添加内部测试者，验证安装、网站授权和规则替换。
4. 需要邀请团队外用户时，创建外部测试组并提交 Beta App Review。
5. 外部审核通过后，再生成公开邀请链接或发送邮件邀请。

同一 App 版本重新上传时必须递增 `CURRENT_PROJECT_VERSION`，否则 App Store Connect 会拒绝重复构建号。

## 发布边界

- GitHub 和网盘中的 `.ipa` 不能替代 TestFlight。
- `build/chromium-load/` 只用于桌面 Chromium 手动测试。
- `build/safari-upload/` 是构建输入，不是普通用户安装包。
- 普通 iPhone/iPad 用户最终通过 TestFlight 或 App Store 安装。
