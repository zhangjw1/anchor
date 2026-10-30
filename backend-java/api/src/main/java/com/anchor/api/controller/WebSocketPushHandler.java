package com.anchor.api.controller;

import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 向前端推送交换、确认进度、重组和链头指标。
 */
@Component
public class WebSocketPushHandler extends TextWebSocketHandler implements WebSocketPushService {

    static final String CHANNEL_SWAPS = "swaps";
    static final String CHANNEL_TX = "tx";
    static final String CHANNEL_METRICS = "metrics";
    static final String TYPE_SWAP = "SWAP_CREATED";
    static final String TYPE_PING = "PING";
    static final String TYPE_RESYNC = "RESYNC";
    static final String TYPE_ERROR = "ERROR";
    static final String TYPE_METRICS = "METRICS";
    private static final String SUBSCRIPTIONS = "subscriptions";
    private static final Pattern TX_HASH = Pattern.compile("^0x[0-9a-fA-F]{64}$");
    private static final Logger log = LoggerFactory.getLogger(WebSocketPushHandler.class);

    private final ObjectMapper objectMapper;
    private final ChainRegistry chainRegistry;
    private final CheckpointMapper checkpointMapper;
    private final StringRedisTemplate redis;
    private final ReplayBuffer replay = new ReplayBuffer();
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<Long, Object> chainLocks = new ConcurrentHashMap<>();

