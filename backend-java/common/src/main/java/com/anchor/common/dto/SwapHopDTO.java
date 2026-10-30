package com.anchor.common.dto;

import java.math.BigInteger;

/**
 * 交换路径中的一跳，带代币展示字段。
 */
public class SwapHopDTO {
    private Long swapId;
    private Integer hopIndex;
    private String dex;
    private String pool;
    private String tokenIn;
    private String tokenInSymbol;
    private Integer tokenInDecimals;
    private BigInteger amountIn;
    private String tokenOut;
    private String tokenOutSymbol;
    private Integer tokenOutDecimals;
    private BigInteger amountOut;
    private Integer logIndex;

    public Long getSwapId() { return swapId; }
    public void setSwapId(Long swapId) { this.swapId = swapId; }
    public Integer getHopIndex() { return hopIndex; }
    public void setHopIndex(Integer hopIndex) { this.hopIndex = hopIndex; }
    public String getDex() { return dex; }
    public void setDex(String dex) { this.dex = dex; }
    public String getPool() { return pool; }
    public void setPool(String pool) { this.pool = pool; }
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
    public Integer getLogIndex() { return logIndex; }
    public void setLogIndex(Integer logIndex) { this.logIndex = logIndex; }
}
