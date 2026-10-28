-- 为已有表补充 PostgreSQL 字段注释，便于库管理工具展示。

COMMENT ON TABLE block_checkpoint IS '每条链的扫描进度';
COMMENT ON COLUMN block_checkpoint.chain_id IS '链 ID，例如 Base 主网 8453';
COMMENT ON COLUMN block_checkpoint.block_number IS '已处理到的区块高度';
COMMENT ON COLUMN block_checkpoint.block_hash IS '该高度对应的区块哈希';
COMMENT ON COLUMN block_checkpoint.created_by IS '创建人';
COMMENT ON COLUMN block_checkpoint.created_at IS '创建时间';
COMMENT ON COLUMN block_checkpoint.updated_by IS '更新人';
COMMENT ON COLUMN block_checkpoint.updated_at IS '更新时间';

COMMENT ON TABLE block_hash IS '近期区块哈希，用于发现父哈希断裂和重组';
COMMENT ON COLUMN block_hash.chain_id IS '链 ID';
COMMENT ON COLUMN block_hash.block_number IS '区块高度';
COMMENT ON COLUMN block_hash.block_hash IS '本块哈希';
COMMENT ON COLUMN block_hash.parent_hash IS '父块哈希';
COMMENT ON COLUMN block_hash.created_by IS '创建人';
COMMENT ON COLUMN block_hash.created_at IS '创建时间';
COMMENT ON COLUMN block_hash.updated_by IS '更新人';
COMMENT ON COLUMN block_hash.updated_at IS '更新时间';

COMMENT ON TABLE token IS '代币元数据';
COMMENT ON COLUMN token.chain_id IS '链 ID';
COMMENT ON COLUMN token.address IS '代币合约地址；原生币用 0xeeee… 占位';
COMMENT ON COLUMN token.symbol IS '代币符号，例如 USDC';
COMMENT ON COLUMN token.decimals IS '代币精度';
COMMENT ON COLUMN token.created_by IS '创建人';
COMMENT ON COLUMN token.created_at IS '创建时间';
COMMENT ON COLUMN token.updated_by IS '更新人';
COMMENT ON COLUMN token.updated_at IS '更新时间';

COMMENT ON TABLE watched_address IS '监控地址簿';
COMMENT ON COLUMN watched_address.id IS '主键';
COMMENT ON COLUMN watched_address.chain_id IS '链 ID';
COMMENT ON COLUMN watched_address.address IS '监控的链上地址，EVM 存小写';
COMMENT ON COLUMN watched_address.name IS '显示名称';
COMMENT ON COLUMN watched_address.tag IS '标记：WATCH / ROUTER / MEV / WHALE / KOL';
COMMENT ON COLUMN watched_address.note IS '备注';
COMMENT ON COLUMN watched_address.created_by IS '创建人';
COMMENT ON COLUMN watched_address.created_at IS '创建时间';
COMMENT ON COLUMN watched_address.updated_by IS '更新人';
COMMENT ON COLUMN watched_address.updated_at IS '更新时间';

COMMENT ON TABLE swap IS '解析出的交换记录';
COMMENT ON COLUMN swap.id IS '主键';
COMMENT ON COLUMN swap.chain_id IS '链 ID';
COMMENT ON COLUMN swap.tx_hash IS '交易哈希';
COMMENT ON COLUMN swap.log_index IS '交易回执中的日志序号';
COMMENT ON COLUMN swap.block_number IS '交易所在区块高度';
COMMENT ON COLUMN swap.block_hash IS '交易所在区块哈希';
COMMENT ON COLUMN swap.block_timestamp IS '区块时间，Unix 秒';
COMMENT ON COLUMN swap.tx_from IS '交易发起地址';
COMMENT ON COLUMN swap.tx_to IS '交易目标地址，通常是 Router';
COMMENT ON COLUMN swap.dex IS 'DEX 名称，例如 Uniswap V3';
COMMENT ON COLUMN swap.direction IS '方向：BUY / SELL / SWAP';
COMMENT ON COLUMN swap.token_in IS '付出代币地址';
COMMENT ON COLUMN swap.token_out IS '收到代币地址';
COMMENT ON COLUMN swap.amount_in IS '付出数量，最小单位整数';
COMMENT ON COLUMN swap.amount_out IS '收到数量，最小单位整数';
COMMENT ON COLUMN swap.confirm_status IS '确认状态：CONFIRMING / CONFIRMED / FINALIZED / REORGED';
COMMENT ON COLUMN swap.watched_address IS '命中的监控地址';
COMMENT ON COLUMN swap.created_by IS '创建人';
COMMENT ON COLUMN swap.created_at IS '创建时间';
COMMENT ON COLUMN swap.updated_by IS '更新人';
COMMENT ON COLUMN swap.updated_at IS '更新时间';
