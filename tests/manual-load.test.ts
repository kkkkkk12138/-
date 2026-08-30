import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { prepareManualLoadBundle } from '../scripts/prepare-manual-load.mjs';

const tempRoots: string[] = [];

async function createTempProject() {
  const root = await mkdtemp(join(tmpdir(), 'name-replacement-manual-load-'));
  tempRoots.push(root);

  await mkdir(join(root, 'dist'), { recursive: true });
  await writeFile(
    join(root, 'dist', 'manifest.json'),
    JSON.stringify({ name: 'Name Replacement Browser Extension', version: '0.1.0' }),
    'utf8'
  );
  await writeFile(join(root, 'dist', 'content.js'), 'console.log("content");', 'utf8');
  await writeFile(join(root, 'dist', 'popup.html'), '<html></html>', 'utf8');

  return root;
}

afterEach(async () => {
  await Promise.all(tempRoots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe('prepareManualLoadBundle', () => {
  it('copies dist output into a stable chromium-load directory and writes manual loading guide', async () => {
    const root = await createTempProject();

    const result = await prepareManualLoadBundle({
      projectRoot: root,
      outputDirName: 'build/chromium-load',
      browserName: 'Chromium',
      extensionsPageUrl: 'chrome://extensions/',
      buildCommand: 'npm run build:safari-first',
      openCommand: 'npm run chromium:open',
      guideFileName: 'LOAD_IN_CHROMIUM.md',
      guideTitle: 'Chromium 手动加载目录',
      launcherFileName: 'Launch Chromium Manual Load.command',
      launcherDirName: 'build'
    });

    expect(result.outputDir).toBe(join(root, 'build', 'chromium-load'));
    expect(await readFile(join(root, 'build', 'chromium-load', 'manifest.json'), 'utf8')).toContain(
      'Name Replacement Browser Extension'
    );
    expect(await readFile(join(root, 'build', 'chromium-load', 'content.js'), 'utf8')).toContain('content');
    expect(
      await readFile(join(root, 'build', 'chromium-load', 'LOAD_IN_CHROMIUM.md'), 'utf8')
    ).toContain(
      'chrome://extensions/'
    );
    expect(result.guidePath).toBe(join(root, 'build', 'chromium-load', 'LOAD_IN_CHROMIUM.md'));
  });

  it('removes stale files before copying the next manual load bundle', async () => {
    const root = await createTempProject();
    await mkdir(join(root, 'build', 'chromium-load'), { recursive: true });
    await writeFile(join(root, 'build', 'chromium-load', 'stale.txt'), 'stale', 'utf8');

    await prepareManualLoadBundle({
      projectRoot: root,
      outputDirName: 'build/chromium-load'
    });

    await expect(
      readFile(join(root, 'build', 'chromium-load', 'stale.txt'), 'utf8')
    ).rejects.toThrow();
  });

  it('creates a double-clickable .command launcher that builds and opens Chromium extensions page', async () => {
    const root = await createTempProject();

    const result = await prepareManualLoadBundle({
      projectRoot: root,
      outputDirName: 'build/chromium-load',
      browserName: 'Chromium',
      extensionsPageUrl: 'chrome://extensions/',
      buildCommand: 'npm run build:safari-first',
      openCommand: 'npm run chromium:open',
      guideFileName: 'LOAD_IN_CHROMIUM.md',
      guideTitle: 'Chromium 手动加载目录',
      launcherFileName: 'Launch Chromium Manual Load.command',
      launcherDirName: 'build'
    });

    const launcher = await readFile(join(root, 'build', 'Launch Chromium Manual Load.command'), 'utf8');

    expect(launcher).toContain('npm run build:safari-first');
    expect(launcher).toContain('npm run chromium:open');
    expect(launcher).toContain('build/chromium-load/');
    expect(result.launcherPath).toBe(join(root, 'build', 'Launch Chromium Manual Load.command'));
  });
});
