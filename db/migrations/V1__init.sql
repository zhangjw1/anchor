-- Anchor P1 初始表。金额用 NUMERIC(78,0)，地址和交易哈希用字符串，为非 EVM 链预留。

CREATE TABLE block_checkpoint (
    chain_id     BIGINT       NOT NULL,
    block_number BIGINT       NOT NULL,
    block_hash   VARCHAR(128) NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(64)  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_block_checkpoint PRIMARY KEY (chain_id)
);

CREATE TABLE block_hash (
    chain_id     BIGINT       NOT NULL,
    block_number BIGINT       NOT NULL,
    block_hash   VARCHAR(128) NOT NULL,
    parent_hash  VARCHAR(128) NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(64)  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_block_hash PRIMARY KEY (chain_id, block_number)
);

CREATE TABLE token (
    chain_id   BIGINT       NOT NULL,
    address    VARCHAR(128) NOT NULL,
    symbol     VARCHAR(64),
    decimals   INTEGER,
    created_by VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(64)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_token PRIMARY KEY (chain_id, address)
);

CREATE TABLE watched_address (
    id         BIGSERIAL    PRIMARY KEY,
    chain_id   BIGINT       NOT NULL,
    address    VARCHAR(128) NOT NULL,
    name       VARCHAR(128),
    tag        VARCHAR(32)  NOT NULL,
    note       VARCHAR(512),
    created_by VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(64)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_watched_address UNIQUE (chain_id, address)
);

CREATE TABLE swap (
    id               BIGSERIAL      PRIMARY KEY,
    chain_id         BIGINT         NOT NULL,
    tx_hash          VARCHAR(128)   NOT NULL,
    log_index        INTEGER        NOT NULL,
    block_number     BIGINT         NOT NULL,
    block_hash       VARCHAR(128)   NOT NULL,
    block_timestamp  BIGINT         NOT NULL,
    tx_from          VARCHAR(128)   NOT NULL,
    tx_to            VARCHAR(128),
    dex              VARCHAR(64)    NOT NULL,
    direction        VARCHAR(16)    NOT NULL,
    token_in         VARCHAR(128)   NOT NULL,
    token_out        VARCHAR(128)   NOT NULL,
    amount_in        NUMERIC(78, 0) NOT NULL,
    amount_out       NUMERIC(78, 0) NOT NULL,
    confirm_status   VARCHAR(16)    NOT NULL,
    watched_address  VARCHAR(128)   NOT NULL,
    created_by       VARCHAR(64)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(64)    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_swap_idempotent UNIQUE (chain_id, tx_hash, log_index)
);

CREATE INDEX idx_swap_chain_block ON swap (chain_id, block_number DESC);
CREATE INDEX idx_swap_watched ON swap (chain_id, watched_address);
CREATE INDEX idx_block_hash_lookup ON block_hash (chain_id, block_number DESC);
