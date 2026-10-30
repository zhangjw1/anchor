-- P3：聚合器标签与多跳路径。

ALTER TABLE swap ADD COLUMN aggregator VARCHAR(64);

COMMENT ON COLUMN swap.aggregator IS '聚合器名称；tx.to 命中 chains.yaml aggregators 时写入，否则为空';

CREATE TABLE swap_hop (
    id          BIGSERIAL      PRIMARY KEY,
    swap_id     BIGINT         NOT NULL REFERENCES swap (id) ON DELETE CASCADE,
    hop_index   INTEGER        NOT NULL,
    dex         VARCHAR(64)    NOT NULL,
    pool        VARCHAR(128)   NOT NULL,
    token_in    VARCHAR(128)   NOT NULL,
    token_out   VARCHAR(128)   NOT NULL,
    amount_in   NUMERIC(78, 0) NOT NULL,
    amount_out  NUMERIC(78, 0) NOT NULL,
    log_index   INTEGER        NOT NULL,
    created_by  VARCHAR(64)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(64)    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_swap_hop UNIQUE (swap_id, hop_index)
);

COMMENT ON TABLE swap_hop IS '一笔交换的多跳路径；单跳也写一行';
COMMENT ON COLUMN swap_hop.swap_id IS '所属 swap 主键';
COMMENT ON COLUMN swap_hop.hop_index IS '路径中的跳序号，从 0 递增';
COMMENT ON COLUMN swap_hop.dex IS '该跳所属 DEX';
COMMENT ON COLUMN swap_hop.pool IS '池子合约地址';
COMMENT ON COLUMN swap_hop.token_in IS '该跳付出代币地址';
COMMENT ON COLUMN swap_hop.token_out IS '该跳收到代币地址';
COMMENT ON COLUMN swap_hop.amount_in IS '该跳付出数量，最小单位整数';
COMMENT ON COLUMN swap_hop.amount_out IS '该跳收到数量，最小单位整数';
COMMENT ON COLUMN swap_hop.log_index IS '该跳 Swap 事件的日志序号';
COMMENT ON COLUMN swap_hop.created_by IS '创建人';
COMMENT ON COLUMN swap_hop.created_at IS '创建时间';
COMMENT ON COLUMN swap_hop.updated_by IS '更新人';
COMMENT ON COLUMN swap_hop.updated_at IS '更新时间';
