package com.anchor.indexer.dto;
/**
 * 父哈希断裂后的处理结果。暂停时扫描循环应停住。
 */
public record ReorgHandleDTO(boolean paused, long nextBlock) {
    /**
     * 找不到可靠分叉点，扫描暂停。
     */
    public static ReorgHandleDTO halt() {
        return new ReorgHandleDTO(true, -1L);
    }
    /**
     * 已退回到分叉点，下一高度从这里继续。
     */
    public static ReorgHandleDTO rewound(long nextBlock) {
        return new ReorgHandleDTO(false, nextBlock);
    }
}
