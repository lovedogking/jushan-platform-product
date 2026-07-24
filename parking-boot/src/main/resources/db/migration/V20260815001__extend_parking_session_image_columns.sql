-- =============================================================================
-- Flyway 迁移：延长 parking_session 图片列长度
-- =============================================================================
-- 背景：
--   臻识 C5 相机的抓拍图片 URL（AliCloud OSS 签名 URL）超过 255 字符，
--   导致 DataTruncation 错误。entry_image 和 exit_image 列需扩展到 1024 字符。
-- =============================================================================

ALTER TABLE parking_session
    MODIFY COLUMN entry_image VARCHAR(1024) DEFAULT NULL COMMENT '入场抓拍图片URL（AliCloud OSS 签名URL，长度可达 1024）',
    MODIFY COLUMN exit_image VARCHAR(1024) DEFAULT NULL COMMENT '出场抓拍图片URL';
