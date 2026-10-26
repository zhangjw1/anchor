package com.anchor.indexer.dto;

import com.anchor.common.chain.SwapParser;
import com.anchor.common.dto.ChainEventDTO;

import java.util.List;

/**
 * 一个区块入库后待发布的交换和确认事件。
 */
public record BlockIngestDTO(List<SwapParser.ParsedSwap> swaps, List<ChainEventDTO> confirmations) {
}
