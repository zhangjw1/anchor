package com.anchor.common.dataobject;

import com.anchor.common.handler.BigIntegerNumericTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigInteger;

/**
 * swap 表的一行。金额用整数保存最小单位。
 */
public class SwapDO {
    private Long chainId;
    private String txHash;
    private Integer logIndex;
    private Long blockNumber;
    private String blockHash;
    private Long blockTimestamp;
    private String txFrom;
    private String txTo;
    private String dex;
    private String direction;
    private String tokenIn;
    private String tokenOut;
    @TableField(typeHandler = BigIntegerNumericTypeHandler.class)
    private BigInteger amountIn;
    @TableField(typeHandler = BigIntegerNumericTypeHandler.class)
    private BigInteger amountOut;
    private String confirmStatus;
    private String watchedAddress;
    private String createdBy;
    private String updatedBy;

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
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
