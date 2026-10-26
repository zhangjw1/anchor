package com.anchor.common.manager;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    @Test
    void refillsAfterOneSecond() throws Exception {
        AtomicLong clock = new AtomicLong(0L);
        TokenBucket bucket = new TokenBucket(1d, clock::get, millis -> clock.addAndGet(1_000_000_000L));
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        bucket.acquire();
        assertFalse(bucket.tryAcquire());
    }
}
