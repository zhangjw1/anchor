package com.anchor.api.service.swap;

import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.domain.AnchorConstants;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.SwapDirection;
import com.anchor.common.persistence.mapper.SwapMapper;
import com.anchor.common.persistence.model.SwapFilter;
import com.anchor.common.persistence.model.SwapRow;
import com.anchor.common.support.Addresses;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

@Service
public class SwapQueryService {

    private final SwapMapper swapMapper;
    private final ChainRegistry chainRegistry;

    public SwapQueryService(SwapMapper swapMapper, ChainRegistry chainRegistry) {
        this.swapMapper = swapMapper;
        this.chainRegistry = chainRegistry;
    }

    public IPage<SwapRow> list(long chainId, String address, String dex, String direction, Integer page, Integer size) {
        chainRegistry.requireById(chainId);
        SwapFilter filter = new SwapFilter();
        filter.setChainId(chainId);
        if (address != null && !address.isBlank()) {
            filter.setAddress(Addresses.normalizeEvm(address));
        }
        if (dex != null && !dex.isBlank()) {
            filter.setDex(dex.trim());
        }
        if (direction != null && !direction.isBlank()) {
            try {
                filter.setDirection(SwapDirection.valueOf(direction.trim()).name());
            } catch (IllegalArgumentException ex) {
                throw new AnchorException("INVALID_DIRECTION", "方向不正确");
            }
        }
        int current = page == null || page < 1 ? 1 : page;
        int pageSize = size == null ? AnchorConstants.DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > AnchorConstants.MAX_PAGE_SIZE) {
            throw new AnchorException("INVALID_PAGE", "分页大小超出范围");
        }
        return swapMapper.selectPageByFilter(new Page<>(current, pageSize), filter);
    }
}
