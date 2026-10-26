package com.anchor.common.domain;
/**
 * 监控地址的标记，用于区分普通地址、路由、机器人和大户。
 */
public enum AddressTagEnum {
    WATCH,
    ROUTER,
    MEV,
    WHALE,
    KOL
}
