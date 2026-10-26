package com.anchor.api.service.impl;

import com.anchor.api.service.SwapQueryService;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.SwapDirectionEnum;
import com.anchor.common.dto.SwapDTO;
import com.anchor.common.query.SwapQuery;
import com.anchor.common.support.Addresses;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

@Service
public class SwapQueryServiceImpl implements SwapQueryService {

    private final SwapMapper swapMapper;
    private final ChainRegistry chainRegistry;

    public SwapQueryServiceImpl(SwapMapper swapMapper, ChainRegistry chainRegistry) {
        this.swapMapper = swapMapper;
        this.chainRegistry = chainRegistry;
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
        return swapMapper.listByQuery(new Page<>(current, pageSize), query);
    }

    private static String parseDirection(String direction) {
        try {
            return SwapDirectionEnum.valueOf(direction.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new AnchorException("INVALID_DIRECTION", "方向不正确");
        }
    }
}
