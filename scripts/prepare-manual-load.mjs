import { chmod, cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

function createGuideContent(manifest, { browserName, extensionsPageUrl, outputDirName, buildCommand, guideTitle }) {
  return `# ${guideTitle}

这个目录是给 ${browserName} 的“加载已解压的扩展程序”直接选择用的。

## 选择方法

1. 打开 \`${extensionsPageUrl}\`
2. 开启右上角“开发者模式”
3. 点击“加载已解压的扩展程序”
4. 直接选择当前这个 \`${outputDirName}/\` 目录

## 当前扩展

- 名称：\`${manifest.name}\`
- 版本：\`${manifest.version}\`

## 注意

- 这个目录会在每次 \`${buildCommand}\` 后自动刷新
- 不要手动往这里放其他文件，旧文件会被自动清理
`;
}

function createCommandLauncher({
  buildCommand,
  openCommand,
  browserName,
  outputDirName
}) {
  return `#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

ensure_npm() {
  if command -v npm >/dev/null 2>&1; then
    return 0
  fi

  if [ -s "$HOME/.nvm/nvm.sh" ]; then
    . "$HOME/.nvm/nvm.sh"
  fi

  if [ -s "$HOME/.zprofile" ]; then
    . "$HOME/.zprofile" >/dev/null 2>&1 || true
  fi

  if [ -s "$HOME/.zshrc" ]; then
    . "$HOME/.zshrc" >/dev/null 2>&1 || true
  fi

  command -v npm >/dev/null 2>&1
}

clear
echo "准备刷新插件构建产物..."

if ! ensure_npm; then
  echo
  echo "未找到 npm。请先确认 Node.js / npm 已安装，并且终端里可以直接运行 npm。"
  echo
  read -n 1 -s -r -p "按任意键关闭..."
  exit 1
fi

${buildCommand}
${openCommand}

echo
echo "已打开 ${browserName} 扩展管理页。"
echo "下一步：点击“加载已解压的扩展程序”，然后选择当前项目里的 ${outputDirName}/ 目录。"
echo
read -n 1 -s -r -p "按任意键关闭..."
`;
}

export async function prepareManualLoadBundle({
  projectRoot = resolve(__dirname, '..'),
  distDirName = 'dist',
  outputDirName = 'chrome-load',
  browserName = 'Chrome',
  extensionsPageUrl = 'chrome://extensions/',
  buildCommand = 'npm run build',
  openCommand = 'npm run chrome:open',
  guideFileName = 'LOAD_IN_CHROME.md',
  guideTitle = 'Chrome 手动加载目录',
  launcherFileName = 'Launch Chrome Manual Load.command',
  launcherDirName = '.'
} = {}) {
  const root = resolve(projectRoot);
  const distDir = join(root, distDirName);
  const outputDir = join(root, outputDirName);
  const manifestPath = join(distDir, 'manifest.json');
  const guidePath = join(outputDir, guideFileName);
  const launcherPath = launcherFileName ? join(root, launcherDirName, launcherFileName) : null;

  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));

  await rm(outputDir, { recursive: true, force: true });
  await mkdir(outputDir, { recursive: true });
  await cp(distDir, outputDir, { recursive: true });
  await writeFile(
    guidePath,
    createGuideContent(manifest, {
      browserName,
      extensionsPageUrl,
      outputDirName,
      buildCommand,
      guideTitle
    }),
    'utf8'
  );

  if (launcherPath) {
    await mkdir(dirname(launcherPath), { recursive: true });
    await writeFile(
      launcherPath,
      createCommandLauncher({
        buildCommand,
        openCommand,
        browserName,
        outputDirName
      }),
      'utf8'
    );
    await chmod(launcherPath, 0o755);
  }

  return {
    outputDir,
    manifest,
    guidePath,
    launcherPath
  };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const { outputDir } = await prepareManualLoadBundle({
    distDirName: 'build/webextension',
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
  console.log(`Manual load bundle ready at ${outputDir}`);
}
