package com.anchor.indexer.scan;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScannerLeaseTest {

    @Test
    void acquireSucceedsWhenSetIfAbsentTrue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        ScannerLease lease = new ScannerLease(redis);
        assertTrue(lease.tryAcquire(8453L));
        verify(values).setIfAbsent(eq("anchor:scanner:lock:8453"), eq(lease.ownerId()), any(Duration.class));
    }

    @Test
    void acquireFailsWhenHeldByOther() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("other-owner");
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        ScannerLease lease = new ScannerLease(redis);
        assertFalse(lease.tryAcquire(8453L));
    }
}
