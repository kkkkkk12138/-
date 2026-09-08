import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { assessTestFlightReadiness } from '../scripts/testflight-preflight.mjs';

const roots: string[] = [];

async function tempRoot(): Promise<string> {
  const root = await mkdtemp(join(tmpdir(), 'xiaoshuo-testflight-'));
  roots.push(root);
  return root;
}

function pngHeader(width: number, height: number, colorType: number): Buffer {
  const buffer = Buffer.alloc(26);
  buffer.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], 0);
  buffer.write('IHDR', 12, 'ascii');
  buffer.writeUInt32BE(width, 16);
  buffer.writeUInt32BE(height, 20);
  buffer[24] = 8;
  buffer[25] = colorType;
  return buffer;
}

async function writeFixture(
  root: string,
  options?: { version?: string; colorType?: number; hasEncryptionDeclaration?: boolean }
) {
  const iconDir = join(
    root,
    'apple/小说一键换名/Shared (App)/Assets.xcassets/AppIcon.appiconset'
  );
  const projectDir = join(root, 'apple/小说一键换名/小说一键换名.xcodeproj');
  const infoDir = join(root, 'apple/小说一键换名/iOS (App)');
  await Promise.all([
    mkdir(iconDir, { recursive: true }),
    mkdir(projectDir, { recursive: true }),
    mkdir(infoDir, { recursive: true }),
    mkdir(join(root, 'docs/safari'), { recursive: true })
  ]);

  await writeFile(
    join(root, 'manifest.json'),
    JSON.stringify({ version: options?.version ?? '0.2.0' })
  );
  await writeFile(
    join(projectDir, 'project.pbxproj'),
    'MARKETING_VERSION = 0.2.0;\nCURRENT_PROJECT_VERSION = 1;\n'
  );
  await writeFile(join(root, 'docs/safari/PRIVACY_POLICY.md'), '# 隐私政策\n');
  await writeFile(join(root, 'docs/safari/TESTFLIGHT_BETA.md'), '# TestFlight 内测资料\n');
  await writeFile(
    join(infoDir, 'Info.plist'),
    options?.hasEncryptionDeclaration === false
      ? '<plist><dict></dict></plist>'
      : '<plist><dict><key>ITSAppUsesNonExemptEncryption</key><false/></dict></plist>'
  );
  await writeFile(
    join(iconDir, 'universal-icon-1024@1x.png'),
    pngHeader(1024, 1024, options?.colorType ?? 2)
  );
}

afterEach(async () => {
  await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe('TestFlight preflight', () => {
  it('accepts matching metadata and an opaque 1024 icon', async () => {
    const root = await tempRoot();
    await writeFixture(root);

    const result = await assessTestFlightReadiness(root);

    expect(result.errors).toEqual([]);
    expect(result.version).toBe('0.2.0');
    expect(result.buildNumber).toBe('1');
  });

  it('reports version mismatch and alpha-channel icons', async () => {
    const root = await tempRoot();
    await writeFixture(root, { version: '0.3.0', colorType: 6 });

    const result = await assessTestFlightReadiness(root);

    expect(result.errors).toContain('Xcode MARKETING_VERSION 必须与 manifest.json version 一致');
    expect(result.errors).toContain('App Store 1024 图标不能包含 Alpha 通道');
  });

  it('requires an export-compliance declaration', async () => {
    const root = await tempRoot();
    await writeFixture(root, { hasEncryptionDeclaration: false });

    const result = await assessTestFlightReadiness(root);

    expect(result.errors).toContain('iOS App 必须声明不使用非豁免加密');
  });
});
