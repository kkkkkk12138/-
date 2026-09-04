# Safari Native Host Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate and maintain an Apple-official Safari Web Extension Xcode project for 小说一键换名, with one minimal SwiftUI host experience across iPhone, iPad, and Mac.

**Architecture:** Keep the TypeScript WebExtension as the only business implementation. Apple’s `safari-web-extension-packager` creates the four native targets, a Node script synchronizes built extension resources without touching signing or host code, and the generated shared view controller embeds a native SwiftUI onboarding view.

**Tech Stack:** TypeScript, Node.js, Vitest, Safari WebExtension Manifest V3, Swift, SwiftUI, SafariServices, Xcode 26.6, `xcodebuild`

---

## File map

**Create**

- `scripts/apple-project.mjs` — generate, validate, and atomically synchronize the Apple project.
- `tests/apple-project.test.ts` — cover resource synchronization and missing-project failures.
- `tests/apple-host.test.ts` — assert host copy, privacy language, and platform actions remain present.
- `apple/小说一键换名/小说一键换名.xcodeproj/` — generated Xcode project.
- `apple/小说一键换名/Shared (App)/ViewController.swift` — shared SwiftUI host and platform bridge.
- `docs/safari/XCODE_BUILD_AND_RUN.md` — local build, simulator, signing, and TestFlight handoff.

**Modify**

- `package.json` — add Apple generation, sync, build, and verification commands.
- `.gitignore` — ignore Xcode user state, DerivedData, archives, and local signing artifacts.
- `README.md` — make the Xcode project the primary Safari development entry.

**Generated and committed**

- `apple/小说一键换名/Shared (Extension)/Resources/` — synchronized WebExtension runtime files.
- Remaining files under `apple/小说一键换名/` — Apple-generated target metadata and assets.

## Task 1: Add tested Apple project synchronization

**Files**

- Create: `tests/apple-project.test.ts`
- Create: `scripts/apple-project.mjs`
- Modify: `package.json`

- [ ] **Step 1: Write failing synchronization tests**

Create `tests/apple-project.test.ts`:

```ts
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import {
  getApplePaths,
  syncExtensionResources,
  verifyAppleProject
} from '../scripts/apple-project.mjs';

const roots: string[] = [];

async function tempRoot() {
  const root = await mkdtemp(join(tmpdir(), 'xiaoshuo-apple-'));
  roots.push(root);
  return root;
}

afterEach(async () => {
  await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe('Apple project helpers', () => {
  it('uses stable generated project paths', () => {
    const paths = getApplePaths('/repo');
    expect(paths.projectDir).toBe('/repo/apple/小说一键换名');
    expect(paths.projectFile).toBe('/repo/apple/小说一键换名/小说一键换名.xcodeproj');
    expect(paths.extensionResources).toBe(
      '/repo/apple/小说一键换名/Shared (Extension)/Resources'
    );
  });

  it('replaces extension resources and removes stale files', async () => {
    const root = await tempRoot();
    const source = join(root, 'build/safari-upload');
    const target = join(root, 'apple/小说一键换名/Shared (Extension)/Resources');
    await mkdir(source, { recursive: true });
    await mkdir(target, { recursive: true });
    await writeFile(join(source, 'manifest.json'), '{"name":"小说一键换名"}');
    await writeFile(join(source, 'content.js'), 'fresh');
    await writeFile(join(target, 'stale.js'), 'stale');

    await syncExtensionResources({ source, target });

    expect(await readFile(join(target, 'content.js'), 'utf8')).toBe('fresh');
    await expect(readFile(join(target, 'stale.js'), 'utf8')).rejects.toThrow();
  });

  it('rejects synchronization when the Xcode project is missing', async () => {
    const root = await tempRoot();
    await expect(verifyAppleProject(root)).rejects.toThrow(
      '请先运行 npm run apple:generate'
    );
  });
});
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
npx vitest run tests/apple-project.test.ts
```

Expected: FAIL because `scripts/apple-project.mjs` does not exist.

- [ ] **Step 3: Implement generation and atomic resource synchronization**

Create `scripts/apple-project.mjs`:

