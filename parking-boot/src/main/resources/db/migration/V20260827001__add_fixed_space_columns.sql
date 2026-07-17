-- 任务包 3-2：固定车位补齐审核流/缴费方式/唯一校验/到期任务
-- 在 fixed_space_binding 表上新增字段与索引

ALTER TABLE fixed_space_binding
    ADD COLUMN pay_method VARCHAR(20) DEFAULT NULL COMMENT '缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER' AFTER valid_end,
    ADD COLUMN paid_amount_cents INT NOT NULL DEFAULT 0 COMMENT '实收金额（分）' AFTER pay_method,
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核 / APPROVED-已通过 / REJECTED-已驳回' AFTER paid_amount_cents,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ADMIN' COMMENT '来源：ADMIN-运营端 / MINIAPP-小程序端' AFTER review_status,
    ADD COLUMN applicant_id BIGINT DEFAULT NULL COMMENT '申请人ID（小程序用户ID；运营端录入为NULL）' AFTER source;

CREATE INDEX idx_fsb_review_status ON fixed_space_binding (review_status);
