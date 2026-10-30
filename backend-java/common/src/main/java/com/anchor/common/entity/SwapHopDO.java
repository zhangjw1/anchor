package com.anchor.common.entity;

import com.anchor.common.handler.BigIntegerNumericTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * swap_hop 表的一行，表示交换路径中的一跳。
 */
@TableName("swap_hop")
public class SwapHopDO {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属 swap 主键 */
    @TableField("swap_id")
    private Long swapId;

    /** 路径中的跳序号，从 0 递增 */
    @TableField("hop_index")
    private Integer hopIndex;

    /** 该跳所属 DEX */
    @TableField("dex")
    private String dex;

    /** 池子合约地址 */
    @TableField("pool")
    private String pool;

    /** 该跳付出代币地址 */
    @TableField("token_in")
    private String tokenIn;

    /** 该跳收到代币地址 */
    @TableField("token_out")
    private String tokenOut;

    /** 该跳付出数量，最小单位整数 */
    @TableField(value = "amount_in", typeHandler = BigIntegerNumericTypeHandler.class)
    private BigInteger amountIn;

    /** 该跳收到数量，最小单位整数 */
    @TableField(value = "amount_out", typeHandler = BigIntegerNumericTypeHandler.class)
    private BigInteger amountOut;

    /** 该跳 Swap 事件的日志序号 */
    @TableField("log_index")
    private Integer logIndex;

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
    public String getTokenOut() { return tokenOut; }
    public void setTokenOut(String tokenOut) { this.tokenOut = tokenOut; }
    public BigInteger getAmountIn() { return amountIn; }
    public void setAmountIn(BigInteger amountIn) { this.amountIn = amountIn; }
    public BigInteger getAmountOut() { return amountOut; }
    public void setAmountOut(BigInteger amountOut) { this.amountOut = amountOut; }
    public Integer getLogIndex() { return logIndex; }
    public void setLogIndex(Integer logIndex) { this.logIndex = logIndex; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
