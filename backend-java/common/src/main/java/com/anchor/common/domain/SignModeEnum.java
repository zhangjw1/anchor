package com.anchor.common.domain;
/**
 * 签名位置。CLIENT 由用户钱包签，HOT_WALLET 只用于声明过的测试网。
 */
public enum SignModeEnum {
    CLIENT,
    HOT_WALLET
}
