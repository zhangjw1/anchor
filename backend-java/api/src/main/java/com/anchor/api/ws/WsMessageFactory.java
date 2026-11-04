package com.anchor.api.ws;

import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 组装 WebSocket 推送 JSON。
 */
public final class WsMessageFactory {

    public static final String CHANNEL_SWAPS = "swaps";
    public static final String CHANNEL_TX = "tx";
    public static final String CHANNEL_METRICS = "metrics";
    public static final String TYPE_SWAP = "SWAP_CREATED";
    public static final String TYPE_PING = "PING";
    public static final String TYPE_RESYNC = "RESYNC";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_METRICS = "METRICS";
    public static final String TYPE_TX_UPDATE = "TX_UPDATE";

    private final ObjectMapper objectMapper;

    public WsMessageFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode swapCreated(SwapEventDTO event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", TYPE_SWAP);
        node.put("chainId", event.chainId());
        node.put("txHash", event.txHash());
        node.put("logIndex", event.logIndex());
        node.put("blockNumber", event.blockNumber());
        node.put("direction", event.direction());
        node.put("dex", event.dex());
        node.put("aggregator", event.aggregator());
        node.put("tokenIn", event.tokenIn());
        node.put("tokenOut", event.tokenOut());
        node.put("amountIn", event.amountIn());
        node.put("amountOut", event.amountOut());
        node.put("confirmStatus", event.confirmStatus());
        node.put("watchedAddress", event.watchedAddress());
        node.set("hops", objectMapper.valueToTree(event.hops() == null ? List.of() : event.hops()));
        return node;
    }

    public ObjectNode chainEvent(ChainEventDTO event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", event.type());
        node.put("chainId", event.chainId());
        if (KafkaConstants.REORG.equals(event.type())) {
            node.put("depth", event.depth());
            node.put("ancestorBlock", event.ancestorBlock());
            node.put("fromBlock", event.fromBlock());
            node.put("toBlock", event.toBlock());
        } else {
            node.put("blockNumber", event.blockNumber());
            node.put("txHash", event.txHash());
            node.put("logIndex", event.logIndex());
            node.put("confirmStatus", event.confirmStatus());
            node.put("confirmations", event.confirmations());
            node.put("watchedAddress", event.watchedAddress());
        }
        return node;
    }

    public ObjectNode txUpdate(long chainId, String txHash, String kind, String status, String quoteId, String revertReason) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("channel", CHANNEL_TX);
        node.put("type", TYPE_TX_UPDATE);
        node.put("chainId", chainId);
        node.put("txHash", txHash);
        node.put("kind", kind);
        node.put("status", status);
        if (quoteId != null) {
            node.put("quoteId", quoteId);
        }
        if (revertReason != null) {
            node.put("revertReason", revertReason);
        } else {
            node.putNull("revertReason");
        }
        return node;
    }

    public ObjectNode ping() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", TYPE_PING);
        return node;
    }

    public ObjectNode metricsShell(long chainId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("channel", CHANNEL_METRICS);
        node.put("type", TYPE_METRICS);
        node.put("chainId", chainId);
        return node;
    }

    public ObjectNode error(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", TYPE_ERROR);
        node.put("message", message);
        return node;
    }

    public ObjectNode resync(WsSubscription subscription) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", TYPE_RESYNC);
        node.put("channel", subscription.channel());
        node.put("chainId", subscription.chainId());
        node.put("message", "序号已过期，请重新拉取 REST");
        return node;
    }

    public ObjectNode withChannel(JsonNode event, String channel) {
        ObjectNode copy = event.deepCopy();
        copy.put("channel", channel);
        return copy;
    }
}
