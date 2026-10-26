package com.anchor.indexer.service;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.indexer.dto.ReorgHandleDTO;

/**
 * 处理父哈希与本地记录不一致的区块。
 */
public interface ReorgService {
    /**
     * 能定位分叉点则回退，不能定位则要求扫描暂停。
     */

    ReorgHandleDTO handle(ChainDefinition chain, ChainBlock incoming);
}
