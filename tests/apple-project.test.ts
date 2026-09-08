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

  it('exposes reproducible Apple build commands', async () => {
    const packageJson = JSON.parse(
      await readFile(join(process.cwd(), 'package.json'), 'utf8')
    );
    expect(packageJson.scripts['apple:build:ios']).toContain('iOS Simulator');
    expect(packageJson.scripts['apple:build:macos']).toContain('platform=macOS');
    expect(packageJson.scripts['apple:verify']).toContain('apple:sync');
    expect(packageJson.scripts['testflight:preflight']).toContain('testflight-preflight.mjs');
  });
});
