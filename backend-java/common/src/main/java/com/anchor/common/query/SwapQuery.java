package com.anchor.common.query;

public class SwapQuery {
    private Long chainId;
    private String address;
    private String dex;
    private String direction;

    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getDex() { return dex; }
    public void setDex(String dex) { this.dex = dex; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
}
