package com.anchor.api.service;

import com.anchor.common.dto.SwapDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 交换记录分页查询。
 */
public interface SwapQueryService {
    /**
     * 校验链和方向后按条件分页。地址会规范成小写。
     */

    IPage<SwapDTO> list(long chainId, String address, String dex, String direction, Integer page, Integer size);
}
