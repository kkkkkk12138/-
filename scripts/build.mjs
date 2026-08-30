import { build } from 'esbuild';
import { cp, mkdir, rm } from 'node:fs/promises';
import { generateIcons } from './generate-icons.mjs';
import { prepareManualLoadBundle } from './prepare-manual-load.mjs';

const buildRoot = 'build';
const webExtensionDir = `${buildRoot}/webextension`;
const chromiumLoadDir = `${buildRoot}/chromium-load`;
const safariUploadDir = `${buildRoot}/safari-upload`;

await generateIcons();
await rm(buildRoot, { recursive: true, force: true });
await rm('dist', { recursive: true, force: true });
await mkdir(webExtensionDir, { recursive: true });

await Promise.all([
  build({
    entryPoints: ['src/popup/index.ts'],
    bundle: true,
    outfile: `${webExtensionDir}/popup.js`,
    format: 'iife',
    platform: 'browser'
  }),
  build({
    entryPoints: ['src/content/index.ts'],
    bundle: true,
    outfile: `${webExtensionDir}/content.js`,
    format: 'iife',
    platform: 'browser'
  }),
  cp('manifest.json', `${webExtensionDir}/manifest.json`),
  cp('popup.html', `${webExtensionDir}/popup.html`),
  cp('src/popup/styles.css', `${webExtensionDir}/styles.css`),
  cp('assets/icons', `${webExtensionDir}/icons`, { recursive: true })
]);

await Promise.all([
  cp(webExtensionDir, safariUploadDir, { recursive: true }),
  prepareManualLoadBundle({
    distDirName: webExtensionDir,
    outputDirName: chromiumLoadDir,
    browserName: 'Chromium',
    extensionsPageUrl: 'chrome://extensions/',
    buildCommand: 'npm run build:safari-first',
    openCommand: 'npm run chromium:open',
    guideFileName: 'LOAD_IN_CHROMIUM.md',
    guideTitle: 'Chromium 手动加载目录',
    launcherFileName: 'Launch Chromium Manual Load.command',
    launcherDirName: buildRoot
  })
]);
