package com.anchor.api.controller;

import com.anchor.api.service.StatusMetricsService;
import com.anchor.api.service.WebSocketPushService;
import com.anchor.api.ws.ReplayBuffer;
import com.anchor.api.ws.SubscriptionRegistry;
import com.anchor.api.ws.WsMessageFactory;
import com.anchor.api.ws.WsSubscription;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.common.metrics.AnchorMetricNames;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 向前端推送交换、确认进度、重组和链头指标。
 */
@Component
public class WebSocketPushHandler extends TextWebSocketHandler implements WebSocketPushService {

    private static final Pattern TX_HASH = Pattern.compile("^0x[0-9a-fA-F]{64}$");
    private static final Logger log = LoggerFactory.getLogger(WebSocketPushHandler.class);

    private final ChainRegistry chainRegistry;
    private final StatusMetricsService statusMetricsService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final WsMessageFactory messages;
    private final SubscriptionRegistry registry = new SubscriptionRegistry();
    private final ReplayBuffer replay = new ReplayBuffer();
    private final Map<Long, Object> chainLocks = new ConcurrentHashMap<>();

    public WebSocketPushHandler(ObjectMapper objectMapper, ChainRegistry chainRegistry,
                                StatusMetricsService statusMetricsService, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.chainRegistry = chainRegistry;
        this.statusMetricsService = statusMetricsService;
        this.meterRegistry = meterRegistry;
        this.messages = new WsMessageFactory(objectMapper);
    }

    @PostConstruct
    void registerConnectionGauge() {
        Gauge.builder(AnchorMetricNames.WS_CONNECTIONS, registry, SubscriptionRegistry::connectionCount)
                .description("Active WebSocket connections")
                .register(meterRegistry);
    }

    /** 把一笔新交换推给订阅了对应地址的连接。 */
    public void onSwap(SwapEventDTO event) {
        publish(event.chainId(), messages.swapCreated(event));
    }

    /** 推送用户交易状态到订阅了对应 txHash 的连接（不占 swaps 序号）。 */
    @Override
    public void onTxUpdate(long chainId, String txHash, String kind, String status, String quoteId, String revertReason) {
        ObjectNode node = messages.txUpdate(chainId, txHash, kind, status, quoteId, revertReason);
        String hash = txHash == null ? "" : txHash.toLowerCase();
        List<SendTask> tasks = new ArrayList<>();
        for (WebSocketSession session : registry.txSessions(chainId, hash)) {
            for (WsSubscription subscription : registry.subscriptions(session)) {
                if (WsMessageFactory.CHANNEL_TX.equals(subscription.channel())
                        && subscription.chainId() == chainId
                        && hash.equalsIgnoreCase(subscription.txHash())) {
                    tasks.add(new SendTask(session, node));
                }
            }
        }
        flush(tasks);
    }

    /** 推送确认进度或重组事件。 */
    public void onChain(ChainEventDTO event) {
        publish(event.chainId(), messages.chainEvent(event));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketSession target = registry.resolve(session);
        JsonNode body = objectMapper.readTree(message.getPayload());
        String action = body.path("action").asText();
        if ("pong".equals(action)) {
            return;
        }
        if (!"subscribe".equals(action) && !"resume".equals(action)) {
            send(target, messages.error("无法识别的动作"));
            return;
        }
        try {
            WsSubscription subscription = parseSubscription(body);
            boolean resume = "resume".equals(action);
            long sequence = body.path("sequence").asLong(0L);
            accept(target, subscription, resume, sequence);
        } catch (AnchorException ex) {
            send(target, messages.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            send(target, messages.error(ex.getMessage()));
        }
    }

    @Scheduled(fixedRate = AnchorConstants.WS_PING_INTERVAL_MS)
    public void ping() {
        ObjectNode ping = messages.ping();
        List<SendTask> tasks = new ArrayList<>();
        for (WebSocketSession session : registry.allSessions()) {
            tasks.add(new SendTask(session, ping));
        }
        flush(tasks);
    }

    @Scheduled(fixedRate = AnchorConstants.WS_METRICS_INTERVAL_MS)
    public void metrics() {
        for (var chain : chainRegistry.all()) {
            if (!registry.hasMetricsSubscriber(chain.chainId())) {
                continue;
            }
            ObjectNode node = messages.metricsShell(chain.chainId());
            statusMetricsService.fill(node, chain.chainId());
            List<SendTask> tasks = new ArrayList<>();
            for (WebSocketSession session : registry.metricsSessions(chain.chainId())) {
                tasks.add(new SendTask(session, node));
            }
            flush(tasks);
        }
    }

    private void publish(long chainId, ObjectNode draft) {
        List<SendTask> tasks = new ArrayList<>();
        synchronized (lock(chainId)) {
            JsonNode stored = replay.append(chainId, draft);
            for (WebSocketSession session : registry.matchingForEvent(stored)) {
                for (WsSubscription subscription : registry.subscriptions(session)) {
                    if (SubscriptionRegistry.matches(subscription, stored)) {
                        tasks.add(new SendTask(session, messages.withChannel(stored, subscription.channel())));
                    }
                }
            }
        }
        flush(tasks);
    }

    private void accept(WebSocketSession session, WsSubscription subscription, boolean resume, long sequence) {
        if (WsMessageFactory.CHANNEL_METRICS.equals(subscription.channel())) {
            registry.replace(session, subscription);
            return;
        }
        List<SendTask> tasks = new ArrayList<>();
        synchronized (lock(subscription.chainId())) {
            registry.replace(session, subscription);
            if (!resume) {
                return;
            }
            List<JsonNode> missed = replay.after(subscription.chainId(), sequence);
            if (missed == null) {
                tasks.add(new SendTask(session, messages.resync(subscription)));
            } else {
                for (JsonNode event : missed) {
                    if (SubscriptionRegistry.matches(subscription, event)) {
                        tasks.add(new SendTask(session, messages.withChannel(event, subscription.channel())));
                    }
                }
            }
        }
        flush(tasks);
    }

    private WsSubscription parseSubscription(JsonNode body) {
        String channel = body.path("channel").asText();
        if (!body.hasNonNull("chainId")) {
            throw new IllegalArgumentException("缺少 chainId");
        }
        long chainId = body.get("chainId").asLong();
        chainRegistry.requireById(chainId);
        if (WsMessageFactory.CHANNEL_SWAPS.equals(channel)) {
            return new WsSubscription(channel, chainId, Addresses.normalizeEvm(body.path("address").asText(null)), null);
        }
        if (WsMessageFactory.CHANNEL_TX.equals(channel)) {
            String txHash = body.path("txHash").asText("");
            if (!TX_HASH.matcher(txHash).matches()) {
                throw new IllegalArgumentException("交易哈希不正确");
            }
            return new WsSubscription(channel, chainId, null, txHash.toLowerCase());
        }
        if (WsMessageFactory.CHANNEL_METRICS.equals(channel)) {
            return new WsSubscription(channel, chainId, null, null);
        }
        throw new IllegalArgumentException("无法识别的频道");
    }

    private void flush(List<SendTask> tasks) {
        for (SendTask task : tasks) {
            send(task.session(), task.payload());
        }
    }

    private Object lock(long chainId) {
        return chainLocks.computeIfAbsent(chainId, id -> new Object());
    }

    private void send(WebSocketSession session, JsonNode payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException ex) {
            log.warn("WebSocket 发送失败，会话={}", session.getId());
        }
    }

    private record SendTask(WebSocketSession session, JsonNode payload) {
    }
}
