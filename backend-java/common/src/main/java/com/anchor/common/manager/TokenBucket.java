package com.anchor.common.manager;

import java.util.function.LongSupplier;

/** 令牌桶。permitsPerSecond 来自链注册表，测试可以注入时钟。 */
public final class TokenBucket {

    private final double permitsPerSecond;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private double tokens;
    private long lastNanos;

    public TokenBucket(double permitsPerSecond) {
        this(permitsPerSecond, System::nanoTime, Thread::sleep);
    }

    public TokenBucket(double permitsPerSecond, LongSupplier nanoTime, Sleeper sleeper) {
        this.permitsPerSecond = permitsPerSecond;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
        this.tokens = permitsPerSecond;
        this.lastNanos = nanoTime.getAsLong();
    }

    public void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            sleeper.sleep(10L);
        }
    }

    public synchronized boolean tryAcquire() {
        long now = nanoTime.getAsLong();
        double elapsedSeconds = (now - lastNanos) / 1_000_000_000d;
        if (elapsedSeconds > 0) {
            tokens = Math.min(permitsPerSecond, tokens + elapsedSeconds * permitsPerSecond);
            lastNanos = now;
        }
        if (tokens >= 1d) {
            tokens -= 1d;
            return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
