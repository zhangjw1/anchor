package com.anchor.common.query;
/**
 * 交换分页的查询条件。空字段表示不筛选。
 */
public class SwapQuery {
    private Long chainId;
    private String address;
    private String dex;
    private String direction;
    /** 为 true 时包含 REORGED；默认 false / null 均排除。 */
    private Boolean includeReorged;
    /** 为 true 时包含 tx_hash 以 0xseed 开头的演示数据；默认排除。 */
    private Boolean includeSeed;
    /** keyset：取严格早于 (beforeBlockNumber, beforeLogIndex) 的记录（降序翻页）。 */
    private Long beforeBlockNumber;
    private Integer beforeLogIndex;
    /** LIMIT，含探测 hasMore 时多取 1 条。 */
    private Integer limit;

    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getDex() { return dex; }
    public void setDex(String dex) { this.dex = dex; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Boolean getIncludeReorged() { return includeReorged; }
    public void setIncludeReorged(Boolean includeReorged) { this.includeReorged = includeReorged; }
    public Boolean getIncludeSeed() { return includeSeed; }
    public void setIncludeSeed(Boolean includeSeed) { this.includeSeed = includeSeed; }
    public Long getBeforeBlockNumber() { return beforeBlockNumber; }
    public void setBeforeBlockNumber(Long beforeBlockNumber) { this.beforeBlockNumber = beforeBlockNumber; }
    public Integer getBeforeLogIndex() { return beforeLogIndex; }
    public void setBeforeLogIndex(Integer beforeLogIndex) { this.beforeLogIndex = beforeLogIndex; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
