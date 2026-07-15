-- ---------------------------------------------------------------------------
-- FIX: 修正 super_admin 初始密码哈希
-- ---------------------------------------------------------------------------
-- 背景：V20260723001 迁移中写入的 BCrypt 哈希与注释声明的密码 admin123 不匹配，
--       导致全新数据库启动后 super_admin 无法登录。
-- 修复：使用 Spring Security BCryptPasswordEncoder 重新生成 admin123 的正确哈希。
-- ---------------------------------------------------------------------------

UPDATE sys_admin_account
SET password = '$2a$10$tbnJalEKCDRhYzXwkjINRur42EBSciqq/WZjArDLRHBAl0eLMKI3S'
WHERE id = 1
  AND username = 'super_admin';
