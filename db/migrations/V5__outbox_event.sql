-- 事务发件箱：扫块先落库再异步投递 Kafka，避免 Kafka 故障卡住 checkpoint。
CREATE TABLE outbox_event (
    id              BIGSERIAL       PRIMARY KEY, -- 主键，按 id 顺序投递
    topic           VARCHAR(128)    NOT NULL,    -- Kafka topic
    msg_key         VARCHAR(128)    NOT NULL,    -- 分区键，通常为 chainId
    payload         JSONB           NOT NULL,    -- 事件 JSON
    status          VARCHAR(16)     NOT NULL,    -- PENDING / SENT
    attempts        INTEGER         NOT NULL DEFAULT 0, -- 已尝试发送次数
    next_attempt_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(), -- 下次可投递时间（退避）
    created_by      VARCHAR(64)     NOT NULL,    -- 创建人
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(), -- 创建时间
    updated_by      VARCHAR(64)     NOT NULL,    -- 更新人
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(), -- 更新时间
    sent_at         TIMESTAMPTZ                  -- 成功发送时间
);

CREATE INDEX idx_outbox_pending
    ON outbox_event (next_attempt_at, id)
    WHERE status = 'PENDING';

COMMENT ON TABLE outbox_event IS 'Kafka 事务发件箱';
COMMENT ON COLUMN outbox_event.topic IS 'Kafka topic';
COMMENT ON COLUMN outbox_event.msg_key IS '消息键';
COMMENT ON COLUMN outbox_event.payload IS '事件 JSON';
COMMENT ON COLUMN outbox_event.status IS 'PENDING=待发送 SENT=已发送';
COMMENT ON COLUMN outbox_event.attempts IS '发送失败累计次数';
COMMENT ON COLUMN outbox_event.next_attempt_at IS '下次允许投递的时间';
COMMENT ON COLUMN outbox_event.sent_at IS '成功投递时间';
