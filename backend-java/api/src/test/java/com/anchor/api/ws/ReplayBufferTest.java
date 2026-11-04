package com.anchor.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayBufferTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReplayBuffer buffer = new ReplayBuffer();

    @Test
    void afterReturnsMissedEvents() {
        buffer.append(8453L, draft(1));
        buffer.append(8453L, draft(2));
        buffer.append(8453L, draft(3));

        List<JsonNode> missed = buffer.after(8453L, 1);
        assertNotNull(missed);
        assertEquals(2, missed.size());
        assertEquals(2, missed.get(0).path("sequence").asLong());
        assertEquals(3, missed.get(1).path("sequence").asLong());
    }

    @Test
    void afterExpiredSequenceReturnsNullForResync() {
        buffer.append(8453L, draft(1));
        assertNull(buffer.after(8453L, 99));
    }

    @Test
    void emptyBufferWithSequenceZeroIsEmptyReplay() {
        assertTrue(buffer.after(8453L, 0).isEmpty());
        assertNull(buffer.after(8453L, 1));
    }

    private ObjectNode draft(int marker) {
        ObjectNode node = mapper.createObjectNode();
        node.put("marker", marker);
        node.put("chainId", 8453L);
        return node;
    }
}
