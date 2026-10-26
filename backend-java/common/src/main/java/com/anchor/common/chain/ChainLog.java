package com.anchor.common.chain;

import java.util.List;

public record ChainLog(String address, List<String> topics, String data, int logIndex) {
}
