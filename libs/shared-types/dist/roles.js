"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RolePermissions = exports.UserRole = void 0;
exports.hasPermission = hasPermission;
exports.hasAnyPermission = hasAnyPermission;
exports.getRolePermissions = getRolePermissions;
/**
 * Ролевая модель авторизации
 */
var UserRole;
(function (UserRole) {
    UserRole["RIDER"] = "rider";
    UserRole["COACH"] = "coach";
    UserRole["ADMIN"] = "admin";
})(UserRole || (exports.UserRole = UserRole = {}));
/**
 * Права доступа для ролей
 */
exports.RolePermissions = {
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
};
/**
 * Проверка прав доступа
 */
function hasPermission(role, permission) {
    return exports.RolePermissions[role].includes(permission);
}
/**
 * Проверка прав для нескольких ролей
 */
function hasAnyPermission(role, permissions) {
    return permissions.some(p => hasPermission(role, p));
}
/**
 * Получение разрешений для роли
 */
function getRolePermissions(role) {
    return [...exports.RolePermissions[role]];
}
