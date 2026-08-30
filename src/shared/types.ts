export type ReplaceRule = {
  id: string;
  source: string;
  target: string;
};

export type PermissionState = 'granted' | 'needs-user-action' | 'unknown';

export type PageRuntimeState = {
  enabled: boolean;
  activeRuleCount: number;
  permissionState: PermissionState;
};

export type ContentMessage =
  | { type: 'GET_PAGE_STATE' }
  | { type: 'APPLY_RULES'; rules: ReplaceRule[] }
  | { type: 'DISABLE_PAGE' };
