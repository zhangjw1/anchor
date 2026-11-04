package com.anchor.common.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevertReasonsTest {

    @Test
    void decodesErrorStringPayload() {
        // Error(string) = selector + offset(32) + length(32) + "STF" padded
        String payload = "0x08c379a0"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000003"
                + "5354460000000000000000000000000000000000000000000000000000000000";
        assertEquals("STF", RevertReasons.decodeErrorString("call failed: " + payload));
    }

    @Test
    void explainFallsBackToMessage() {
        assertEquals("boom", RevertReasons.explain(new IllegalStateException("boom")));
        assertTrue(RevertReasons.explain(null).contains("reverted"));
        assertNull(RevertReasons.decodeErrorString("no hex here"));
    }
}
