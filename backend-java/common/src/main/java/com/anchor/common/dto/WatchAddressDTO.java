package com.anchor.common.dto;

public class WatchAddressDTO {
    private Long id;
    private Long chainId;
    private String address;
    private String name;
    private String tag;
    private String note;
    private Long swapCount;
    private Long lastActiveAt;
    private Long createdAtMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getSwapCount() { return swapCount; }
    public void setSwapCount(Long swapCount) { this.swapCount = swapCount; }
    public Long getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Long lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(Long createdAtMs) { this.createdAtMs = createdAtMs; }
}