```js
import { cp, mkdir, mkdtemp, rename, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const APP_NAME = '小说一键换名';
const BUNDLE_ID = 'com.xiaoshuo.yijianhuanming';

export function getApplePaths(projectRoot = process.cwd()) {
  const root = resolve(projectRoot);
  const projectDir = join(root, 'apple', APP_NAME);
  return {
    root,
    projectDir,
    projectFile: join(projectDir, `${APP_NAME}.xcodeproj`),
    sourceResources: join(root, 'build', 'safari-upload'),
    extensionResources: join(projectDir, 'Shared (Extension)', 'Resources')
  };
}

async function exists(path) {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

export async function verifyAppleProject(projectRoot = process.cwd()) {
  const paths = getApplePaths(projectRoot);
  if (!(await exists(paths.projectFile))) {
    throw new Error('Apple 工程不存在，请先运行 npm run apple:generate');
  }
  return paths;
}

export async function syncExtensionResources({ source, target }) {
  if (!(await exists(source))) {
    throw new Error(`Safari 扩展构建产物不存在：${source}`);
  }

  await mkdir(dirname(target), { recursive: true });
  const stagingRoot = await mkdtemp(join(tmpdir(), 'xiaoshuo-extension-sync-'));
  const staged = join(stagingRoot, 'Resources');

  try {
    await cp(source, staged, { recursive: true });
    await rm(target, { recursive: true, force: true });
    await rename(staged, target);
  } finally {
    await rm(stagingRoot, { recursive: true, force: true });
  }
}

export async function generateAppleProject(projectRoot = process.cwd()) {
  const paths = getApplePaths(projectRoot);
  if (await exists(paths.projectFile)) {
    throw new Error(`Apple 工程已存在：${paths.projectFile}`);
  }

  await execFileAsync('npm', ['run', 'build:safari-first'], { cwd: paths.root });
  await mkdir(join(paths.root, 'apple'), { recursive: true });
  await execFileAsync(
    'xcrun',
    [
      'safari-web-extension-packager',
      '--project-location',
      join(paths.root, 'apple'),
      '--app-name',
      APP_NAME,
      '--bundle-identifier',
      BUNDLE_ID,
      '--swift',
      '--copy-resources',
      '--no-open',
      '--no-prompt',
      paths.sourceResources
    ],
    { cwd: paths.root }
  );

  return verifyAppleProject(paths.root);
}

export async function syncAppleProject(projectRoot = process.cwd()) {
  const paths = await verifyAppleProject(projectRoot);
  await execFileAsync('npm', ['run', 'build:safari-first'], { cwd: paths.root });
  await syncExtensionResources({
    source: paths.sourceResources,
    target: paths.extensionResources
  });
  return paths;
}

const command = process.argv[2];
if (command === 'generate') {
  await generateAppleProject();
} else if (command === 'sync') {
  await syncAppleProject();
} else if (fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  throw new Error('用法：node scripts/apple-project.mjs <generate|sync>');
}
```

- [ ] **Step 4: Add npm commands**

Add to `package.json`:

```json
"apple:generate": "node scripts/apple-project.mjs generate",
"apple:sync": "node scripts/apple-project.mjs sync"
```

- [ ] **Step 5: Run focused and full tests**

Run:

```bash
npx vitest run tests/apple-project.test.ts
npm test
```

Expected: the new 3 tests pass and the existing 23 tests remain green.

- [ ] **Step 6: Commit**

```bash
git add scripts/apple-project.mjs tests/apple-project.test.ts package.json package-lock.json
git commit -m "build: add Safari project synchronization"
```

## Task 2: Generate the official Apple project

**Files**

- Create: `apple/小说一键换名/`

- [ ] **Step 1: Generate from the current Safari build**

Run:

```bash
npm run apple:generate
```

Expected output includes:

```text
App Name: 小说一键换名
App Bundle Identifier: com.xiaoshuo.yijianhuanming
Platform: All
Language: Swift
```

- [ ] **Step 2: Verify all four targets and two schemes**

Run:

```bash
xcodebuild \
  -project "apple/小说一键换名/小说一键换名.xcodeproj" \
  -list
```

Expected targets:

```text
小说一键换名 (iOS)
小说一键换名 (macOS)
小说一键换名 Extension (iOS)
小说一键换名 Extension (macOS)
```

Expected schemes:

```text
小说一键换名 (iOS)
小说一键换名 (macOS)
```

- [ ] **Step 3: Verify resource identity**

Run:

```bash
cmp \
  "build/safari-upload/manifest.json" \
  "apple/小说一键换名/Shared (Extension)/Resources/manifest.json"
```

Expected: exit code `0`.

- [ ] **Step 4: Commit the generated baseline**

```bash
git add "apple/小说一键换名"
git commit -m "build: generate Safari extension project"
```

## Task 3: Replace the generated host with a system-minimal SwiftUI experience

**Files**

- Create: `tests/apple-host.test.ts`
- Modify: `apple/小说一键换名/Shared (App)/ViewController.swift`

