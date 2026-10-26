package com.anchor.indexer.service;

import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;

import java.util.List;

/**
 * 把索引结果发到 Kafka。发送失败时调用方应保留进度并重试。
 */
public interface ChainEventPublishService {
    /**
     * 发布本块新增的交换。
     */
    void publishSwaps(List<SwapEventDTO> events);
    /**
     * 发布确认进度或重组。
     */
    void publishChain(List<ChainEventDTO> events);
}
