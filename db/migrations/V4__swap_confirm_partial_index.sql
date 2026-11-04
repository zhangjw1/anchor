-- P0：确认状态推进用部分索引，只覆盖仍在确认中的行。
CREATE INDEX idx_swap_confirm_active
    ON swap (chain_id, confirm_status, block_number)
    WHERE confirm_status IN ('CONFIRMING', 'CONFIRMED');

COMMENT ON INDEX idx_swap_confirm_active IS '批量推进 CONFIRMING/CONFIRMED 时按链与高度扫描';
