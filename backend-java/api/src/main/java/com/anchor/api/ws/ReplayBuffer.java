package com.anchor.api.ws;

import com.anchor.common.constant.AnchorConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按链保留最近序号事件，供 resume 回放；过期则返回 null 表示需 RESYNC。
 */
public final class ReplayBuffer {

    private final Map<Long, ArrayDeque<JsonNode>> events = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> sequences = new ConcurrentHashMap<>();

    public JsonNode append(long chainId, ObjectNode draft) {
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

    /**
     * @return 序号之后的事件；null 表示序号过期或缓冲空需 RESYNC；空列表表示无新事件
     */
    public List<JsonNode> after(long chainId, long sequence) {
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
