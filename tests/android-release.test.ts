import { access, readFile } from 'node:fs/promises';
import { describe, expect, it } from 'vitest';

const signingVariables = [
  'ANDROID_KEYSTORE_PATH',
  'ANDROID_KEYSTORE_PASSWORD',
  'ANDROID_KEY_ALIAS',
  'ANDROID_KEY_PASSWORD',
];

describe('Android release', () => {
  it('keeps signing secrets out of git and exposes release verification', async () => {
    const ignore = await readFile('.gitignore', 'utf8');
    const pkg = JSON.parse(await readFile('package.json', 'utf8'));

    expect(ignore).toMatch(/\*\.jks/);
    expect(ignore).toMatch(/\*\.keystore/);
    expect(pkg.scripts['android:release:verify']).toContain('verify-android-release.mjs');
  });

  it('requires all release signing environment variables without a debug fallback', async () => {
    const build = await readFile('android/app/build.gradle.kts', 'utf8');

    for (const variable of signingVariables) {
      expect(build).toContain(`System.getenv("${variable}")`);
    }
    expect(build).toContain('GradleException');
    expect(build).not.toMatch(/signingConfig\s*=\s*signingConfigs\.getByName\("debug"\)/);
  });

  it('explicitly disables WebView debugging in release builds', async () => {
    const application = await readFile(
      'android/app/src/main/java/com/xiaoshuo/yijianhuanming/NameReplacerApp.kt',
      'utf8',
    );

    expect(application).toContain('WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)');
  });

  it('verifies only INTERNET among Android platform permissions', async () => {
    const verifier = await readFile('scripts/verify-android-release.mjs', 'utf8');

    expect(verifier).toContain(
      ".filter((permission) => permission.startsWith('android.permission.'))",
    );
    expect(verifier).toContain("'android.permission.INTERNET'");
  });

  it('ships CI and Android release documentation', async () => {
    await Promise.all([
      access('.github/workflows/android.yml'),
      access('android/README.md'),
      access('android/signing/README.md'),
      access('docs/android/PRIVACY.md'),
      access('docs/android/INSTALL.md'),
      access('docs/android/THIRD_PARTY_NOTICES.md'),
      access('scripts/verify-android-release.mjs'),
    ]);
  });

  it('runs JVM, migration, Compose, WebView, lint, and debug assembly gates in CI', async () => {
    const workflow = await readFile('.github/workflows/android.yml', 'utf8');

    expect(workflow).toContain('branches: [main]');
    expect(workflow).toContain(':app:testDebugUnitTest');
    expect(workflow).toContain(
      'com.xiaoshuo.yijianhuanming.data.AppDatabaseTest',
    );
    expect(workflow).toContain(
      'com.xiaoshuo.yijianhuanming.content.web.WebViewSecurityTest',
    );
    expect(workflow).toContain(
      'com.xiaoshuo.yijianhuanming.AdaptiveReaderTest',
    );
    expect(workflow).toContain(':app:lintDebug');
    expect(workflow).toContain(':app:assembleDebug');
    expect(workflow).toContain('run_suite "Room migration"');
    expect(workflow).toContain('run_suite "WebView instrumentation"');
    expect(workflow).toContain('run_suite "Compose instrumentation"');
    expect(workflow).toContain('run_suite "Full instrumentation"');
    expect(workflow).toContain('::error title=${suite} failed');
    expect(workflow).toContain('api-level: 35');
    expect(workflow).toContain('target: google_apis');
  });

  it('publishes the verified APK to a GitHub prerelease for Android beta tags', async () => {
    const workflow = await readFile('.github/workflows/android.yml', 'utf8');

    expect(workflow).toContain("tags: ['v*-android-beta']");
    expect(workflow).toContain('softprops/action-gh-release@v2');
    expect(workflow).toContain('prerelease: true');
    expect(workflow).toContain(
      'android/app/build/outputs/apk/debug/app-debug.apk',
    );
  });
});
