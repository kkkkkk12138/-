import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT_PATH = 'apple/小说一键换名/小说一键换名.xcodeproj/project.pbxproj';
const APP_ICON_PATH =
  'apple/小说一键换名/Shared (App)/Assets.xcassets/AppIcon.appiconset/universal-icon-1024@1x.png';
const IOS_INFO_PATH = 'apple/小说一键换名/iOS (App)/Info.plist';
const REQUIRED_DOCUMENTS = [
  'docs/safari/PRIVACY_POLICY.md',
  'docs/safari/TESTFLIGHT_BETA.md'
];

async function readRequired(root, relativePath, errors) {
  try {
    return await readFile(resolve(root, relativePath));
  } catch {
    errors.push(`缺少文件：${relativePath}`);
    return null;
  }
}

function uniqueMatches(content, pattern) {
  return [...new Set([...content.matchAll(pattern)].map((match) => match[1]))];
}

function inspectPng(buffer) {
  const isPng =
    buffer.length >= 26 &&
    buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) &&
    buffer.subarray(12, 16).toString('ascii') === 'IHDR';

  if (!isPng) {
    return null;
  }

  const colorType = buffer[25];
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20),
    hasAlpha: colorType === 4 || colorType === 6
  };
}

export async function assessTestFlightReadiness(projectRoot = process.cwd()) {
  const root = resolve(projectRoot);
  const errors = [];
  const manifestBuffer = await readRequired(root, 'manifest.json', errors);
  const projectBuffer = await readRequired(root, PROJECT_PATH, errors);
  const iconBuffer = await readRequired(root, APP_ICON_PATH, errors);
  const infoBuffer = await readRequired(root, IOS_INFO_PATH, errors);

  await Promise.all(REQUIRED_DOCUMENTS.map((path) => readRequired(root, path, errors)));

  let version = '';
  let buildNumber = '';

  if (manifestBuffer) {
    try {
      version = JSON.parse(manifestBuffer.toString('utf8')).version ?? '';
    } catch {
      errors.push('manifest.json 不是有效 JSON');
    }
  }

  if (projectBuffer) {
    const project = projectBuffer.toString('utf8');
    const marketingVersions = uniqueMatches(project, /MARKETING_VERSION = ([^;]+);/g);
    const buildNumbers = uniqueMatches(project, /CURRENT_PROJECT_VERSION = ([^;]+);/g);
    buildNumber = buildNumbers.length === 1 ? buildNumbers[0] : '';

    if (marketingVersions.length !== 1 || marketingVersions[0] !== version) {
      errors.push('Xcode MARKETING_VERSION 必须与 manifest.json version 一致');
    }

    if (buildNumbers.length !== 1 || !/^\d+$/.test(buildNumbers[0])) {
      errors.push('Xcode CURRENT_PROJECT_VERSION 必须是统一的正整数');
    }
  }

  if (iconBuffer) {
    const icon = inspectPng(iconBuffer);
    if (!icon || icon.width !== 1024 || icon.height !== 1024) {
      errors.push('App Store 图标必须是 1024×1024 PNG');
    } else if (icon.hasAlpha) {
      errors.push('App Store 1024 图标不能包含 Alpha 通道');
    }
  }

  if (
    infoBuffer &&
    !/<key>ITSAppUsesNonExemptEncryption<\/key>\s*<false\/>/.test(infoBuffer.toString('utf8'))
  ) {
    errors.push('iOS App 必须声明不使用非豁免加密');
  }

  return {
    errors,
    version,
    buildNumber
  };
}

async function main() {
  const result = await assessTestFlightReadiness();

  if (result.errors.length > 0) {
    console.error('TestFlight 预检失败：');
    result.errors.forEach((error) => console.error(`- ${error}`));
    process.exitCode = 1;
    return;
  }

  console.log(`TestFlight 预检通过：版本 ${result.version} (${result.buildNumber})`);
}

if (fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  await main();
}
