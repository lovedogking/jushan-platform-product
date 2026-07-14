-- =============================================================================
-- Flyway 迁移：微信用户、车辆与车牌绑定
-- =============================================================================
-- T40｜微信用户、手机号、车辆与车牌绑定
-- 实现微信登录适配、手机号绑定、车辆/车牌管理和绑定验证策略。
-- 前置依赖：T12（平台账号登录）、T16（可信租户上下文）
-- 外部依赖：B05（微信小程序 AppId/Secret）— 使用 Mock 登录完成平台逻辑
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 微信用户表
-- 存储通过微信授权登录的车主账户。
-- 同一微信 OpenId 在系统中唯一，一个微信用户可绑定多个车牌。
-- -----------------------------------------------------------------------------
CREATE TABLE wx_user
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    openid        VARCHAR(128) NOT NULL COMMENT '微信 OpenId（唯一标识）',
    unionid       VARCHAR(128) DEFAULT NULL COMMENT '微信 UnionId（跨应用唯一，可为空）',
    nickname      VARCHAR(100) DEFAULT '' COMMENT '微信昵称（脱敏展示）',
    avatar_url    VARCHAR(500) DEFAULT '' COMMENT '微信头像 URL',
    phone         VARCHAR(20)  DEFAULT NULL COMMENT '绑定手机号（脱敏展示）',
    phone_verified TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '手机号是否已验证：0-未验证, 1-已验证',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, DISABLED-禁用',
    last_login_at DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_openid (openid) COMMENT 'OpenId 唯一索引',
    UNIQUE KEY uk_unionid (unionid) COMMENT 'UnionId 唯一索引（允许 NULL）',
    UNIQUE KEY uk_phone (phone) COMMENT '手机号唯一索引（允许 NULL）',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_created_at (created_at) COMMENT '按创建时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信用户表（车主端）';

-- -----------------------------------------------------------------------------
-- 车辆表
-- 存储车主名下的车辆信息。一辆车可被多个微信用户绑定（需配置策略）。
-- -----------------------------------------------------------------------------
CREATE TABLE vehicle
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    vehicle_plate VARCHAR(20)  NOT NULL COMMENT '车牌号（标准格式，如：京A12345）',
    vehicle_type  VARCHAR(20)  NOT NULL DEFAULT 'SMALL' COMMENT '车辆类型：SMALL-小型车, LARGE-大型车, NEW_ENERGY-新能源车, OTHER-其他',
    brand         VARCHAR(100) DEFAULT '' COMMENT '车辆品牌',
    color         VARCHAR(20)  DEFAULT '' COMMENT '车辆颜色',
    owner_name    VARCHAR(100) DEFAULT '' COMMENT '车主姓名（可选，用于月卡等场景）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, DISABLED-禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_plate (vehicle_plate) COMMENT '车牌号唯一索引',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_created_at (created_at) COMMENT '按创建时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆表';

-- -----------------------------------------------------------------------------
-- 车牌绑定表
-- 建立微信用户与车辆的绑定关系，支持多绑定策略。
-- -----------------------------------------------------------------------------
CREATE TABLE plate_binding
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    wx_user_id      BIGINT       NOT NULL COMMENT '微信用户 ID',
    vehicle_id      BIGINT       NOT NULL COMMENT '车辆 ID',
    binding_type    VARCHAR(20)  NOT NULL DEFAULT 'OWNER' COMMENT '绑定类型：OWNER-车主本人绑定, AUTHORIZED-授权绑定',
    verify_method   VARCHAR(20)  NOT NULL DEFAULT 'PLATE_ONLY' COMMENT '验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核',
    verify_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '验证状态：PENDING-待验证, APPROVED-已通过, REJECTED-已拒绝',
    is_default      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否默认车牌：0-否, 1-是',
    remark          VARCHAR(500) DEFAULT '' COMMENT '备注/审核说明',
    verified_at     DATETIME     DEFAULT NULL COMMENT '验证通过时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_vehicle (wx_user_id, vehicle_id) COMMENT '同一用户不能重复绑定同一辆车',
    INDEX idx_wx_user_id (wx_user_id) COMMENT '按微信用户查询',
    INDEX idx_vehicle_id (vehicle_id) COMMENT '按车辆查询',
    INDEX idx_verify_status (verify_status) COMMENT '按验证状态查询',
    INDEX idx_is_default (is_default) COMMENT '按默认车牌查询',
    INDEX idx_created_at (created_at) COMMENT '按创建时间查询',
    CONSTRAINT fk_plate_binding_wx_user FOREIGN KEY (wx_user_id) REFERENCES wx_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_plate_binding_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车牌绑定表';

-- -----------------------------------------------------------------------------
-- 车辆绑定策略配置表（按租户/停车场配置）
-- 控制同一车牌是否允许多账号绑定、默认验证方式等。
-- -----------------------------------------------------------------------------
CREATE TABLE binding_policy
(
    id                    BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id             BIGINT       DEFAULT NULL COMMENT '租户 ID（NULL 表示平台默认策略）',
    parking_lot_id        BIGINT       DEFAULT NULL COMMENT '停车场 ID（NULL 表示租户默认策略）',
    multi_account_mode    VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_ACCOUNT' COMMENT '多账号模式：SINGLE_ACCOUNT-单一微信账号绑定, AUTHORIZED_MULTI-主车主授权其他账号, ALLOW_MULTI-允许多账号直接绑定',
    default_verify_method VARCHAR(20)  NOT NULL DEFAULT 'PLATE_ONLY' COMMENT '默认验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核',
    max_bindings_per_user INT          NOT NULL DEFAULT 5 COMMENT '单个用户最大绑定车辆数',
    max_users_per_plate   INT          NOT NULL DEFAULT 1 COMMENT '单个车牌最大绑定用户数',
    allow_change_plate    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否允许更换车牌',
    status                VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆绑定策略配置表';

-- -----------------------------------------------------------------------------
-- 初始化平台默认绑定策略
-- -----------------------------------------------------------------------------
INSERT INTO binding_policy (tenant_id, parking_lot_id, multi_account_mode, default_verify_method, max_bindings_per_user, max_users_per_plate, allow_change_plate, status)
VALUES (NULL, NULL, 'SINGLE_ACCOUNT', 'PLATE_ONLY', 5, 1, 1, 'ENABLED');