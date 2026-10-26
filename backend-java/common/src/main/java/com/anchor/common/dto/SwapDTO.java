package com.anchor.common.dto;

import java.math.BigInteger;

/**
 * 带代币符号和精度的交换查询结果，不直接返回给接口。
 */
public class SwapDTO {
    private Long id;
    private Long chainId;
    private String txHash;
    private Integer logIndex;
    private Long blockNumber;
    private Long timestampMs;
    private String direction;
    private String dex;
    private String tokenIn;
    private String tokenInSymbol;
    private Integer tokenInDecimals;
    private BigInteger amountIn;
    private String tokenOut;
    private String tokenOutSymbol;
    private Integer tokenOutDecimals;
    private BigInteger amountOut;
    private String confirmStatus;
    private int confirmations;
    private String watchedAddress;

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
    public Long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(Long timestampMs) { this.timestampMs = timestampMs; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getDex() { return dex; }
    public void setDex(String dex) { this.dex = dex; }
    public String getTokenIn() { return tokenIn; }
    public void setTokenIn(String tokenIn) { this.tokenIn = tokenIn; }
    public String getTokenInSymbol() { return tokenInSymbol; }
    public void setTokenInSymbol(String tokenInSymbol) { this.tokenInSymbol = tokenInSymbol; }
    public Integer getTokenInDecimals() { return tokenInDecimals; }
    public void setTokenInDecimals(Integer tokenInDecimals) { this.tokenInDecimals = tokenInDecimals; }
    public BigInteger getAmountIn() { return amountIn; }
    public void setAmountIn(BigInteger amountIn) { this.amountIn = amountIn; }
    public String getTokenOut() { return tokenOut; }
    public void setTokenOut(String tokenOut) { this.tokenOut = tokenOut; }
    public String getTokenOutSymbol() { return tokenOutSymbol; }
    public void setTokenOutSymbol(String tokenOutSymbol) { this.tokenOutSymbol = tokenOutSymbol; }
    public Integer getTokenOutDecimals() { return tokenOutDecimals; }
    public void setTokenOutDecimals(Integer tokenOutDecimals) { this.tokenOutDecimals = tokenOutDecimals; }
    public BigInteger getAmountOut() { return amountOut; }
    public void setAmountOut(BigInteger amountOut) { this.amountOut = amountOut; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
    public int getConfirmations() { return confirmations; }
    public void setConfirmations(int confirmations) { this.confirmations = confirmations; }
    public String getWatchedAddress() { return watchedAddress; }
    public void setWatchedAddress(String watchedAddress) { this.watchedAddress = watchedAddress; }
}
