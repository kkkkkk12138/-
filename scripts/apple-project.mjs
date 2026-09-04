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
