/**
 * Ролевая модель авторизации
 */
export enum UserRole {
  RIDER = 'rider',
  COACH = 'coach',
  ADMIN = 'admin',
}

export interface User {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  teamId?: string;
  avatar?: string;
  createdAt: Date;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface AuthCredentials {
  email: string;
  password: string;
}

export interface RegisterData extends AuthCredentials {
  name: string;
  role: UserRole;
  teamId?: string;
}

export interface JwtPayload {
  userId: string;
  email: string;
  role: UserRole;
  teamId?: string;
  iat: number;
  exp: number;
}

/**
 * Права доступа для ролей
 */
export const RolePermissions = {
  [UserRole.RIDER]: [
    'sensor:connect',
    'sensor:disconnect',
    'session:start',
    'session:end',
    'metrics:view_own',
    'metrics:send',
  ],
  [UserRole.COACH]: [
    'metrics:view_team',
    'metrics:view_all',
    'alerts:view_team',
    'alerts:view_all',
    'alerts:acknowledge',
    'sessions:view_team',
    'sessions:view_all',
    'riders:view_team',
    'riders:view_all',
    'replay:view',
    'analytics:view',
  ],
  [UserRole.ADMIN]: [
    'metrics:view_all',
    'alerts:view_all',
    'alerts:acknowledge',
    'alerts:configure',
    'sessions:view_all',
    'sessions:manage',
    'riders:view_all',
    'riders:manage',
    'replay:view',
    'replay:manage',
    'analytics:view',
    'analytics:export',
    'users:manage',
    'teams:manage',
  ],
} as const;

export type Permission = (typeof RolePermissions)[keyof typeof RolePermissions][number];

/**
 * Проверка прав доступа
 */
export function hasPermission(role: UserRole, permission: Permission): boolean {
  return (RolePermissions[role] as readonly Permission[]).includes(permission);
}

/**
 * Проверка прав для нескольких ролей
 */
export function hasAnyPermission(role: UserRole, permissions: Permission[]): boolean {
  return permissions.some(p => hasPermission(role, p));
}

/**
 * Получение разрешений для роли
 */
export function getRolePermissions(role: UserRole): Permission[] {
  return [...RolePermissions[role]];
}