- [ ] **Step 1: Write a failing host-content test**

Create `tests/apple-host.test.ts`:

```ts
import { readFile } from 'node:fs/promises';
import { describe, expect, it } from 'vitest';

const hostPath = 'apple/小说一键换名/Shared (App)/ViewController.swift';

describe('Safari native host', () => {
  it('uses SwiftUI and keeps the host focused on Safari activation', async () => {
    const source = await readFile(hostPath, 'utf8');
    expect(source).toContain('import SwiftUI');
    expect(source).toContain('在 Safari 看小说时，把角色名换成你想看的名字。');
    expect(source).toContain('启用一次后，日常使用都在 Safari 阅读页面中完成。');
    expect(source).toContain('规则保存在本机，小说正文不会被主动上传。');
  });

  it('contains native activation actions for iOS and macOS', async () => {
    const source = await readFile(hostPath, 'utf8');
    expect(source).toContain('UIApplication.openSettingsURLString');
    expect(source).toContain('SFSafariApplication.showPreferencesForExtension');
  });
});
```

- [ ] **Step 2: Run the host test and verify it fails**

Run:

```bash
npx vitest run tests/apple-host.test.ts
```

Expected: FAIL because the generated host still uses `WKWebView`.

- [ ] **Step 3: Replace the generated view controller**

Replace `apple/小说一键换名/Shared (App)/ViewController.swift` with:

```swift
import SwiftUI
import SafariServices
import WebKit

#if os(iOS)
import UIKit
typealias PlatformViewController = UIViewController
#elseif os(macOS)
import AppKit
typealias PlatformViewController = NSViewController
#endif

private let extensionBundleIdentifier = "com.xiaoshuo.yijianhuanming.Extension"

private enum SafariActivation {
    static func open() {
#if os(iOS)
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
#elseif os(macOS)
        SFSafariApplication.showPreferencesForExtension(
            withIdentifier: extensionBundleIdentifier
        ) { _ in }
#endif
    }
}

private struct ActivationStep: View {
    let number: Int
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(String(number))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(Color.accentColor, in: Circle())

            Text(text)
                .font(.body)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct HostAppView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image("LargeIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96, height: 96)
                    .accessibilityHidden(true)

                VStack(spacing: 8) {
                    Text("小说一键换名")
                        .font(.title2.weight(.semibold))

                    Text("在 Safari 看小说时，把角色名换成你想看的名字。")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 16) {
                    ActivationStep(number: 1, text: "在 Safari 设置中启用“小说一键换名”")
                    ActivationStep(number: 2, text: "允许扩展访问正在阅读的网站")
                    ActivationStep(number: 3, text: "回到小说页面，点扩展入口设置名字")
                }
                .padding()
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 16))

                Button("去启用 Safari 扩展", action: SafariActivation.open)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)

                Text("启用一次后，日常使用都在 Safari 阅读页面中完成。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                Divider()

                Label(
                    "规则保存在本机，小说正文不会被主动上传。",
                    systemImage: "hand.raised"
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
    }
}

final class ViewController: PlatformViewController {
    @IBOutlet private var webView: WKWebView!

    override func viewDidLoad() {
        super.viewDidLoad()
        webView.removeFromSuperview()

#if os(iOS)
        let host = UIHostingController(rootView: HostAppView())
        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        host.didMove(toParent: self)
#elseif os(macOS)
        let host = NSHostingController(rootView: HostAppView())
        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
#endif
    }
}
```

- [ ] **Step 4: Run the host tests**

Run:

```bash
npx vitest run tests/apple-host.test.ts
```

Expected: 2 tests pass.

- [ ] **Step 5: Compile both host targets without signing**

Run:

```bash
xcodebuild \
  -project "apple/小说一键换名/小说一键换名.xcodeproj" \
  -scheme "小说一键换名 (iOS)" \
  -destination "generic/platform=iOS Simulator" \
  -derivedDataPath "build/xcode-ios" \
  CODE_SIGNING_ALLOWED=NO build

xcodebuild \
  -project "apple/小说一键换名/小说一键换名.xcodeproj" \
  -scheme "小说一键换名 (macOS)" \
  -destination "platform=macOS" \
  -derivedDataPath "build/xcode-macos" \
  CODE_SIGNING_ALLOWED=NO build
```

Expected: both end with `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add tests/apple-host.test.ts "apple/小说一键换名/Shared (App)/ViewController.swift"
git commit -m "feat: add minimal Safari activation host"
```

## Task 4: Add reproducible Apple build commands

**Files**

- Modify: `package.json`
- Modify: `tests/apple-project.test.ts`

