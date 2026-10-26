package com.anchor.api.service;

import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;

/**
 * 把索引事件推到已建立的 WebSocket 连接。
 */
public interface WebSocketPushService {
    /**
     * 推送一笔交换。
     */
    void onSwap(SwapEventDTO event);
    /**
     * 推送确认进度或重组。
     */
    void onChain(ChainEventDTO event);
}
