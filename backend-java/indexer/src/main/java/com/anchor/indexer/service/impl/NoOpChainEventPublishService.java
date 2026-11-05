package com.anchor.indexer.service.impl;

import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.indexer.service.ChainEventPublishService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 扫块已改为 outbox；保留空实现避免测试/旧依赖缺 Bean。
 */
@Service
@ConditionalOnMissingBean(ChainEventPublishService.class)
public class NoOpChainEventPublishService implements ChainEventPublishService {

    @Override
    public void publishSwaps(List<SwapEventDTO> events) {
        // no-op：事件经 outbox 投递
    }

    @Override
    public void publishChain(List<ChainEventDTO> events) {
        // no-op：事件经 outbox 投递
    }
}
