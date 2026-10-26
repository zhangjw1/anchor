package com.anchor.common.domain;
/**
 * 交换确认进度。重组只改状态，不删除原记录。
 */
public enum ConfirmStatusEnum {
    CONFIRMING,
    CONFIRMED,
    FINALIZED,
    REORGED
}
