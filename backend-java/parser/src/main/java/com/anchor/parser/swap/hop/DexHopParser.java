package com.anchor.parser.swap.hop;

import com.anchor.common.chain.SwapParser;

import java.util.List;

/**
 * 从回执中识别某一协议下经工厂校验的 Swap 跳。
 */
public interface DexHopParser {

    /**
     * 按日志顺序返回本协议识别到的跳；无法解码金额的日志跳过。
     */
    List<DetectedHop> detect(SwapParser.SwapParseContext context);
}
