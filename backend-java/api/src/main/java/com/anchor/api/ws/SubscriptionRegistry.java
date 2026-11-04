package com.anchor.api.ws;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 连接与订阅索引：按 (chainId, address/txHash) 定位，避免每次全表扫描。
 */
public final class SubscriptionRegistry {

    private static final String ATTR = "subscriptions";

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, WebSocketSession> byId = new ConcurrentHashMap<>();
    /** chainId|address → sessionId 集合 */
    private final Map<String, Set<String>> swapIndex = new ConcurrentHashMap<>();
    /** chainId|txHash → sessionId 集合 */
    private final Map<String, Set<String>> txIndex = new ConcurrentHashMap<>();
    /** chainId → sessionId 集合 */
    private final Map<Long, Set<String>> metricsIndex = new ConcurrentHashMap<>();

    public WebSocketSession register(WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, AnchorConstants.WS_SEND_TIME_LIMIT_MS, AnchorConstants.WS_SEND_BUFFER_LIMIT_BYTES);
        decorated.getAttributes().put(ATTR, new CopyOnWriteArrayList<WsSubscription>());
        byId.put(session.getId(), decorated);
        sessions.add(decorated);
        return decorated;
    }

    public void unregister(WebSocketSession session) {
        WebSocketSession decorated = byId.remove(session.getId());
        WebSocketSession target = decorated != null ? decorated : session;
        for (WsSubscription subscription : subscriptions(target)) {
            unindex(target.getId(), subscription);
        }
        sessions.remove(target);
        if (decorated == null) {
            sessions.remove(session);
        }
    }

    public WebSocketSession resolve(WebSocketSession session) {
        WebSocketSession decorated = byId.get(session.getId());
        return decorated != null ? decorated : session;
    }

    public List<WebSocketSession> allSessions() {
        return sessions;
    }

    public int connectionCount() {
        return sessions.size();
    }

    public void replace(WebSocketSession session, WsSubscription subscription) {
        List<WsSubscription> current = subscriptions(session);
        current.removeIf(item -> {
            if (item.channel().equals(subscription.channel()) && item.chainId() == subscription.chainId()
                    && same(item.address(), subscription.address()) && same(item.txHash(), subscription.txHash())) {
                unindex(session.getId(), item);
                return true;
            }
            return false;
        });
        current.add(subscription);
        index(session.getId(), subscription);
    }

    public boolean hasMetricsSubscriber(long chainId) {
        Set<String> ids = metricsIndex.get(chainId);
        return ids != null && !ids.isEmpty();
    }

    public List<WebSocketSession> metricsSessions(long chainId) {
        return resolveIds(metricsIndex.get(chainId));
    }

    public List<WebSocketSession> matchingForEvent(JsonNode event) {
        long chainId = event.path("chainId").asLong();
        String type = event.path("type").asText();
        if (KafkaConstants.REORG.equals(type)) {
            return chainSessions(chainId);
        }
        String address = event.path("watchedAddress").asText(null);
        String txHash = event.path("txHash").asText(null);
        List<WebSocketSession> result = new ArrayList<>();
        if (address != null && !address.isBlank()) {
            for (WebSocketSession session : resolveIds(swapIndex.get(swapKey(chainId, address.toLowerCase())))) {
                if (subscriptions(session).stream().anyMatch(sub -> matches(sub, event))) {
                    result.add(session);
                }
            }
        }
        if (txHash != null && !txHash.isBlank()) {
            for (WebSocketSession session : resolveIds(txIndex.get(txKey(chainId, txHash.toLowerCase())))) {
                if (!result.contains(session) && subscriptions(session).stream().anyMatch(sub -> matches(sub, event))) {
                    result.add(session);
                }
            }
        }
        return result;
    }

    public List<WebSocketSession> txSessions(long chainId, String txHash) {
        return resolveIds(txIndex.get(txKey(chainId, txHash.toLowerCase())));
    }

    public List<WsSubscription> subscriptions(WebSocketSession session) {
        @SuppressWarnings("unchecked")
        List<WsSubscription> list = (List<WsSubscription>) session.getAttributes().get(ATTR);
        return list == null ? List.of() : list;
    }

    public static boolean matches(WsSubscription subscription, JsonNode event) {
        if (subscription.chainId() != event.path("chainId").asLong()) {
            return false;
        }
        if (WsMessageFactory.CHANNEL_METRICS.equals(subscription.channel())) {
            return false;
        }
        String type = event.path("type").asText();
        if (KafkaConstants.REORG.equals(type)) {
            return true;
        }
        if (WsMessageFactory.CHANNEL_SWAPS.equals(subscription.channel())) {
            return subscription.address() != null
                    && subscription.address().equalsIgnoreCase(event.path("watchedAddress").asText());
        }
        return subscription.txHash() != null
                && subscription.txHash().equalsIgnoreCase(event.path("txHash").asText());
    }

    private List<WebSocketSession> chainSessions(long chainId) {
        List<WebSocketSession> result = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (subscriptions(session).stream().anyMatch(sub -> sub.chainId() == chainId
                    && !WsMessageFactory.CHANNEL_METRICS.equals(sub.channel()))) {
                result.add(session);
            }
        }
        return result;
    }

    private void index(String sessionId, WsSubscription subscription) {
        if (WsMessageFactory.CHANNEL_SWAPS.equals(subscription.channel()) && subscription.address() != null) {
            swapIndex.computeIfAbsent(swapKey(subscription.chainId(), subscription.address()), k -> ConcurrentHashMap.newKeySet())
                    .add(sessionId);
        } else if (WsMessageFactory.CHANNEL_TX.equals(subscription.channel()) && subscription.txHash() != null) {
            txIndex.computeIfAbsent(txKey(subscription.chainId(), subscription.txHash()), k -> ConcurrentHashMap.newKeySet())
                    .add(sessionId);
        } else if (WsMessageFactory.CHANNEL_METRICS.equals(subscription.channel())) {
            metricsIndex.computeIfAbsent(subscription.chainId(), k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
    }

    private void unindex(String sessionId, WsSubscription subscription) {
        if (WsMessageFactory.CHANNEL_SWAPS.equals(subscription.channel()) && subscription.address() != null) {
            removeFrom(swapIndex, swapKey(subscription.chainId(), subscription.address()), sessionId);
        } else if (WsMessageFactory.CHANNEL_TX.equals(subscription.channel()) && subscription.txHash() != null) {
            removeFrom(txIndex, txKey(subscription.chainId(), subscription.txHash()), sessionId);
        } else if (WsMessageFactory.CHANNEL_METRICS.equals(subscription.channel())) {
            Set<String> ids = metricsIndex.get(subscription.chainId());
            if (ids != null) {
                ids.remove(sessionId);
            }
        }
    }

    private static void removeFrom(Map<String, Set<String>> index, String key, String sessionId) {
        Set<String> ids = index.get(key);
        if (ids != null) {
            ids.remove(sessionId);
        }
    }

    private List<WebSocketSession> resolveIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<WebSocketSession> result = new ArrayList<>();
        for (String id : ids) {
            WebSocketSession session = byId.get(id);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    private static String swapKey(long chainId, String address) {
        return chainId + "|" + address.toLowerCase();
    }

    private static String txKey(long chainId, String txHash) {
        return chainId + "|" + txHash.toLowerCase();
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
