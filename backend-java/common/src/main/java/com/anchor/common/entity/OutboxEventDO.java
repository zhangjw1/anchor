package com.anchor.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * outbox_event 表的一行。
 * 扫块事务内先写入 PENDING，再由 OutboxRelay 异步投递 Kafka；成功后标记 SENT。
 */
@TableName("outbox_event")
public class OutboxEventDO {

    /** 主键，按 id 顺序领取，保证同链事件相对顺序 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** Kafka 主题，如 swap-events / chain-events */
    @TableField("topic")
    private String topic;

    /** Kafka 消息 key，当前为 chainId 字符串，保证同链进同一分区 */
    @TableField("msg_key")
    private String msgKey;

    /** 消息体 JSON 字符串（库内 jsonb，读写按 text） */
    @TableField("payload")
    private String payload;

    /** 投递状态：PENDING / SENT */
    @TableField("status")
    private String status;

    /** 已失败重试次数；成功投递前每次失败 +1 */
    @TableField("attempts")
    private Integer attempts;

    /** 下次允许领取的时间；失败后按指数退避推迟 */
    @TableField("next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    /** 创建人 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /** 更新人 */
    @TableField("updated_by")
    private String updatedBy;

    /** 更新时间 */
    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    /** 成功投递 Kafka 的时间；PENDING 时为空 */
    @TableField("sent_at")
    private OffsetDateTime sentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public void setMsgKey(String msgKey) {
        this.msgKey = msgKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