- [ ] **Step 1: Add a failing package-script assertion**

Append to `tests/apple-project.test.ts`:

```ts
it('exposes reproducible Apple build commands', async () => {
  const packageJson = JSON.parse(
    await readFile(join(process.cwd(), 'package.json'), 'utf8')
  );
  expect(packageJson.scripts['apple:build:ios']).toContain('iOS Simulator');
  expect(packageJson.scripts['apple:build:macos']).toContain('platform=macOS');
  expect(packageJson.scripts['apple:verify']).toContain('apple:sync');
});
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
npx vitest run tests/apple-project.test.ts
```

Expected: FAIL because the three scripts are absent.

- [ ] **Step 3: Add build and verification scripts**

Add to `package.json`:

```json
"apple:build:ios": "xcodebuild -project \"apple/小说一键换名/小说一键换名.xcodeproj\" -scheme \"小说一键换名 (iOS)\" -destination \"generic/platform=iOS Simulator\" -derivedDataPath \"build/xcode-ios\" CODE_SIGNING_ALLOWED=NO build",
"apple:build:macos": "xcodebuild -project \"apple/小说一键换名/小说一键换名.xcodeproj\" -scheme \"小说一键换名 (macOS)\" -destination \"platform=macOS\" -derivedDataPath \"build/xcode-macos\" CODE_SIGNING_ALLOWED=NO build",
"apple:verify": "npm test && npm run apple:sync && npm run apple:build:ios && npm run apple:build:macos"
```

- [ ] **Step 4: Run the complete Apple verification**

Run:

```bash
npm run apple:verify
```

Expected:

- all Vitest tests pass
- extension resources synchronize
- iOS build succeeds
- macOS build succeeds

- [ ] **Step 5: Commit**

```bash
git add package.json package-lock.json tests/apple-project.test.ts
git commit -m "build: add Apple verification commands"
```

## Task 5: Protect local Apple state and document the developer handoff

**Files**

- Modify: `.gitignore`
- Modify: `README.md`
- Create: `docs/safari/XCODE_BUILD_AND_RUN.md`

- [ ] **Step 1: Extend `.gitignore`**

Append:

```gitignore
DerivedData/
*.xcarchive
*.ipa
*.dSYM
xcuserdata/
*.xcuserstate
*.mobileprovision
```

- [ ] **Step 2: Update the README primary Safari path**

Document these commands:

```bash
npm install
npm run apple:sync
open "apple/小说一键换名/小说一键换名.xcodeproj"
```

State clearly:

- ordinary users receive the product from App Store
- GitHub is only for development
- developers select their Apple Developer Team in Xcode before real-device or TestFlight builds

- [ ] **Step 3: Write the Xcode handoff guide**

Create `docs/safari/XCODE_BUILD_AND_RUN.md` with:

- required Xcode version
- project path
- iOS Simulator and macOS schemes
- `npm run apple:verify`
- where to select the Apple Developer Team
- Bundle Identifier values
- how to enable the extension in Safari
- explicit statement that certificates, profiles, and App Store Connect keys must not be committed

- [ ] **Step 4: Run documentation and repository checks**

Run:

```bash
git diff --check
git status --short
npm run apple:verify
```

Expected: no whitespace errors, only intended files changed, all verification steps pass.

- [ ] **Step 5: Commit**

```bash
git add .gitignore README.md docs/safari/XCODE_BUILD_AND_RUN.md
git commit -m "docs: add Safari Xcode handoff"
```

## Task 6: Final regression and push

**Files**

- Verify only; modify files only if a failing check exposes a defect.

- [ ] **Step 1: Confirm Xcode project metadata**

Run:

```bash
xcodebuild \
  -project "apple/小说一键换名/小说一键换名.xcodeproj" \
  -list
```

Expected: four targets and two schemes from Task 2.

- [ ] **Step 2: Confirm synchronized extension metadata**

Run:

```bash
node -e "
const fs=require('fs');
const source=JSON.parse(fs.readFileSync('manifest.json'));
const apple=JSON.parse(fs.readFileSync('apple/小说一键换名/Shared (Extension)/Resources/manifest.json'));
if(source.name!==apple.name || source.version!==apple.version) process.exit(1);
"
```

Expected: exit code `0`.

- [ ] **Step 3: Run final verification**

Run:

```bash
npm run apple:verify
git status -sb
```

Expected: all tests and builds pass; branch contains no uncommitted implementation changes.

- [ ] **Step 4: Push**

```bash
git push origin main
```

Expected: the Safari Xcode project and supporting scripts are available in the private GitHub repository.
