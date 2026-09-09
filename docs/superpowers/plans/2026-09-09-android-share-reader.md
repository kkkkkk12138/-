# Android 分享阅读首版实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建可从夸克等 App 接收公开网页链接、打开本地 TXT/无 DRM EPUB，并复用现有换名规则引擎的原生 Android APK。

**Architecture:** 使用单一 `app` Gradle 模块和清晰 Kotlin package 边界。Compose 管理入口、阅读工具栏、规则和设置；受控 WebView 统一渲染公开网页及本地 TXT/EPUB HTML；浏览器无关的 TypeScript runtime 负责文本替换。Room 保存规则、最近阅读和位置，Storage Access Framework 提供只读文件访问。

**Tech Stack:** Android Studio Quail 3、AGP 9.2.1、Gradle 9.4.1、JDK 17、API 36、Kotlin 内置支持、Jetpack Compose、Material 3、Room、Hilt、AndroidX WebKit、Jsoup、Vitest。

**Design:** `docs/superpowers/specs/2026-09-09-android-share-reader-design.md`

AGP 9.2.1 使用 Gradle 9.4.1、Build Tools 36.0.0 和 JDK 17；版本选择以官方兼容表为准。[$TRAE_REF](https://developer.android.com/build/releases/agp-9-2-0-release-notes) Compose 组件由 BOM 统一约束，Material 3 使用 1.4.0。[$TRAE_REF](https://developer.android.com/develop/ui/compose/bom/bom-mapping)

---

## 固定决策

- `minSdk = 26`，`compileSdk = 36`，`targetSdk = 36`。
- Android application ID：`com.xiaoshuo.yijianhuanming`。
- 首版：`versionName = 0.1.0`，`versionCode = 1`。
- HTTP 仅允许用户对当前顶层链接单次确认；HTTPS 页面禁止混合内容。
- URL 拒绝 localhost、`.local`、环回、链路本地、私网 IPv4/IPv6 字面量。
- 规则 UI 顺序由 `order` 决定；实际替换继续“源文本较长优先，同长度保持 UI 顺序”，与 Safari 一致。
- 网页登录检测只使用导航策略和单向 `evaluateJavascript`；不增加 `addJavascriptInterface`。
- TXT 使用分块同源端点，避免把 20 MiB 内容一次性塞入 DOM。
- EPUB 首版仅支持 reflowable EPUB2/EPUB3；拒绝 fixed-layout、加密和 DRM。
- EPUB 作者脚本和 CSS 不加载；使用内置阅读主题，仅允许会话目录内图片。
- EPUB 缓存总量上限 `512 MiB`，按最近使用淘汰；启动时清理未完成会话。
- Debug APK 是网页分享阶段的首个可安装里程碑；Release APK 等用户提供长期发布密钥后生成。

## 工具链门禁

当前机器未安装 Android SDK，系统 JDK 为 26。开始代码前先安装：

```bash
brew install --cask android-studio
brew install openjdk@17 gradle
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" \
  "build-tools;36.0.0" \
  "platform-tools" \
  "emulator" \
  "system-images;android-36;google_apis;arm64-v8a"
echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name xiaoshuo_api36 \
  --package "system-images;android-36;google_apis;arm64-v8a" \
  --device "pixel_7"
```

验证：

```bash
java -version
adb version
sdkmanager --list_installed
emulator -list-avds
```

预期：JDK 17、Platform 36、Build Tools 36.0.0、platform-tools 和 `xiaoshuo_api36` AVD 可用。若 Android Studio 安装后没有 `cmdline-tools/latest`，先在 SDK Manager 安装 “Android SDK Command-line Tools (latest)”，不要猜测其他路径。

## 文件结构

```text
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/reader/
│       │   ├── java/com/xiaoshuo/yijianhuanming/
│       │   │   ├── MainActivity.kt
│       │   │   ├── NameReplacerApp.kt
│       │   │   ├── intake/
│       │   │   ├── library/
│       │   │   ├── reader/
│       │   │   ├── content/web/
│       │   │   ├── content/txt/
│       │   │   ├── content/epub/
│       │   │   ├── data/
│       │   │   └── di/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── README.md
└── signing/
    └── README.md

src/android-runtime/
├── index.ts
├── runtime.ts
└── types.ts

tests/
├── android-project.test.ts
├── android-runtime.test.ts
└── fixtures/name-replacement-contract.json

scripts/
└── build-android-runtime.mjs
```

---

### Task 1: 工具链与 Compose 空壳

**Files:**
- Create: `tests/android-project.test.ts`
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/proguard-rules.pro`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/NameReplacerApp.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/MainActivity.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/HomeScreen.kt`
- Create: `android/app/src/androidTest/java/com/xiaoshuo/yijianhuanming/HomeScreenTest.kt`
- Modify: `.gitignore`
- Modify: `package.json`

- [ ] **Step 1: 写工程约束失败测试**

```ts
// tests/android-project.test.ts
import { readFile } from 'node:fs/promises';
import { describe, expect, it } from 'vitest';

describe('Android project', () => {
  it('pins the supported SDK and app identity', async () => {
    const build = await readFile('android/app/build.gradle.kts', 'utf8');
    expect(build).toContain('namespace = "com.xiaoshuo.yijianhuanming"');
    expect(build).toContain('compileSdk = 36');
    expect(build).toContain('minSdk = 26');
    expect(build).toContain('targetSdk = 36');
    expect(build).toContain('versionCode = 1');
    expect(build).toContain('versionName = "0.1.0"');
  });

  it('does not request sensitive permissions', async () => {
    const manifest = await readFile('android/app/src/main/AndroidManifest.xml', 'utf8');
    expect(manifest).toContain('android.permission.INTERNET');
    expect(manifest).not.toMatch(/READ_CONTACTS|ACCESS_FINE_LOCATION|SYSTEM_ALERT_WINDOW|BIND_ACCESSIBILITY_SERVICE|RECORD_AUDIO/);
  });
});
```

- [ ] **Step 2: 验证 RED**

Run:

```bash
npx vitest run tests/android-project.test.ts
```

Expected: FAIL，提示 `android/app/build.gradle.kts` 不存在。

- [ ] **Step 3: 创建最小 Android 工程**

使用 AGP `9.2.1`、Gradle Wrapper `9.4.1`、JDK 17、Compose BOM `2026.04.01` 和 Material 3 `1.4.0`。AGP 9 使用内置 Kotlin，不应用 `org.jetbrains.kotlin.android`。其他依赖在 `libs.versions.toml` 锁定，启用 dependency locking 并提交 `gradle.lockfile`；执行时若 KSP 与内置 Kotlin 2.3.10 不兼容，停止而不是降低 AGP 或关闭内置 Kotlin。

`AndroidManifest.xml` 只声明：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<application
    android:name=".NameReplacerApp"
    android:allowBackup="false"
    android:usesCleartextTraffic="true"
    android:theme="@style/Theme.Xiaoshuo">
    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

首页仅渲染“打开网页链接”“打开 TXT / EPUB”和隐私说明。所有主要按钮最小高度 `48.dp`。

- [ ] **Step 4: 创建 Wrapper 并验证 GREEN**

Run:

```bash
cd android
gradle wrapper --gradle-version 9.4.1
cd ..
npx vitest run tests/android-project.test.ts
./android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug
```

Expected: TypeScript 工程约束测试通过，Gradle 输出 `BUILD SUCCESSFUL`，生成 Debug APK。

- [ ] **Step 5: 增加首页 Compose 仪器测试**

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsPrimaryInputsAndPrivacyBoundary() {
        compose.setContent { HomeScreen(onOpenUrl = {}, onOpenDocument = {}) }
        compose.onNodeWithText("打开网页链接").assertIsDisplayed()
        compose.onNodeWithText("打开 TXT / EPUB").assertIsDisplayed()
        compose.onNodeWithText("不登录网站，不上传阅读内容").assertIsDisplayed()
    }
}
```

Run:

```bash
./android/gradlew -p android :app:connectedDebugAndroidTest
```

Expected: 已连接模拟器或真机时通过。

- [ ] **Step 6: 提交**

```bash
git add android tests/android-project.test.ts package.json .gitignore
git commit -m "chore(android): bootstrap compose app"
```

---

### Task 2: 浏览器无关换名运行时

**Files:**
- Create: `src/android-runtime/types.ts`
- Create: `src/android-runtime/runtime.ts`
- Create: `src/android-runtime/index.ts`
- Create: `tests/android-runtime.test.ts`
- Create: `tests/fixtures/name-replacement-contract.json`
- Create: `scripts/build-android-runtime.mjs`
- Modify: `package.json`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: 写 runtime RED 测试**

```ts
import { describe, expect, it } from 'vitest';
import { installNameReplacerRuntime } from '../src/android-runtime/runtime';

