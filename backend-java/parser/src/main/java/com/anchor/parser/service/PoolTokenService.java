package com.anchor.parser.service;

import com.anchor.parser.dto.PoolTokensDTO;

import java.util.Optional;

/**
 * 查询池子的 token0 / token1。
 */
public interface PoolTokenService {
    /**
     * 查不到或调用失败时返回空。
     */
    Optional<PoolTokensDTO> findTokens(long chainId, String pool);
}
