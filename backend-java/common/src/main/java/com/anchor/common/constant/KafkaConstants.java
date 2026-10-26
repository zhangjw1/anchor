package com.anchor.common.constant;
/**
 * 交换事件和链事件的主题名，以及确认、重组两种消息类型。
 */
public final class KafkaConstants {

    public static final String SWAP_EVENTS = "swap-events";
    public static final String CHAIN_EVENTS = "chain-events";
    public static final String CONFIRMATION = "CONFIRMATION";
    public static final String REORG = "REORG";

    private KafkaConstants() {
    }
}
