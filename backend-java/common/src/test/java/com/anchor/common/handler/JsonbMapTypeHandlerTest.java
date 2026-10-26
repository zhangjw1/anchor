package com.anchor.common.handler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonbMapTypeHandlerTest {

    @Test
    void roundTripsObject() {
        String json = JsonbMapTypeHandler.JsonbCodec.write(Map.of("hop", "USDC"));
        assertEquals("USDC", JsonbMapTypeHandler.JsonbCodec.read(json).get("hop"));
    }
}