    public WebSocketPushHandler(ObjectMapper objectMapper, ChainRegistry chainRegistry, CheckpointMapper checkpointMapper,
                     StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.chainRegistry = chainRegistry;
        this.checkpointMapper = checkpointMapper;
        this.redis = redis;
    }
    /**
     * 把一笔新交换推给订阅了对应地址的连接。
     */
    public void onSwap(SwapEventDTO event) {
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
        publish(event.chainId(), node);
    }
    /**
     * 推送确认进度或重组事件。
     */
    public void onChain(ChainEventDTO event) {
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
        publish(event.chainId(), node);
    }
    /**
     * 登记新连接，订阅列表先置空。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(SUBSCRIPTIONS, new CopyOnWriteArrayList<Subscription>());
        sessions.add(session);
    }
    /**
     * 连接关闭后从推送列表移除。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }
    /**
     * 处理 subscribe、resume 和 pong。无法识别的动作返回错误。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode body = objectMapper.readTree(message.getPayload());
        String action = body.path("action").asText();
        if ("pong".equals(action)) {
            return;
        }
        if (!"subscribe".equals(action) && !"resume".equals(action)) {
            send(session, error("无法识别的动作"));
            return;
        }
        try {
            Subscription subscription = subscription(body);
            boolean resume = "resume".equals(action);
            long sequence = body.path("sequence").asLong(0L);
            accept(session, subscription, resume, sequence);
        } catch (AnchorException ex) {
            send(session, error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            send(session, error(ex.getMessage()));
        }
    }
    /**
     * 定时向所有连接发送心跳。
     */
    @Scheduled(fixedRate = AnchorConstants.WS_PING_INTERVAL_MS)
    public void ping() {
        ObjectNode ping = objectMapper.createObjectNode();
        ping.put("type", TYPE_PING);
        for (WebSocketSession session : sessions) {
            send(session, ping);
        }
    }
    /**
     * 向订阅了指标的连接推送当前链头。
     */
    @Scheduled(fixedRate = AnchorConstants.WS_METRICS_INTERVAL_MS)
    public void metrics() {
        for (var chain : chainRegistry.all()) {
            if (!hasMetricsSubscriber(chain.chainId())) {
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("channel", CHANNEL_METRICS);
            node.put("type", TYPE_METRICS);
            node.put("chainId", chain.chainId());
            Long head = checkpointMapper.getBlockNumber(chain.chainId());
            if (head != null) {
                node.put("blockNumber", head);
            }
            node.put("reorgCount", redisLong(RedisKeys.reorgCount(chain.chainId())));
            node.put("reorgDepth", redisLong(RedisKeys.reorgDepth(chain.chainId())));
            for (WebSocketSession session : sessions) {
                if (subscriptions(session).stream().anyMatch(item -> item.channel.equals(CHANNEL_METRICS) && item.chainId == chain.chainId())) {
                    send(session, node);
                }
            }
        }
    }

    private void publish(long chainId, ObjectNode draft) {
        synchronized (lock(chainId)) {
            JsonNode stored = replay.append(chainId, draft);
            for (WebSocketSession session : sessions) {
                for (Subscription subscription : subscriptions(session)) {
                    if (matches(subscription, stored)) {
                        send(session, withChannel(stored, subscription.channel));
                    }
                }
            }
        }
    }

    private void accept(WebSocketSession session, Subscription subscription, boolean resume, long sequence) {
        if (CHANNEL_METRICS.equals(subscription.channel)) {
            replace(session, subscription);
            return;
        }
        synchronized (lock(subscription.chainId)) {
            replace(session, subscription);
            if (!resume) {
                return;
            }
            List<JsonNode> missed = replay.after(subscription.chainId, sequence);
            if (missed == null) {
                send(session, resync(subscription));
                return;
            }
            for (JsonNode event : missed) {
                if (matches(subscription, event)) {
                    send(session, withChannel(event, subscription.channel));
                }
            }
        }
    }

    private Subscription subscription(JsonNode body) {
        String channel = body.path("channel").asText();
        if (!body.hasNonNull("chainId")) {
            throw new IllegalArgumentException("缺少 chainId");
        }
        long chainId = body.get("chainId").asLong();
        chainRegistry.requireById(chainId);
        if (CHANNEL_SWAPS.equals(channel)) {
            return new Subscription(channel, chainId, Addresses.normalizeEvm(body.path("address").asText(null)), null);
        }
        if (CHANNEL_TX.equals(channel)) {
            String txHash = body.path("txHash").asText("");
            if (!TX_HASH.matcher(txHash).matches()) {
                throw new IllegalArgumentException("交易哈希不正确");
            }
            return new Subscription(channel, chainId, null, txHash.toLowerCase());
        }
        if (CHANNEL_METRICS.equals(channel)) {
            return new Subscription(channel, chainId, null, null);
        }
        throw new IllegalArgumentException("无法识别的频道");
    }

    private void replace(WebSocketSession session, Subscription subscription) {
        List<Subscription> current = subscriptions(session);
        current.removeIf(item -> item.channel.equals(subscription.channel) && item.chainId == subscription.chainId
                && same(item.address, subscription.address) && same(item.txHash, subscription.txHash));
        current.add(subscription);
    }

    private boolean hasMetricsSubscriber(long chainId) {
        for (WebSocketSession session : sessions) {
            for (Subscription subscription : subscriptions(session)) {
                if (CHANNEL_METRICS.equals(subscription.channel) && subscription.chainId == chainId) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matches(Subscription subscription, JsonNode event) {
        if (subscription.chainId != event.path("chainId").asLong()) {
            return false;
        }
        if (CHANNEL_METRICS.equals(subscription.channel)) {
            return false;
        }
        String type = event.path("type").asText();
        if (KafkaConstants.REORG.equals(type)) {
            return true;
        }
        if (CHANNEL_SWAPS.equals(subscription.channel)) {
            return subscription.address.equalsIgnoreCase(event.path("watchedAddress").asText());
        }
        return subscription.txHash.equalsIgnoreCase(event.path("txHash").asText());
    }

    private ObjectNode withChannel(JsonNode event, String channel) {
        ObjectNode copy = event.deepCopy();
        copy.put("channel", channel);
        return copy;
    }

    private ObjectNode error(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", TYPE_ERROR);
        node.put("message", message);
        return node;
    }

    private ObjectNode resync(Subscription subscription) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", TYPE_RESYNC);
        node.put("channel", subscription.channel);
        node.put("chainId", subscription.chainId);
        node.put("message", "序号已过期，请重新拉取 REST");
        return node;
    }

    private long redisLong(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Object lock(long chainId) {
        return chainLocks.computeIfAbsent(chainId, id -> new Object());
    }

    @SuppressWarnings("unchecked")
    private static List<Subscription> subscriptions(WebSocketSession session) {
        return (List<Subscription>) session.getAttributes().get(SUBSCRIPTIONS);
    }

    private void send(WebSocketSession session, JsonNode payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            }
        } catch (IOException ex) {
            log.warn("WebSocket 发送失败，会话={}", session.getId());
        }
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record Subscription(String channel, long chainId, String address, String txHash) {
    }

    private static final class ReplayBuffer {
        private final Map<Long, ArrayDeque<JsonNode>> events = new ConcurrentHashMap<>();
        private final Map<Long, AtomicLong> sequences = new ConcurrentHashMap<>();

        JsonNode append(long chainId, ObjectNode draft) {
            long sequence = sequences.computeIfAbsent(chainId, id -> new AtomicLong()).incrementAndGet();
            draft.put("sequence", sequence);
            ArrayDeque<JsonNode> deque = events.computeIfAbsent(chainId, id -> new ArrayDeque<>());
            synchronized (deque) {
                deque.addLast(draft);
                while (deque.size() > AnchorConstants.WS_REPLAY_LIMIT) {
                    deque.removeFirst();
                }
            }
            return draft;
        }

        List<JsonNode> after(long chainId, long sequence) {
            ArrayDeque<JsonNode> deque = events.get(chainId);
            if (deque == null || deque.isEmpty()) {
                return sequence > 0 ? null : List.of();
            }
            synchronized (deque) {
                long oldest = deque.peekFirst().path("sequence").asLong();
                long newest = deque.peekLast().path("sequence").asLong();
                if (sequence > newest || sequence < oldest - 1) {
                    return null;
                }
                List<JsonNode> missed = new ArrayList<>();
                for (JsonNode event : deque) {
                    if (event.path("sequence").asLong() > sequence) {
                        missed.add(event);
                    }
                }
                return missed;
            }
        }
    }
}
