# Safari Web Extension 上传说明

## 目标

把 `build/safari-upload/` 作为 Safari Web Extension Packager 的输入目录，先走 TestFlight，再进入 App Store 审核。

## 上传路径

1. 登录 App Store Connect
2. 创建 App 记录，平台选择 iOS 与 macOS；如果当前只先做移动端验证，至少保留 iOS
3. 打开对应 App 的 Xcode Cloud 标签页
4. 进入 Safari Web Extension Packager
5. 点击 Upload
6. 上传 `build/safari-upload/` 中的完整扩展文件
7. 等待网页端打包完成
8. 通过 TestFlight 分发到 iPhone、iPad、Mac 做真实设备验证
9. 验证通过后再提交 App Store 审核

## 上传前检查

- `build/safari-upload/manifest.json` 存在
- `build/safari-upload/popup.html`、`popup.js`、`content.js`、`styles.css` 存在
- `build/safari-upload/icons/` 目录完整
- `manifest.json` 版本号已更新
- 权限说明、README、弹窗标题与 Safari 首发文案一致

## 为什么上传 `build/safari-upload/`

- `build/webextension/` 是标准核心产物，适合工程内部复用
- `build/chromium-load/` 带有桌面手工加载说明，服务于 Chromium 兼容验证
- `build/safari-upload/` 才是面向 Safari 打包上传整理后的稳定输入目录

原则上不要把 Chromium 手工加载目录直接当作 Safari 上传源，否则会混入错误的分发语义。