describe('Android page runtime', () => {
  it('applies longest sources first and restores from original text', () => {
    document.body.innerHTML = '<p>沈清辞和沈清</p>';
    const runtime = installNameReplacerRuntime(document);
    runtime.applyRules([
      { id: 'short', source: '沈清', target: 'A', order: 0 },
      { id: 'long', source: '沈清辞', target: 'B', order: 1 }
    ]);
    expect(document.body.textContent).toBe('B和A');
    runtime.applyRules([]);
    expect(document.body.textContent).toBe('沈清辞和沈清');
  });

  it('does not expose browser or native capabilities', async () => {
    const bundle = await import('../src/android-runtime/index');
    expect(String(bundle)).not.toMatch(/chrome\\.|browser\\.|addJavascriptInterface|document\\.cookie|fetch\\(/);
  });
});
```

- [ ] **Step 2: 验证 RED**

Run:

```bash
npx vitest run tests/android-runtime.test.ts
```

Expected: FAIL，模块不存在。

- [ ] **Step 3: 实现最小 API**

`runtime.ts` 导出：

```ts
export type ApplyResult =
  | { ok: true; activeRuleCount: number; changedTextNodeCount: number }
  | { ok: false; code: 'NOT_INSTALLED' | 'INVALID_RULES' | 'RUNTIME_ERROR'; message: string };

export interface NameReplacerRuntime {
  install(): void;
  applyRules(rules: OrderedReplaceRule[]): ApplyResult;
  restoreOriginalText(): void;
  dispose(): void;
}
```

复用 `src/content/textEngine.ts`，将 DOM 逻辑与 WebExtension 消息适配分开。应用前按 `source.length` 降序，同长度按 `order` 升序。`index.ts` 只把 API 挂到 `window.__NAME_REPLACER__`。

- [ ] **Step 4: 构建单文件 IIFE**

`scripts/build-android-runtime.mjs` 使用现有 esbuild：

```js
await build({
  entryPoints: ['src/android-runtime/index.ts'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  outfile: 'android/app/build/generated/assets/webRuntime/name-replacer.js',
  minify: true
});
```

在 `package.json` 增加：

```json
"build:android-runtime": "node scripts/build-android-runtime.mjs"
```

Gradle 注册 `buildAndroidRuntime` Exec task，并让 `mergeDebugAssets`、`mergeReleaseAssets` 依赖它；inputs 指向 `src/android-runtime` 与共享引擎，outputs 指向生成 JS。

- [ ] **Step 5: 验证 GREEN 和契约回归**

Run:

```bash
npx vitest run tests/android-runtime.test.ts tests/textEngine.test.ts tests/content-script.test.ts
npm run build:android-runtime
test -f android/app/build/generated/assets/webRuntime/name-replacer.js
```

Expected: 全部通过，bundle 仅生成一份。

- [ ] **Step 6: 提交**

```bash
git add src/android-runtime tests/android-runtime.test.ts tests/fixtures scripts/build-android-runtime.mjs package.json android/app/build.gradle.kts
git commit -m "feat(runtime): expose browser-neutral replacement api"
```

---

### Task 3: Room 规则与阅读会话

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/AppDatabase.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/RuleEntity.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/ReaderSessionEntity.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/RuleDao.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/ReaderSessionDao.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/RuleRepository.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/data/RoomRuleRepository.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/di/AppModule.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/data/RuleNormalizerTest.kt`
- Create: `android/app/src/androidTest/java/com/xiaoshuo/yijianhuanming/data/AppDatabaseTest.kt`

- [ ] **Step 1: 写规则 RED 测试**

```kotlin
class RuleNormalizerTest {
    @Test fun trims_filters_and_keeps_ui_order() {
        val result = normalizeRules(listOf(
            ReplaceRule("1", " 沈清辞 ", " 林惊鹤 ", 4),
            ReplaceRule("2", "", "无效", 1),
            ReplaceRule("3", "顾怀安", "江望舒", 2)
        ))
        assertThat(result.map { it.id }).containsExactly("3", "1").inOrder()
        assertThat(result[1].source).isEqualTo("沈清辞")
    }
}
```

Room 测试覆盖 `replaceAll()` 的事务性、空规则集合和最近阅读倒序。

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*RuleNormalizerTest"
```

Expected: FAIL，模型或函数不存在。

- [ ] **Step 3: 实现数据模型**

`RuleEntity` 包含 `id/source/target/position`；DAO 查询显式 `ORDER BY position ASC`。`replaceAll()` 使用单个 `@Transaction` 删除并写入，失败时回滚。`ReaderSessionEntity` 只存 `sourceId/type/title/uri/chapterId/scrollRatio/lastOpenedAt`。`AppModule` 提供 Room database、DAO 和 repository 接口绑定；`NameReplacerApp` 使用 `@HiltAndroidApp`，`MainActivity` 使用 `@AndroidEntryPoint`。

- [ ] **Step 4: 验证 GREEN**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Expected: 规则和 Room 测试通过。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/xiaoshuo/yijianhuanming/data android/app/src/main/java/com/xiaoshuo/yijianhuanming/di android/app/src/test android/app/src/androidTest
git commit -m "feat(android): persist rules and reader sessions"
```

---

### Task 4: 分享入口、URL 和文件识别

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/intake/ReaderInput.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/intake/InputResolver.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/intake/AndroidInputResolver.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/intake/UrlPolicy.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/intake/UrlPolicyTest.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/intake/InputResolverTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/MainActivity.kt`

- [ ] **Step 1: 写 URL RED 测试**

```kotlin
class UrlPolicyTest {
    private val policy = UrlPolicy()

    @Test fun allows_public_https_and_requires_confirmation_for_http() {
        assertThat(policy.evaluate("https://example.com/book")).isEqualTo(UrlDecision.Allow)
        assertThat(policy.evaluate("http://example.com/book")).isEqualTo(UrlDecision.ConfirmCleartext)
    }

    @Test fun rejects_private_and_dangerous_targets() {
        listOf(
            "http://127.0.0.1", "http://192.168.1.2",
            "http://[::1]", "http://reader.local",
            "file:///sdcard/a.txt", "javascript:alert(1)", "intent://x"
        ).forEach { assertThat(policy.evaluate(it)).isInstanceOf(UrlDecision.Reject::class.java) }
    }
}
```

`InputResolverTest` 覆盖 `ACTION_SEND text/plain` 中唯一 URL、多 URL、无 URL，以及 TXT/EPUB `ACTION_VIEW`。

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*UrlPolicyTest" --tests "*InputResolverTest"
```

Expected: FAIL，输入模型不存在。

- [ ] **Step 3: 实现输入解析**

`ReaderInput` 使用设计文档中的 sealed interface。`ACTION_SEND` 只接受分享文本中唯一的绝对 HTTP/HTTPS URL；不把任意普通文本当网页。`ACTION_OPEN_DOCUMENT` 使用 MIME、扩展名和文件头共同判断 TXT/EPUB，并调用 `takePersistableUriPermission()`；提供方不支持时标记为当前会话访问。

Manifest 新增：

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

- [ ] **Step 4: 验证 GREEN**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest
```

Expected: 输入与 URL 测试通过。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/xiaoshuo/yijianhuanming/intake android/app/src/main/java/com/xiaoshuo/yijianhuanming/MainActivity.kt android/app/src/test
git commit -m "feat(android): resolve shared links and reader files"
```

---

### Task 5: 加固公开网页阅读

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/web/NavigationPolicy.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/web/LoginRiskPolicy.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/web/WebViewProfile.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/web/SecureWebViewClient.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/ReaderWebView.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/WebRuntimeController.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/content/web/NavigationPolicyTest.kt`
- Create: `android/app/src/androidTest/java/com/xiaoshuo/yijianhuanming/content/web/WebViewSecurityTest.kt`

- [ ] **Step 1: 写策略 RED 测试**

覆盖：

```kotlin
@Test fun blocks_login_paths_and_revalidates_redirects() {
    assertThat(policy.evaluate(Uri.parse("https://site.test/login"))).isEqualTo(BlockLogin)
    assertThat(policy.evaluate(Uri.parse("https://site.test/read/1"))).isEqualTo(Allow)
}
```

仪器测试断言：

```kotlin
assertThat(webView.settings.allowFileAccess).isFalse()
assertThat(webView.settings.allowContentAccess).isFalse()
assertThat(webView.settings.mixedContentMode).isEqualTo(WebSettings.MIXED_CONTENT_NEVER_ALLOW)
assertThat(WebView.getCurrentWebViewPackage()).isNotNull()
```

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*NavigationPolicyTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现两个 WebView profile**

`REMOTE_PUBLIC_WEB` 允许受策略约束的网络，关闭文件/内容 URI、地理位置、媒体自动播放、新窗口和下载。`LOCAL_READER` 只允许 `https://appassets.androidplatform.net/`，设置 `blockNetworkLoads = true`。

登录 guard 在 `onPageFinished` 后单向执行：

```js
(() => {
  const password = document.querySelector('input[type=\"password\"]');
  if (!password) return JSON.stringify({ loginRisk: false });
  document.querySelectorAll('form').forEach(form => {
    if (form.querySelector('input[type=\"password\"]')) {
      form.addEventListener('submit', event => event.preventDefault(), { capture: true });
    }
  });
  return JSON.stringify({ loginRisk: true });
})()
```

使用导航 generation token，旧页面的异步回调不得覆盖新页面状态。

- [ ] **Step 4: 实现退出清理**

退出网页会话调用：

```kotlin
CookieManager.getInstance().removeAllCookies(null)
WebStorage.getInstance().deleteAllData()
webView.clearFormData()
webView.clearCache(true)
webView.clearHistory()
```

Release `BuildConfig.DEBUG == false` 时始终 `WebView.setWebContentsDebuggingEnabled(false)`。

- [ ] **Step 5: 验证 GREEN**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Expected: 协议、登录路径、配置和返回历史测试通过。

- [ ] **Step 6: 提交**

```bash
git add android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/web android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader android/app/src/test android/app/src/androidTest
git commit -m "feat(android): add hardened public web reader"
```

---

### Task 6: 规则面板与网页换名 MVP

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/ReaderViewModel.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/ReaderScreen.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/RuleEditorSheet.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/reader/WebRuntimeScriptEncoderTest.kt`
- Create: `android/app/src/androidTest/java/com/xiaoshuo/yijianhuanming/reader/RuleEditorSheetTest.kt`
- Modify: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/WebRuntimeController.kt`

- [ ] **Step 1: 写注入安全 RED 测试**

```kotlin
@Test fun serializes_hostile_rule_text_as_json() {
    val script = encoder.applyRules(listOf(
        ReplaceRule("1", "\"</script>\\n", "\\\\新名", 0)
    ))
    assertThat(script).doesNotContain("applyRules([{\"id\"")
    assertThat(json.decodeFromString<List<ReplaceRule>>(encoder.extractPayload(script))).hasSize(1)
}
```

Compose 测试覆盖摘要列表、单行编辑、添加、删除最后一条后保留“全部生效”、失败保留草稿及 `48dp` 点击区域。

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*WebRuntimeScriptEncoderTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现 ViewModel 状态**

```kotlin
data class RuleEditorState(
    val persisted: List<ReplaceRule> = emptyList(),
    val draft: List<ReplaceRule> = emptyList(),
    val editingRuleId: String? = null,
    val isApplying: Boolean = false,
    val error: String? = null
)
```

只有 Room 保存和 runtime 回调均成功时清除编辑状态。空规则调用 `restoreOriginalText()`。页面刷新、前进后退后重新 `install()` 并应用持久化规则。

- [ ] **Step 4: 验证网页分享里程碑**

Run:

```bash
npm test
npm run build:android-runtime
./android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

手工验证：从夸克分享公开 HTTPS 页面，添加两条规则，刷新和返回后仍生效；登录页被拦截。

- [ ] **Step 5: 提交**

```bash
git add android/app/src src/android-runtime tests scripts package.json
git commit -m "feat(android): apply shared rules to shared web pages"
```

此提交是首个可安装 MVP，保留生成的 Debug APK作为本地验证产物，不提交 `build/`。

---

### Task 7: TXT 编码、分块和本地阅读

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/txt/TxtLimits.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/txt/TxtEncodingDetector.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/txt/TxtChunkStore.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/txt/TxtAssetPathHandler.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/txt/TxtContentSource.kt`
- Create: `android/app/src/main/assets/reader/local-reader.html`
- Create: `android/app/src/main/assets/reader/local-reader.css`
- Create: `android/app/src/main/assets/reader/local-reader.js`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/content/txt/TxtEncodingDetectorTest.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/content/txt/TxtChunkStoreTest.kt`

- [ ] **Step 1: 写 TXT RED 测试**

覆盖 UTF-8、UTF-16LE/BE BOM、GB18030、低置信度、HTML 转义、`20 MiB` 边界、代理对不拆分和分块无重复。

```kotlin
@Test fun escapes_text_and_preserves_chunk_sequence() {
    val chunks = store.write(StringReader("<script>沈清辞&</script>"), 8)
    assertThat(chunks.joinToString("")).isEqualTo("&lt;script&gt;沈清辞&amp;&lt;/script&gt;")
}
```

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*Txt*Test"
```

Expected: FAIL。

- [ ] **Step 3: 实现有限 DOM 流**

先读取最多 `64 KiB` 样本：BOM 优先，否则使用 ICU `CharsetDetector`。低置信度返回候选编码，不自动继续。

`TxtChunkStore` 将转义文本写入 App 私有缓存，每块最大 `128 KiB`。`TxtAssetPathHandler` 只响应当前会话的 `/txt/{session}/{index}`。本地 reader JS 在距底部 2 屏时加载下一块，并最多保留相邻窗口所需 DOM；滚动位置使用字符偏移而非像素。

本地 HTML CSP：

```html
<meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'">
```

- [ ] **Step 4: 验证 GREEN**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Expected: TXT fixtures、滚动追加和恢复位置测试通过。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/txt android/app/src/main/assets/reader android/app/src/test android/app/src/androidTest
git commit -m "feat(android): add bounded txt reader"
```

---

### Task 8: EPUB 安全核心

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubLimits.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/SecureZipExtractor.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubPackageParser.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubHtmlSanitizer.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/CacheManager.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/content/epub/SecureZipExtractorTest.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/content/epub/EpubHtmlSanitizerTest.kt`
- Create: `android/app/src/test/resources/fixtures/epub/`

- [ ] **Step 1: 建立恶意 EPUB RED fixtures**

Fixtures 覆盖：

- 正常 EPUB2/EPUB3。
- `../escape.xhtml`、绝对路径、反斜杠和 NUL。
- 未知 size、超 10,000 条目、超 `500 MiB` 总量、超 `100:1` 压缩比。
- XML DTD/外部实体。
- `encryption.xml` 和 fixed-layout 元数据。
- script、iframe、object、embed、form、事件属性、远程图片和 SVG script。

测试要求任何失败都删除本次会话目录。

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*SecureZipExtractorTest" --tests "*EpubHtmlSanitizerTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现流式安全解包**

目标路径必须使用 canonical path 验证：

```kotlin
val root = sessionDir.canonicalFile
val target = File(root, entry.name.replace('\\', '/')).canonicalFile
require(target.path.startsWith(root.path + File.separator))
```

同时拒绝绝对路径、NUL、未知压缩数据无法计数的异常，并在复制循环中累计实际解压字节和压缩比，不信任 ZIP 元数据。所有入口使用 `try/finally` 清理失败会话。

- [ ] **Step 4: 实现安全解析和清理**

XML parser 禁用 DTD 和外部实体。Jsoup Safelist 只保留阅读所需块级/行内标签与安全属性，移除作者 CSS、表单、iframe、脚本、事件属性和远程 URL；内部图片重写为受控 appassets URL。

- [ ] **Step 5: 验证 GREEN**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest
```

Expected: 正常 EPUB 通过，所有恶意 fixture 被结构化拒绝且无残留缓存。

- [ ] **Step 6: 提交**

```bash
git add android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub android/app/src/test
git commit -m "feat(android): validate and sanitize epub content"
```

---

### Task 9: EPUB 目录、章节和进度

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubModels.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubNavigationParser.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubResourceResolver.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubAssetPathHandler.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub/EpubContentSource.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/ContentsSheet.kt`
- Create: `android/app/src/test/java/com/xiaoshuo/yijianhuanming/content/epub/EpubNavigationParserTest.kt`
- Create: `android/app/src/androidTest/java/com/xiaoshuo/yijianhuanming/reader/EpubReaderTest.kt`

- [ ] **Step 1: 写章节 RED 测试**

覆盖 manifest/spine 顺序、EPUB3 nav、EPUB2 NCX、无目录降级、相对路径、片段链接、上一/下一章边界和位置往返。

```kotlin
@Test fun follows_spine_order_when_navigation_is_missing() {
    val book = parser.parse(fixture("epub/no-nav.epub"))
    assertThat(book.chapters.map { it.href }).containsExactly("c1.xhtml", "c2.xhtml").inOrder()
    assertThat(book.tableOfContents).isEmpty()
}
```

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest --tests "*EpubNavigationParserTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现受控章节加载**

`EpubDocument` 只暴露章节 ID、标题、前后关系和 appassets URL。`EpubAssetPathHandler` 只映射当前会话白名单文件；章节 HTML 注入内置主题、严格 CSP 和换名 runtime。

目录缺失时隐藏目录入口但保留 spine 前后章导航。阅读位置保存 `chapterId + scrollRatio`。

- [ ] **Step 4: 验证 GREEN**

Run:

```bash
./android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Expected: 目录、章节、图片、规则和重启恢复测试通过。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/xiaoshuo/yijianhuanming/content/epub android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader android/app/src/test android/app/src/androidTest
git commit -m "feat(android): add epub navigation and progress"
```

---

### Task 10: 最近阅读、设置和自适应体验

**Files:**
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/library/LibraryViewModel.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/library/RecentReadingList.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/ReaderSettingsSheet.kt`
- Create: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/navigation/AppNavHost.kt`
- Create: `android/app/src/androidTest/java/com/xiaoshuo/yijianhuanming/AdaptiveReaderTest.kt`
- Modify: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/HomeScreen.kt`
- Modify: `android/app/src/main/java/com/xiaoshuo/yijianhuanming/reader/ReaderScreen.kt`

- [ ] **Step 1: 写状态 RED 测试**

覆盖：

- 清历史不删除原文件。
- 清 EPUB 缓存不删除规则和历史。
- 清规则恢复当前页原文。
- 进程重建恢复输入类型和位置。
- 手机使用底部抽屉，宽屏使用支持侧栏。
- TalkBack 标签、字体缩放、动态配色和预测性返回。

- [ ] **Step 2: 验证 RED**

Run:

```bash
./android/gradlew -p android :app:connectedDebugAndroidTest
```

Expected: 新 UI 状态测试失败。

- [ ] **Step 3: 实现 Material 3 自适应 UI**

使用 edge-to-edge、`WindowSizeClass`/Material 3 Adaptive 和系统动态色。手机使用底部 sheet；宽屏显示目录或规则 supporting pane。所有删除操作使用明确确认对话框，设置页分别提供清网页数据、历史、规则和 EPUB 缓存。

CacheManager 在启动时删除 `.incomplete` 会话目录；完整缓存按最近使用淘汰，总量不超过 `512 MiB`。

- [ ] **Step 4: 完整回归**

Run:

```bash
npm test
npm run build:android-runtime
./android/gradlew -p android :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug
```

Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
git add android
git commit -m "feat(android): finish adaptive reader and local library"
```

---

### Task 11: 发布门禁与签名 APK

**Files:**
- Create: `android/signing/README.md`
- Create: `android/README.md`
- Create: `docs/android/PRIVACY.md`
- Create: `docs/android/INSTALL.md`
- Create: `docs/android/THIRD_PARTY_NOTICES.md`
- Create: `scripts/verify-android-release.mjs`
- Create: `tests/android-release.test.ts`
- Create: `.github/workflows/android.yml`
- Modify: `.gitignore`
- Modify: `android/app/build.gradle.kts`
- Modify: `package.json`

- [ ] **Step 1: 写发布 RED 测试**

```ts
describe('Android release', () => {
  it('keeps signing secrets out of git and exposes release verification', async () => {
    const ignore = await readFile('.gitignore', 'utf8');
    const pkg = JSON.parse(await readFile('package.json', 'utf8'));
    expect(ignore).toMatch(/\\.jks|\\.keystore/);
    expect(pkg.scripts['android:release:verify']).toContain('verify-android-release.mjs');
  });
});
```

验证脚本检查：

- Release manifest 权限只有 `INTERNET`。
- APK 使用 release 证书签名。
- APK 中只存在一份 `name-replacer.js`。
- WebView debug 在 release 关闭。
- 文件名为 `小说一键换名-android-0.1.0.apk`。
- 同目录生成 `.sha256`。
- Android 隐私、安装和第三方依赖许可文档存在。

- [ ] **Step 2: 验证 RED**

Run:

```bash
npx vitest run tests/android-release.test.ts
```

Expected: FAIL。

- [ ] **Step 3: 配置安全签名**

`android/signing/README.md` 只记录四个环境变量名称，不记录值：

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

`build.gradle.kts` 通过 `System.getenv()` 读取以上变量。`*.jks`、`*.keystore` 必须被 `.gitignore` 排除。Release 缺少任一变量时明确失败，不回退到 debug key。

发布密钥由用户确定保管者、备份位置和密码；助手不得把密钥或密码写入仓库、日志或文档。

- [ ] **Step 4: 添加 CI**

GitHub Actions 使用 JDK 17、Node 26、Android SDK 36，运行：

```bash
npm ci
npm test
npm run build:android-runtime
./android/gradlew -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

CI 不持有 release keystore。

`docs/android/PRIVACY.md` 明确不读取其他浏览器 Cookie、不提供网站登录、不上传网页/文件/规则/历史；`docs/android/INSTALL.md` 写明 APK 未知来源授权、SHA-256 校验和覆盖升级；`docs/android/THIRD_PARTY_NOTICES.md` 列出 AndroidX、Compose、Room、Hilt、Jsoup 及其许可证。

- [ ] **Step 5: 构建并验证候选包**

在用户配置本地 release keystore 后运行：

```bash
./android/gradlew -p android clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
npm run android:release:verify
apksigner verify --verbose --print-certs "dist/小说一键换名-android-0.1.0.apk"
```

使用测试设备执行真实升级：

```bash
adb install "dist/小说一键换名-android-0.1.0.apk"
# 构建 versionCode=2 的本地升级候选后
adb install -r "dist/小说一键换名-android-0.1.0-upgrade-test.apk"
```

Expected: 规则、最近阅读和位置在覆盖升级后保留。升级测试包仅用于本地验证，不发布、不提交。

- [ ] **Step 6: 最终验收**

手工验证：

- 夸克分享公开 HTTPS 链接。
- HTTP 单次警告，HTTPS 混合内容被拒绝。
- 登录页和密码提交被阻止。
- UTF-8、GB18030 TXT。
- EPUB2、EPUB3、图片、无目录、损坏、DRM、路径穿越。
- Android 8、当前主流 Android、API 36。
- 手机与宽屏。

- [ ] **Step 7: 提交**

```bash
git add android docs/android scripts/verify-android-release.mjs tests/android-release.test.ts .github/workflows/android.yml package.json .gitignore
git commit -m "build(android): produce verified signed apk"
```

---

## 全局验证命令

每个任务提交前至少运行受影响测试；最终运行：

```bash
npm test
npm run build:android-runtime
./android/gradlew -p android clean \
  :app:testDebugUnitTest \
  :app:connectedDebugAndroidTest \
  :app:lintRelease \
  :app:assembleDebug
```

用户提供 release 签名材料后再追加：

```bash
./android/gradlew -p android :app:assembleRelease
npm run android:release:verify
```

## 提交历史

预期提交顺序：

```text
chore(android): bootstrap compose app
feat(runtime): expose browser-neutral replacement api
feat(android): persist rules and reader sessions
feat(android): resolve shared links and reader files
feat(android): add hardened public web reader
feat(android): apply shared rules to shared web pages
feat(android): add bounded txt reader
feat(android): validate and sanitize epub content
feat(android): add epub navigation and progress
feat(android): finish adaptive reader and local library
build(android): produce verified signed apk
```

不得 squash、amend 已推送提交或 force push。每个阶段使用独立功能分支或连续可回溯提交。
