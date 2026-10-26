package com.anchor.indexer.service;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.dto.ChainEventDTO;

import java.util.List;

/**
 * 根据链头和最终高度推进交换的确认状态。
 */
public interface ConfirmationService {
    /**
     * 返回状态有变化、或仍在确认窗口内的事件。
     */

    List<ChainEventDTO> apply(ChainDefinition chain, long head, long finalizedBlock);
}
