import { access, mkdtemp, mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { prepareManualLoadBundle } from '../scripts/prepare-manual-load.mjs';

const tempRoots: string[] = [];

async function createTempProject() {
  const root = await mkdtemp(join(tmpdir(), 'name-replacement-branding-'));
  tempRoots.push(root);

  await mkdir(join(root, 'dist', 'icons'), { recursive: true });
  await writeFile(
    join(root, 'dist', 'manifest.json'),
    JSON.stringify({
      manifest_version: 3,
      name: '小说一键换名',
      version: '0.2.0',
      description: 'Safari 首发的阅读换名扩展，在当前阅读网页里无感替换角色名字。',
      permissions: ['storage', 'activeTab'],
      host_permissions: ['https://*/*', 'http://*/*'],
      icons: {
        '16': 'icons/icon16.png',
        '32': 'icons/icon32.png',
        '48': 'icons/icon48.png',
        '128': 'icons/icon128.png'
      },
      action: {
        default_popup: 'popup.html',
        default_icon: {
          '16': 'icons/icon16.png',
          '32': 'icons/icon32.png'
        }
      }
    }),
    'utf8'
  );
  await writeFile(join(root, 'dist', 'popup.html'), '<html></html>', 'utf8');
  await writeFile(join(root, 'dist', 'popup.js'), 'console.log("popup");', 'utf8');
  await writeFile(join(root, 'dist', 'content.js'), 'console.log("content");', 'utf8');
  await writeFile(join(root, 'dist', 'styles.css'), 'body {}', 'utf8');
  await writeFile(join(root, 'dist', 'icons', 'icon16.png'), 'icon16', 'utf8');
  await writeFile(join(root, 'dist', 'icons', 'icon32.png'), 'icon32', 'utf8');
  await writeFile(join(root, 'dist', 'icons', 'icon48.png'), 'icon48', 'utf8');
  await writeFile(join(root, 'dist', 'icons', 'icon128.png'), 'icon128', 'utf8');

  return root;
}

afterEach(async () => {
  await Promise.all(tempRoots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe('branding assets', () => {
  it('keeps safari-first metadata and icon declarations in manifest', async () => {
    const manifest = JSON.parse(
      await readFile(resolve('manifest.json'), 'utf8')
    );

    expect(manifest.name).toBe('小说一键换名');
    expect(manifest.version).toBe('0.2.0');
    expect(manifest.description).toContain('Safari 首发');
    expect(manifest.description).toContain('当前阅读网页');
    expect(manifest.permissions).toEqual(['storage', 'activeTab']);
    expect(manifest.host_permissions).toEqual(['https://*/*', 'http://*/*']);
    expect(manifest.icons).toEqual({
      '16': 'icons/icon16.png',
      '32': 'icons/icon32.png',
      '48': 'icons/icon48.png',
      '128': 'icons/icon128.png'
    });
    expect(manifest.action.default_icon).toEqual({
      '16': 'icons/icon16.png',
      '32': 'icons/icon32.png'
    });
    expect(manifest.content_scripts).toEqual([
      {
        matches: ['https://*/*', 'http://*/*'],
        js: ['content.js'],
        run_at: 'document_idle'
      }
    ]);
  });

  it('uses the concise product name as the popup document title', async () => {
    const popupHtml = await readFile(resolve('popup.html'), 'utf8');

    expect(popupHtml).toContain('<title>小说一键换名</title>');
  });

  it('copies icon files into the manual load bundle', async () => {
    const root = await createTempProject();

    await prepareManualLoadBundle({ projectRoot: root });

    await access(join(root, 'chrome-load', 'icons', 'icon16.png'));
    await access(join(root, 'chrome-load', 'icons', 'icon32.png'));
    await access(join(root, 'chrome-load', 'icons', 'icon48.png'));
    const iconStat = await stat(join(root, 'chrome-load', 'icons', 'icon128.png'));

    expect(iconStat.isFile()).toBe(true);
  });
});
