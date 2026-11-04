-- 清理本地演示用种子交换（tx_hash 以 0xseed 开头）。
-- 用法示例（Docker Postgres）：
--   docker exec -i <postgres容器> psql -U anchor -d anchor < db/scripts/clear-seed-swaps.sql

DELETE FROM swap_hop
WHERE swap_id IN (
    SELECT id FROM swap WHERE tx_hash LIKE '0xseed%' OR block_hash LIKE '0xseed%'
);

DELETE FROM swap
WHERE tx_hash LIKE '0xseed%' OR block_hash LIKE '0xseed%';
