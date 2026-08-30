import type { BrowserFamily } from '../platform/types';
import type { PermissionState } from '../shared/types';

export type OnboardingContent = {
  subhead: string;
  title: string;
  detail: string;
  hint: string;
};

export function getOnboardingContent(
  browserFamily: BrowserFamily,
  permissionState: PermissionState
): OnboardingContent {
  if (browserFamily === 'safari') {
    return {
      subhead: 'Safari 首发，按当前网站逐页开启',
      title: '请先在 Safari 中为当前网站开启扩展权限',
      detail: 'Safari 更强调按网站授权。先在当前阅读页开启本扩展，再回到弹窗继续应用换名规则。',
      hint: 'iPhone / iPad 通常在地址栏的拼图菜单开启，Mac 可在 Safari 的网站设置里允许此扩展。'
    };
  }

  if (permissionState === 'needs-user-action') {
    return {
      subhead: '先确认权限，再对当前页生效',
      title: '当前网站还没有授权',
      detail: '请先允许扩展访问这个网站。授权后再次打开弹窗，就能把规则应用到当前页面。',
      hint: '如果你刚修改过浏览器权限，刷新页面后再点一次“立即生效”会更稳。'
    };
  }

  if (permissionState === 'unknown') {
    return {
      subhead: '面向移动端入口的当前页操作',
      title: '先打开要阅读的网页，再在这里处理换名',
      detail: '弹窗只会作用于当前标签页，规则仍会保存到本地，方便你在下一次阅读时继续使用。',
      hint: '如果没有检测到页面状态，通常是当前页还没准备好，重新打开目标网页即可。'
    };
  }

  return {
    subhead: '当前页触发，规则只在本地保存',
    title: '当前页已准备好应用规则',
    detail: '保存后的规则会立即同步到当前标签页，更符合移动端和按站点授权的使用心智。',
    hint: '建议一页一页确认效果；关闭“本页启用”后，只会停用当前标签页，不会删除已保存规则。'
  };
}
