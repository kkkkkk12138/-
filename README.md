# 小说一键换名

Safari 首发的阅读换名扩展。目标是在当前阅读网页里无感替换角色名字，并尽量保持原网页的字体、字号、颜色与排版不变。

普通用户通过 App Store 安装正式产品；GitHub 仓库仅用于开发、构建与发布。

## 项目结构

- `apple/小说一键换名/`：iOS 与 macOS Safari Extension 的 Xcode 工程
- `build/webextension/`：标准 WebExtension 核心产物
- `build/safari-upload/`：同步到 Xcode 工程的 Safari 扩展资源
- `build/chromium-load/`：Chromium 桌面手工加载目录

## 安装依赖

要求：

- Node.js 18+
- npm 9+

安装命令：

```bash
npm install
```

## Safari 开发

首次拉取仓库后：

```bash
npm install
npm run apple:sync
open "apple/小说一键换名/小说一键换名.xcodeproj"
```

`apple:sync` 会先构建 WebExtension，再将最新资源同步到 Xcode 工程。真机调试或 TestFlight 构建前，开发者还需在 Xcode 的 Signing & Capabilities 中为 App 和 Extension targets 选择自己的 Apple Developer Team。

完整的 Xcode 构建、签名与 Safari 启用步骤见 `docs/safari/XCODE_BUILD_AND_RUN.md`。

## WebExtension 构建

```bash
npm run build
```

构建后会刷新三类产物：

- `build/webextension/`
- `build/safari-upload/`
- `build/chromium-load/`

## Safari 首发测试

1. 执行 `npm run apple:verify`
2. 执行 `npm run testflight:preflight`
3. 在 Xcode 中选择 iOS Simulator 或 macOS scheme 运行
4. 真机或归档前选择 Apple Developer Team
5. 通过 Xcode Archive 上传 TestFlight，在 iPhone、iPad、Mac 上测试

补充说明见 `docs/safari/XCODE_BUILD_AND_RUN.md` 和 `docs/safari/APP_STORE_CONNECT_UPLOAD.md`。

## Chromium 桌面兼容测试

1. 执行 `npm run chromium:ready`
2. 在 Chrome 扩展页选择 `build/chromium-load/`

说明：

- `build/chromium-load/` 是兼容验证路径，不再作为首发主链路
- 如果只验证 Safari 上架链路，优先关注 `build/safari-upload/`

## 核心使用

1. 打开阅读网页
2. 打开扩展入口
3. 输入 `原名 -> 替换成`
4. 对当前页生效

实现原则：

- 规则默认保存在浏览器本地
- 当前版本不依赖账号系统
- 页面替换处理在本地完成
- Safari 下以“当前网站授权 + 当前页触发”为主心智

## 手工烟测

1. 验证普通正文节点会替换
2. 验证输入框、按钮、代码块不替换
3. 修改规则后确认当前页按原文重算
4. 验证动态新增内容继续替换
5. 在 Safari 里验证未授权网站时会出现明确提示

## 自动化验证

```bash
npm test
```

目标：

- 全量测试通过
- Safari 首发元数据、标题与文档口径一致

## 当前已知边界

- 若替换后的名字长度差异较大，网页换行可能发生正常重排
- Canvas、Shadow DOM、图片文字、部分极端前端框架渲染内容不保证覆盖
- 如果用户把高频普通词当作规则源文本，仍可能产生误替换
