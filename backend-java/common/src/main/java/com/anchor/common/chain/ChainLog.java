package com.anchor.common.chain;

import java.util.List;

/**
 * 一条合约日志。topics 和 data 保持链上十六进制原样。
 */
public record ChainLog(String address, List<String> topics, String data, int logIndex) {
}
