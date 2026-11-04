package com.anchor.indexer.scan;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单链扫块 Redis 选主：SET NX + TTL，持有者周期性续约，避免多实例重复扫块。
 */
@Component
public class ScannerLease {

    private static final Logger log = LoggerFactory.getLogger(ScannerLease.class);

    private final StringRedisTemplate redis;
    private final String ownerId = UUID.randomUUID().toString();
    private final AtomicLong lastRenewAtMs = new AtomicLong(0L);

    public ScannerLease(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String ownerId() {
        return ownerId;
    }

    /** 尝试获取锁；已由本实例持有则视为成功并续约。 */
    public boolean tryAcquire(long chainId) {
        String key = RedisKeys.scannerLock(chainId);
        String current = redis.opsForValue().get(key);
        if (ownerId.equals(current)) {
            renew(chainId);
            return true;
        }
        Boolean ok = redis.opsForValue().setIfAbsent(key, ownerId, Duration.ofSeconds(AnchorConstants.SCANNER_LOCK_TTL_SECONDS));
        if (Boolean.TRUE.equals(ok)) {
            lastRenewAtMs.set(System.currentTimeMillis());
            log.info("Acquired scanner lock, chainId={}, owner={}", chainId, ownerId);
            return true;
        }
        return false;
    }

    public void renewIfDue(long chainId) {
        long now = System.currentTimeMillis();
        if (now - lastRenewAtMs.get() < AnchorConstants.SCANNER_LOCK_RENEW_MS) {
            return;
        }
        renew(chainId);
    }

    public void renew(long chainId) {
        String key = RedisKeys.scannerLock(chainId);
        String current = redis.opsForValue().get(key);
        if (!ownerId.equals(current)) {
            return;
        }
        redis.expire(key, Duration.ofSeconds(AnchorConstants.SCANNER_LOCK_TTL_SECONDS));
        lastRenewAtMs.set(System.currentTimeMillis());
    }

    public void release(long chainId) {
        String key = RedisKeys.scannerLock(chainId);
        String current = redis.opsForValue().get(key);
        if (ownerId.equals(current)) {
            redis.delete(key);
            log.info("Released scanner lock, chainId={}, owner={}", chainId, ownerId);
        }
    }

    public boolean isHeldBySelf(long chainId) {
        return ownerId.equals(redis.opsForValue().get(RedisKeys.scannerLock(chainId)));
    }
}
