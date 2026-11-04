package com.anchor.api.service.impl;

import com.anchor.api.service.SwapPageResult;
import com.anchor.api.service.SwapQueryService;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.SwapHopMapper;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.domain.SwapDirectionEnum;
import com.anchor.common.dto.SwapDTO;
import com.anchor.common.dto.SwapHopDTO;
import com.anchor.common.query.SwapQuery;
import com.anchor.common.support.Addresses;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按链、监控地址、协议和方向查询交换；使用 (block_number, log_index) keyset 翻页。
 */
@Service
public class SwapQueryServiceImpl implements SwapQueryService {

    private final SwapMapper swapMapper;
    private final SwapHopMapper swapHopMapper;
    private final ChainRegistry chainRegistry;
    private final CheckpointMapper checkpointMapper;

    public SwapQueryServiceImpl(SwapMapper swapMapper, SwapHopMapper swapHopMapper, ChainRegistry chainRegistry,
                                CheckpointMapper checkpointMapper) {
        this.swapMapper = swapMapper;
        this.swapHopMapper = swapHopMapper;
        this.chainRegistry = chainRegistry;
        this.checkpointMapper = checkpointMapper;
    }

    @Override
    public SwapPageResult list(long chainId, String address, String dex, String direction, Integer size,
                               boolean includeReorged, boolean includeSeed, Long beforeBlock, Integer beforeLogIndex) {
        chainRegistry.requireById(chainId);
        if ((beforeBlock == null) != (beforeLogIndex == null)) {
            throw new AnchorException("VALIDATION_ERROR", "beforeBlock 与 beforeLogIndex 需同时提供");
        }
        int pageSize = size == null ? AnchorConstants.DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > AnchorConstants.MAX_PAGE_SIZE) {
            throw new AnchorException("INVALID_PAGE", "分页大小超出范围");
        }

        SwapQuery query = new SwapQuery();
        query.setChainId(chainId);
        query.setIncludeReorged(includeReorged);
        query.setIncludeSeed(includeSeed);
        query.setBeforeBlockNumber(beforeBlock);
        query.setBeforeLogIndex(beforeLogIndex);
        query.setLimit(pageSize + 1);
        if (address != null && !address.isBlank()) {
            query.setAddress(Addresses.normalizeEvm(address));
        }
        if (dex != null && !dex.isBlank()) {
            query.setDex(dex.trim());
        }
        if (direction != null && !direction.isBlank()) {
            query.setDirection(parseDirection(direction));
        }

        List<SwapDTO> rows = swapMapper.listByQuery(query);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        fillConfirmations(chainId, rows);
        fillHops(rows);

        Long nextBlock = null;
        Integer nextLog = null;
        if (hasMore && !rows.isEmpty()) {
            SwapDTO last = rows.getLast();
            nextBlock = last.getBlockNumber();
            nextLog = last.getLogIndex();
        }
        long total = swapMapper.countByQuery(query);
        return new SwapPageResult(rows, pageSize, total, hasMore, nextBlock, nextLog);
    }

    private void fillHops(List<SwapDTO> rows) {
        List<Long> ids = rows.stream().map(SwapDTO::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<SwapHopDTO>> bySwap = new HashMap<>();
        for (SwapHopDTO hop : swapHopMapper.listBySwapIds(ids)) {
            bySwap.computeIfAbsent(hop.getSwapId(), key -> new ArrayList<>()).add(hop);
        }
        for (SwapDTO row : rows) {
            row.setHops(bySwap.getOrDefault(row.getId(), List.of()));
        }
    }

    private void fillConfirmations(long chainId, List<SwapDTO> rows) {
        Long head = checkpointMapper.getBlockNumber(chainId);
        for (SwapDTO row : rows) {
            row.setConfirmations(confirmations(row, head));
        }
    }

    private static int confirmations(SwapDTO row, Long head) {
        if (head == null || row.getBlockNumber() == null || ConfirmStatusEnum.REORGED.name().equals(row.getConfirmStatus())) {
            return 0;
        }
        long value = head - row.getBlockNumber() + 1;
        return value < 0 ? 0 : (int) value;
    }

    private static String parseDirection(String direction) {
        try {
            return SwapDirectionEnum.valueOf(direction.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new AnchorException("INVALID_DIRECTION", "方向不正确");
        }
    }
}
