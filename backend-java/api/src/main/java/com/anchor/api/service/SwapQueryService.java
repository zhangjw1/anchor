package com.anchor.api.service;

import com.anchor.common.dto.SwapDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface SwapQueryService {

    IPage<SwapDTO> list(long chainId, String address, String dex, String direction, Integer page, Integer size);
}
