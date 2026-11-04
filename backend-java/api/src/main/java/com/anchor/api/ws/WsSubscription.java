package com.anchor.api.ws;

/**
 * 一条 WebSocket 订阅。
 */
public record WsSubscription(String channel, long chainId, String address, String txHash) {
}
