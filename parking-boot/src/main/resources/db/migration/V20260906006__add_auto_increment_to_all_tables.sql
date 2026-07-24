-- ============================================================
-- v0.1 生产部署修复 — 给所有业务表 id 列补齐 AUTO_INCREMENT
-- ============================================================
-- 背景：DDL 未声明 AUTO_INCREMENT，导致 MyBatis-Plus INSERT 时
--       id 无默认值报错 "Field 'id' doesn't have a default value"
-- 迁移版本：紧跟 V20260906005
-- ============================================================

ALTER TABLE access_policy           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE billing_rule           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE company                MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE device                 MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE employee_parking_lot   MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE fee_rule               MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE fee_rule_segment       MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE fixed_space_binding    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE lane_permission        MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE mini_message           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE mock_payment_config    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE mock_payment_record    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE monthly_pass           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE parking_lane           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE parking_lot            MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE parking_session        MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE parking_space_policy   MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE parking_zone           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE proxy_pay_record       MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE shift_record           MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_admin_account      MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_admin_account_parking_lot MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_admin_account_role MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_auth_code          MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_business_log       MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_company            MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_custom_role        MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_department         MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_role_permission    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_tenant             MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_vehicle            MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_vehicle_multi_plate MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_vehicle_wallet     MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_vehicle_wallet_log MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE vehicle_audit          MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE vehicle_renewal_log    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE visitor_apply          MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
