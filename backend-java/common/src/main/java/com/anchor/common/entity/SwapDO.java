package com.anchor.common.entity;

import com.anchor.common.handler.BigIntegerNumericTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * swap 表的一行。金额用整数保存最小单位。
 */
@TableName("swap")
public class SwapDO {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 链 ID */
    @TableField("chain_id")
    private Long chainId;

    /** 交易哈希 */
    @TableField("tx_hash")
    private String txHash;

    /** 交易回执中的日志序号 */
    @TableField("log_index")
    private Integer logIndex;

    /** 交易所在区块高度 */
    @TableField("block_number")
    private Long blockNumber;

    /** 交易所在区块哈希 */
    @TableField("block_hash")
    private String blockHash;

    /** 区块时间，Unix 秒 */
    @TableField("block_timestamp")
    private Long blockTimestamp;

    /** 交易发起地址 */
    @TableField("tx_from")
    private String txFrom;

    /** 交易目标地址，通常是 Router */
    @TableField("tx_to")
    private String txTo;

    /** DEX 名称，例如 Uniswap V3 */
    @TableField("dex")
    private String dex;

    /** 方向：BUY / SELL / SWAP */
    @TableField("direction")
    private String direction;

    /** 付出代币地址 */
    @TableField("token_in")
    private String tokenIn;

    /** 收到代币地址 */
    @TableField("token_out")
    private String tokenOut;

    /** 付出数量，最小单位整数 */
    @TableField(value = "amount_in", typeHandler = BigIntegerNumericTypeHandler.class)
    private BigInteger amountIn;

    /** 收到数量，最小单位整数 */
    @TableField(value = "amount_out", typeHandler = BigIntegerNumericTypeHandler.class)
    private BigInteger amountOut;

    /** 确认状态：CONFIRMING / CONFIRMED / FINALIZED / REORGED */
    @TableField("confirm_status")
    private String confirmStatus;

    /** 命中的监控地址 */
    @TableField("watched_address")
    private String watchedAddress;

    /** 创建人 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新人 */
    @TableField("updated_by")
    private String updatedBy;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
    public Integer getLogIndex() { return logIndex; }
    public void setLogIndex(Integer logIndex) { this.logIndex = logIndex; }
    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }
    public String getBlockHash() { return blockHash; }
    public void setBlockHash(String blockHash) { this.blockHash = blockHash; }
    public Long getBlockTimestamp() { return blockTimestamp; }
    public void setBlockTimestamp(Long blockTimestamp) { this.blockTimestamp = blockTimestamp; }
    public String getTxFrom() { return txFrom; }
    public void setTxFrom(String txFrom) { this.txFrom = txFrom; }
    public String getTxTo() { return txTo; }
    public void setTxTo(String txTo) { this.txTo = txTo; }
    public String getDex() { return dex; }
    public void setDex(String dex) { this.dex = dex; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getTokenIn() { return tokenIn; }
    public void setTokenIn(String tokenIn) { this.tokenIn = tokenIn; }
    public String getTokenOut() { return tokenOut; }
    public void setTokenOut(String tokenOut) { this.tokenOut = tokenOut; }
    public BigInteger getAmountIn() { return amountIn; }
    public void setAmountIn(BigInteger amountIn) { this.amountIn = amountIn; }
    public BigInteger getAmountOut() { return amountOut; }
    public void setAmountOut(BigInteger amountOut) { this.amountOut = amountOut; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
    public String getWatchedAddress() { return watchedAddress; }
    public void setWatchedAddress(String watchedAddress) { this.watchedAddress = watchedAddress; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
