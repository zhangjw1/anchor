package com.anchor.indexer.dto;
/**
 * 处理一个区块后的下一步。nextBlock 为 -1 表示暂停。
 */
public record ScanStepDTO(Kind kind, long nextBlock) {
    /**
     * 本块已入库、已回退，或扫描应暂停。
     */
    public enum Kind {
        INGESTED,
        REWOUND,
        PAUSED
    }
    /**
     * 本块处理完成，继续下一高度。
     */
    public static ScanStepDTO ingested(long nextBlock) {
        return new ScanStepDTO(Kind.INGESTED, nextBlock);
    }
    /**
     * 发生重组，从回退后的高度重新扫描。
     */
    public static ScanStepDTO rewound(long nextBlock) {
        return new ScanStepDTO(Kind.REWOUND, nextBlock);
    }
    /**
     * 无法安全回退，扫描停止。
     */
    public static ScanStepDTO paused() {
        return new ScanStepDTO(Kind.PAUSED, -1L);
    }
}
