-- Anchor P1 初始表。金额用 NUMERIC(78,0)，地址和交易哈希用字符串，为非 EVM 链预留。

-- 每条链的扫描进度。检查点只允许向前推进。
CREATE TABLE block_checkpoint (
    chain_id     BIGINT       NOT NULL, -- 链 ID，例如 Base 主网 8453
    block_number BIGINT       NOT NULL, -- 已处理到的区块高度
    block_hash   VARCHAR(128) NOT NULL, -- 该高度对应的区块哈希
    created_by   VARCHAR(64)  NOT NULL, -- 创建人
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 创建时间
    updated_by   VARCHAR(64)  NOT NULL, -- 更新人
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 更新时间
    CONSTRAINT pk_block_checkpoint PRIMARY KEY (chain_id)
);

-- 近期区块哈希，用于发现父哈希断裂和重组。只保留最近一段高度。
CREATE TABLE block_hash (
    chain_id     BIGINT       NOT NULL, -- 链 ID
    block_number BIGINT       NOT NULL, -- 区块高度
    block_hash   VARCHAR(128) NOT NULL, -- 本块哈希
    parent_hash  VARCHAR(128) NOT NULL, -- 父块哈希
    created_by   VARCHAR(64)  NOT NULL, -- 创建人
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 创建时间
    updated_by   VARCHAR(64)  NOT NULL, -- 更新人
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 更新时间
    CONSTRAINT pk_block_hash PRIMARY KEY (chain_id, block_number)
);

-- 代币元数据。同一条链上地址唯一。
CREATE TABLE token (
    chain_id   BIGINT       NOT NULL, -- 链 ID
    address    VARCHAR(128) NOT NULL, -- 代币合约地址；原生币用 0xeeee… 占位
    symbol     VARCHAR(64),           -- 代币符号，例如 USDC
    decimals   INTEGER,               -- 代币精度
    created_by VARCHAR(64)  NOT NULL, -- 创建人
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 创建时间
    updated_by VARCHAR(64)  NOT NULL, -- 更新人
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 更新时间
    CONSTRAINT pk_token PRIMARY KEY (chain_id, address)
);

-- 监控地址簿。删除为物理删除。
CREATE TABLE watched_address (
    id         BIGSERIAL    PRIMARY KEY, -- 主键
    chain_id   BIGINT       NOT NULL,    -- 链 ID
    address    VARCHAR(128) NOT NULL,    -- 监控的链上地址，EVM 存小写
    name       VARCHAR(128),             -- 显示名称
    tag        VARCHAR(32)  NOT NULL,    -- 标记：WATCH / ROUTER / MEV / WHALE / KOL
    note       VARCHAR(512),             -- 备注
    created_by VARCHAR(64)  NOT NULL,    -- 创建人
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 创建时间
    updated_by VARCHAR(64)  NOT NULL,    -- 更新人
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(), -- 更新时间
    CONSTRAINT uk_watched_address UNIQUE (chain_id, address)
);

-- 解析出的交换记录。按链、交易哈希、日志序号幂等写入。
CREATE TABLE swap (
    id               BIGSERIAL      PRIMARY KEY, -- 主键
    chain_id         BIGINT         NOT NULL,    -- 链 ID
    tx_hash          VARCHAR(128)   NOT NULL,    -- 交易哈希
    log_index        INTEGER        NOT NULL,    -- 交易回执中的日志序号
    block_number     BIGINT         NOT NULL,    -- 交易所在区块高度
    block_hash       VARCHAR(128)   NOT NULL,    -- 交易所在区块哈希
    block_timestamp  BIGINT         NOT NULL,    -- 区块时间，Unix 秒
    tx_from          VARCHAR(128)   NOT NULL,    -- 交易发起地址
    tx_to            VARCHAR(128),               -- 交易目标地址，通常是 Router
    dex              VARCHAR(64)    NOT NULL,    -- DEX 名称，例如 Uniswap V3
    direction        VARCHAR(16)    NOT NULL,    -- 方向：BUY / SELL / SWAP
    token_in         VARCHAR(128)   NOT NULL,    -- 付出代币地址
    token_out        VARCHAR(128)   NOT NULL,    -- 收到代币地址
    amount_in        NUMERIC(78, 0) NOT NULL,    -- 付出数量，最小单位整数
    amount_out       NUMERIC(78, 0) NOT NULL,    -- 收到数量，最小单位整数
    confirm_status   VARCHAR(16)    NOT NULL,    -- 确认状态：CONFIRMING / CONFIRMED / FINALIZED / REORGED
    watched_address  VARCHAR(128)   NOT NULL,    -- 命中的监控地址
    created_by       VARCHAR(64)    NOT NULL,    -- 创建人
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(), -- 创建时间
    updated_by       VARCHAR(64)    NOT NULL,    -- 更新人
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(), -- 更新时间
    CONSTRAINT uk_swap_idempotent UNIQUE (chain_id, tx_hash, log_index)
);

CREATE INDEX idx_swap_chain_block ON swap (chain_id, block_number DESC); -- 按链和区块倒序查交换
CREATE INDEX idx_swap_watched ON swap (chain_id, watched_address);       -- 按链和监控地址查交换
CREATE INDEX idx_block_hash_lookup ON block_hash (chain_id, block_number DESC); -- 按链查近期区块哈希
