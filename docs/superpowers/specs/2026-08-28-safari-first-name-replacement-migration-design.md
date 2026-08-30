# 小说一键换名 Safari 首发迁移设计稿

日期：2026-08-28

## 目标

将当前以 `Chrome Manifest V3` 为中心的 `小说一键换名` 项目，改写为一条以 `Safari Web Extension` 为首发形态的产品路线。
新的首发目标不再是“先做桌面 Chrome 插件，再考虑其他平台”，而是：

- 首发可通过 `App Store` 正式分发
- `iPhone`、`iPad`、`Mac` 都可用
- 核心阅读体验仍保持“无感替换”
- 尽量复用现有 WebExtension 内核，而不是重写为独立阅读器

这次迁移不是单纯换一个打包目标，而是把产品主线从“桌面浏览器插件”改成“移动端可分发的网页阅读换名工具”。

## 为什么改成 Safari 首发

当前项目已经完成了一个可工作的 WebExtension 内核：`popup + content script + storage + 文本节点替换引擎`。  
问题不在于插件不能用，而在于原先选择的 `Chrome Web Store` 路线，在开发者注册支付环节已经形成实际阻塞。

与之相比，Apple 官方明确支持 Safari 扩展在 `iPhone`、`iPad`、`Mac` 上运行，并通过 `App Store` 分发；同时，Apple 也明确支持把现有 WebExtension 直接转换或上传到 Safari 的打包流程中，而不是要求从零重写原生扩展 [$TRAE_REF](https://developer.apple.com/safari/extensions/)[$TRAE_REF](https://developer.apple.com/documentation/safariservices/safari-web-extensions)。

更关键的是，Safari 已经把移动端扩展安装和启用做成了官方路径。用户可以在 iPhone 上通过 `App Store` 获取扩展，并在 Safari 的扩展设置中开启 [$TRAE_REF](https://support.apple.com/zh-cn/guide/iphone/-iphab0432bf6/ios)。  
这和你的真实目标是对齐的：不是“先有一个桌面插件”，而是“能正式上架，而且手机端可用”。

## 产品定位修正

迁移后，产品定义应从：

`一个支持任意网页的 Chrome 换名插件`

修正为：

`一个在 Safari 阅读场景中工作的角色名称替换扩展，首发覆盖 iPhone、iPad、Mac，后续再兼容 Chromium 桌面生态`

这个修正有三个现实意义：

- 首发成功标准从“Chrome 商店通过”改成“App Store 可分发且手机端可用”
- 交互入口要从“浏览器工具栏弹窗”改写为“Safari 扩展入口 + 页面菜单入口”
- 权限策略要从“默认全网页长期覆盖”改成“更符合 Safari 用户授权心智的当前页触发型”

## 当前项目评估

现有代码结构如下：

- `src/popup/`：规则编辑弹窗
- `src/content/`：DOM 过滤、文本替换、动态监听
- `src/shared/`：规则、消息、存储模型
- `manifest.json`：标准 MV3 清单
- `scripts/build.mjs`：输出 `dist/` 与 `chrome-load/`

这说明项目已经具备较强的“跨浏览器内核”特征。  
真正带有 Chrome 偏向的部分，主要不是替换引擎本身，而是：

- `manifest` 的权限策略
- `tabs` 驱动的当前标签页通信逻辑
- `chrome-load/` 这类面向桌面手工加载的交付形态
- 面向 Chrome Web Store 的商店素材与文案

所以迁移策略应当是：

- 保留 `WebExtension` 核心
- 抽离 Chrome 特定打包和分发假设
- 增加 Safari 首发所需的包装层、权限策略和移动端交互说明

## 非目标

本次迁移设计明确不做以下方向：

- 不把产品改造成独立阅读器 App
- 不在第一版引入云同步、账号系统或角色库
- 不承诺安卓手机同时获得同等级的正式分发路径
- 不以夸克或其他国产 Chromium 浏览器作为首发主通路
- 不为了迁移 Safari 而重写核心文本替换逻辑

这几个不做很重要。  
如果一边要 Safari 首发，一边又想同步解决安卓公开分发、云同步、跨平台账户体系，就会把一个本来可以迁移的项目拉成新项目。

## 平台策略

### 首发平台

首发平台定义为：

- `iPhone Safari`
- `iPad Safari`
- `Mac Safari`

这三端共享同一个产品名、同一套规则语义和同一个扩展能力边界。

### 次级平台

次级平台保留为：

- `Chrome 桌面`
- 其他 Chromium 桌面浏览器

这些平台在迁移后的角色是“复用内核的桌面兼容分发”，而不是首发路线。

### 暂不承诺的平台

以下平台不放入当前公开承诺中：

- 安卓手机浏览器扩展生态
- 夸克公开插件上架

原因不是完全不能适配，而是当前没有 Safari 这样明确、稳定、可审核的公开分发依据。

## 核心迁移原则

### 保留 WebExtension 内核

现有项目的真正价值，不是 `manifest v3` 这个壳，而是内部已经做出的“无感文本节点替换系统”。  
因此迁移时，必须优先保留这些部分：

- 规则数据模型
- 文本节点过滤逻辑
- 原文快照与重算机制
- `MutationObserver` 动态替换能力
- 极简规则编辑 UI

### 打包层与运行层解耦

现有项目把“构建产物”和“Chrome 手动加载目录”绑得比较紧。  
迁移后，应该把项目拆成两层：

- `webextension-core`：浏览器无关的扩展核心资源
- `distribution-layer`：Safari 首发包装与 Chromium 补充分发

简单说，未来真正的“源产物”不应该叫 `chrome-load/`，而应该是一个中性的 WebExtension 包。

### 产品语义服从 Safari 授权模型

Safari 对扩展权限的处理更强调用户逐站点授权和显式授予。  
Apple 官方建议优先使用 `activeTab`、精确的 host permissions 与 optional permissions，只有在没有其他选择时才使用 `<all_urls>` [$TRAE_REF](https://developer.apple.com/documentation/safariservices/managing-safari-web-extension-permissions)。

这刚好和产品原本的理想交互一致：  
用户本来就不是要“默认全网自动替换”，而是“在当前阅读页点开扩展后再生效”。

因此，Safari 首发不是在削弱产品，而是在逼产品回到更正确的权限哲学。

## 目标架构

迁移后的架构分为三层。

### 第一层：共享 WebExtension 内核

这一层继续保存现有核心逻辑：

- `shared`：规则、消息、存储模型
- `content`：文本过滤、原文缓存、替换与动态监听
- `popup`：规则编辑与当前页触发

这里的目标不是大改，而是把所有 `chrome.*` 直接耦合的部分收口，改成更稳定的扩展 API 适配层。

### 第二层：浏览器适配层

新增一个很薄的适配层，用来隔离浏览器差异。  
建议新增目录，例如：

- `src/platform/browserApi.ts`

由它统一提供：

- 读取当前活动页面
- 向当前页面发送消息
- 读写本地存储
- 处理 Safari 与 Chromium 的运行时差异

这样做的好处是，后续如果还要保留 Chrome 桌面兼容版，不需要在 `popup` 和 `content` 里到处打补丁。

### 第三层：Safari 分发包装层

Safari 首发后，需要的是一个 `Safari Web Extension` 包装层，而不是 Chrome 的解压目录。

Apple 官方现在支持两条路径：

- 用 `Xcode` 将现有 WebExtension 转成 Safari 扩展宿主 App
- 或直接通过 `App Store Connect` 的 Safari Web Extension Packager 上传完整扩展文件进行打包、走 `TestFlight` 测试，再上 `App Store` [$TRAE_REF](https://developer.apple.com/documentation/safariservices/packaging-and-distributing-safari-web-extensions-with-app-store-connect)

本项目的设计建议是：

- 产品层面按 `App Store Connect 打包可行` 来设计
- 工程层面仍保留 `Xcode` 路径作为后备和调试手段

原因很简单：  
App Store Connect 的网页打包路径更贴近你的“先保证能上架”的目标，而 Xcode 更适合作为开发期与本地调试期的补充。

## 构建产物改写

### 现状问题

当前项目输出：

- `dist/`
- `chrome-load/`

这两个目录都明显带有 Chrome 时代的语义。  
如果继续沿用，团队会在认知上默认“Safari 只是附加适配”，而不是首发主线。

### 目标产物

建议将构建目标改为：

- `build/webextension/`：唯一的浏览器无关核心扩展包
- `build/chromium-load/`：从核心包派生的桌面手工加载目录
- `build/safari-upload/`：面向 Safari 打包上传的整理目录

其中，`build/webextension/` 才是源头。  
`build/chromium-load/` 和 `build/safari-upload/` 都只是针对不同分发链路的包装。

### 为什么这样改

因为 Safari 首发后，产品真正的“标准产物”应该是：

- 一份完整的 WebExtension 文件集合
- 能被 Safari 打包器直接识别
- 同时仍能回流给 Chromium 桌面版本使用

这个结构会让项目从“Chrome 插件项目”变成“跨浏览器扩展项目，Safari 先发”。

## 权限模型改写

### 现状

当前 `manifest.json` 使用：

- `storage`
- `tabs`
- `host_permissions: <all_urls>`

这在 Chrome 上合理，但在 Safari 首发语境下有两个问题：

- `<all_urls>` 容易放大审核与用户感知上的风险
- `tabs + 直接全局匹配` 过于偏桌面浏览器心智

### 建议方向

Safari 首发版的权限策略，建议改成：

- 保留 `storage`
- 以 `activeTab` 或更克制的 host 授权为主
- 把“当前页点击后生效”做成主交互
- 把“用户逐站点允许”当成产品正常流程，而不是异常状态

这与 Apple 官方推荐是对齐的：优先使用最小权限，并让用户对单站点访问保持可理解的控制 [$TRAE_REF](https://developer.apple.com/documentation/safariservices/managing-safari-web-extension-permissions)。

### 产品层面的变化

这意味着产品文案也要跟着变：

- 不再默认表述为“任何网页都自动可用”
- 改为“在你当前打开的阅读网页中启用”
- 当 Safari 尚未授权某个网站时，给出明确提示，而不是静默失败

## 移动端交互改写

### 现状问题

当前交互默认用户从桌面浏览器工具栏点击扩展图标打开弹窗。  
这套心智在 iPhone 上不成立，因为 Safari 扩展的入口通常不在常驻工具栏里，而在 Safari 的页面菜单和扩展管理流中。

Apple 官方用户路径明确是：在 iPhone 上通过 App Store 安装扩展，然后在 Safari 设置或页面菜单中启用和管理扩展 [$TRAE_REF](https://support.apple.com/zh-cn/guide/iphone/-iphab0432bf6/ios)。

### 设计结论

迁移后需要把产品交互写成两套入口，但保持一套规则心智。

#### 桌面 Safari

桌面 Safari 仍可保留“点击扩展入口 -> 编辑规则 -> 当前页生效”的结构。  
这部分与现有 popup 逻辑最接近，迁移成本低。

#### iPhone / iPad Safari

移动端应默认用户从 Safari 的扩展入口进入插件界面。  
因此 UI 上需要补一层“首次使用引导”和“未授权网站提示”，而不能假设用户天然知道如何把扩展固定在自己的操作流里。

### 必须新增的移动端认知设计

第一版 Safari 首发方案里，必须额外补这些内容：

- 第一次安装后如何在 Safari 中开启扩展
- 当前网站尚未授权时如何授予权限
- 规则已保存，但本页未获授权时为什么没有生效

换句话说，移动端真正新增的不是“更多功能”，而是“权限与入口解释层”。

## 数据与同步策略

第一版仍然坚持：

- 规则本地保存
- 不引入账号
- 不做跨端云同步

这在 Safari 首发阶段反而更有利。  
因为一旦引入账号系统，你就不仅是在做 Safari 扩展，而是在做一个带后端的跨端产品，审核、隐私、支持成本都会明显上升。

如果后续确实要做跨端同步，应该放在 Safari 首发稳定之后，再评估用：

- `iCloud` 相关能力
- 或浏览器无关的自建同步后端

但这些都不属于当前首发设计。

## 商店与分发改写

### 上架对象

迁移后，上架对象不再是“浏览器插件条目”，而是：

- 一个包含 Safari 扩展能力的 App Store 应用

这意味着商店素材也要整体改写。  
原来的 `Chrome Web Store` 包里可复用的是产品定位、隐私口径和截图脚本思路，但商店字段、安装说明、权限表述都要转成 App Store 语境。

### 首发分发流程

建议的首发路径是：

1. 产出完整 `WebExtension` 核心包
2. 在 `App Store Connect` 创建 App 记录
3. 上传扩展完整文件给 Safari Web Extension Packager
4. 先通过 `TestFlight` 做 iPhone / iPad / Mac 真实测试
5. 再提交 `App Store` 审核

Apple 官方已经把这条路径讲得很清楚，且明确支持“无需 Mac 或 Xcode，也可以在网页端完成打包与分发” [$TRAE_REF](https://developer.apple.com/documentation/safariservices/packaging-and-distributing-safari-web-extensions-with-app-store-connect)。

这个事实会直接影响工程判断：

- 你现在不必先把项目改造成原生 App
- 先把 WebExtension 核心包做干净，价值最高

## 对现有代码的具体影响

### 基本保留

这些模块应视为迁移资产：

- `src/content/domFilter.ts`
- `src/content/textEngine.ts`
- `src/content/index.ts`
- `src/shared/rules.ts`
- `src/shared/storage.ts`
- `src/shared/types.ts`

这些文件承载的是产品真正的差异能力，迁移时应优先保留。

### 需要重构

这些区域需要改写：

- `manifest.json`：从 Chrome 优先改成 Safari/跨浏览器兼容优先
- `src/popup/index.ts`：加入 Safari 首发的授权提示与移动端入口解释
- `scripts/build.mjs`：从 `dist + chrome-load` 改成中性核心包 + 分发派生包
- `README.md`：从 Chrome 安装说明改成 Safari 首发说明
- `listing/chrome-web-store/`：后续要补一套 App Store 对应上架包

### 新增模块建议

建议新增：

- `src/platform/browserApi.ts`
- `src/popup/onboarding.ts`
- `docs/superpowers/specs/...-app-store-listing-design.md`（后续）

其中最先该补的是 `browserApi.ts`。  
因为只要浏览器适配层不收口，后面每改一次平台，就会反复碰到消息通信和权限行为差异。

## 测试策略改写

Safari 首发后，测试目标不能只剩“桌面网页替换是否生效”，而要变成三层。

### 内核测试

继续保留现有单测和 DOM 测试，验证：

- 文本节点过滤
- 长词优先替换
- 原文快照与重算
- 动态节点新增后的继续替换

### 浏览器适配测试

新增验证：

- 当前页消息发送在 Safari 适配层下仍然成立
- 权限未授予时，popup 能返回明确状态
- 授权后同一页可再次立即生效

### 分发链路测试

新增手工验证：

- `App Store Connect` 打包上传能识别 manifest 与资源
- `TestFlight` 安装后 iPhone / iPad / Mac 可启用扩展
- iPhone 上真实网页可触发规则编辑和当前页替换

这部分测试的重点不再只是“代码对不对”，而是“产品路径是否真实成立”。

## 风险与边界

### 风险一：移动端入口更深

Safari 在 iPhone 上的扩展入口天然比桌面浏览器更深。  
这会带来首轮使用摩擦，因此必须靠产品引导来补，而不是假设用户会自己摸索。

### 风险二：权限授予更显性

Safari 对网站权限授予更显性，这会让“点开就换”的链路多一步确认。  
但这不是纯负担，它也让产品更符合“当前页触发”的真实心智。

### 风险三：动态长文在移动端的性能

现有替换引擎在桌面上已经可用，但移动端 Safari 的长文、论坛、富前端页面在性能上更敏感。  
因此 Safari 首发后，需要把“移动端长文不卡顿”当作真实验收项，而不是只看桌面烟测。

### 风险四：上架对象从扩展变成 App

一旦走 App Store，用户看到的是一个应用，而不是浏览器商店里的插件条目。  
这会影响命名、截图、隐私政策、功能说明和用户预期，后续必须单独补一版 App Store 上架包。

## 迁移后的成功标准

Safari 首发迁移成功，不是指“代码能跑在 Safari 里”。  
而是同时满足下面几件事：

- 现有无感替换内核保留下来
- 可以产出中性的 WebExtension 核心包
- 可以通过 Safari 打包路径进入 `TestFlight`
- iPhone、iPad、Mac 上能完成安装、启用与当前页替换
- 产品说明已经从 Chrome 插件叙事切换成 Safari 首发叙事

## 建议路线

建议按下面顺序推进：

### 第一阶段：架构去 Chrome 化

- 抽出浏览器适配层
- 改写构建产物命名
- 让 `webextension-core` 成为唯一源头

### 第二阶段：补 Safari 首发体验层

- 改 popup 文案与授权提示
- 增加移动端入口解释
- 调整权限策略

### 第三阶段：走真实分发链路

- 准备 Safari 打包上传目录
- 走 `App Store Connect` 打包
- 用 `TestFlight` 做三端实测

### 第四阶段：再回补 Chromium

- 保留 Chrome 桌面版作为兼容产物
- 把它视为共享内核的额外分发渠道
- 不让它继续主导产品路线

## 结论

当前项目最值得保留的，不是它“已经是 Chrome 插件”，而是它已经长出了一个相当正确的阅读替换内核。  
Safari 首发的价值，在于它第一次让这个内核和你的真实产品目标对齐了：正式上架、手机端可用、且不必重做成另一个产品。

因此，这次迁移的正确姿势不是“顺手兼容 Safari”，而是：

先把项目从 Chrome 思维里解耦，再把 Safari 作为正式首发平台来设计产品、构建和分发。  
只有这样，后续 Chrome 和其他 Chromium 浏览器才会变成你的复用收益，而不是反过来拖住主线。
