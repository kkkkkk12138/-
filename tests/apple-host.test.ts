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
