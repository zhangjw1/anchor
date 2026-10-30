package com.anchor.api.service.impl;

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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按链、监控地址、协议和方向查询交换，并限制分页大小。
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
    public IPage<SwapDTO> list(long chainId, String address, String dex, String direction, Integer page, Integer size) {
        chainRegistry.requireById(chainId);
        SwapQuery query = new SwapQuery();
        query.setChainId(chainId);
        if (address != null && !address.isBlank()) {
            query.setAddress(Addresses.normalizeEvm(address));
        }
        if (dex != null && !dex.isBlank()) {
            query.setDex(dex.trim());
        }
        if (direction != null && !direction.isBlank()) {
            query.setDirection(parseDirection(direction));
        }
        int current = page == null || page < 1 ? 1 : page;
        int pageSize = size == null ? AnchorConstants.DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > AnchorConstants.MAX_PAGE_SIZE) {
            throw new AnchorException("INVALID_PAGE", "分页大小超出范围");
        }
        IPage<SwapDTO> result = swapMapper.listByQuery(new Page<>(current, pageSize), query);
        fillConfirmations(chainId, result);
        fillHops(result);
        return result;
    }

    private void fillHops(IPage<SwapDTO> result) {
        List<Long> ids = result.getRecords().stream().map(SwapDTO::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<SwapHopDTO>> bySwap = new HashMap<>();
        for (SwapHopDTO hop : swapHopMapper.listBySwapIds(ids)) {
            bySwap.computeIfAbsent(hop.getSwapId(), key -> new ArrayList<>()).add(hop);
        }
        for (SwapDTO row : result.getRecords()) {
            row.setHops(bySwap.getOrDefault(row.getId(), List.of()));
        }
    }

    private void fillConfirmations(long chainId, IPage<SwapDTO> result) {
        Long head = checkpointMapper.getBlockNumber(chainId);
        for (SwapDTO row : result.getRecords()) {
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
