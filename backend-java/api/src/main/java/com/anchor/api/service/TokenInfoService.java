package com.anchor.api.service;

/** 读取代币符号与精度（库表优先，缺失时 eth_call）。 */
public interface TokenInfoService {

    TokenInfo resolve(long chainId, String address);

    record TokenInfo(String address, String symbol, Integer decimals) {
    }
}
