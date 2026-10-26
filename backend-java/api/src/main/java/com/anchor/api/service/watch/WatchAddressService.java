package com.anchor.api.service.watch;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.domain.ChainType;
import com.anchor.common.domain.AnchorConstants;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.RedisKeys;
import com.anchor.common.persistence.entity.WatchAddressEntity;
import com.anchor.common.persistence.mapper.WatchAddressMapper;
import com.anchor.common.persistence.model.WatchAddressRow;
import com.anchor.common.support.Addresses;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class WatchAddressService {

    private final WatchAddressMapper watchAddressMapper;
    private final ChainRegistry chainRegistry;
    private final StringRedisTemplate redis;

    public WatchAddressService(WatchAddressMapper watchAddressMapper, ChainRegistry chainRegistry, StringRedisTemplate redis) {
        this.watchAddressMapper = watchAddressMapper;
        this.chainRegistry = chainRegistry;
        this.redis = redis;
    }

    public void reloadAll() {
        for (ChainDefinition chain : chainRegistry.all()) {
            reload(chain.chainId());
        }
    }

    public List<WatchAddressRow> list(long chainId) {
        chainRegistry.requireById(chainId);
        return watchAddressMapper.listByChain(chainId);
    }

    @Transactional(rollbackFor = Exception.class)
    public WatchAddressEntity create(WatchAddressEntity request) {
        ChainDefinition chain = prepare(request);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        request.setCreatedBy(AnchorConstants.ACTOR_API);
        request.setUpdatedBy(AnchorConstants.ACTOR_API);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        try {
            watchAddressMapper.insert(request);
        } catch (DataIntegrityViolationException ex) {
            throw new AnchorException("CONFLICT", "该地址已在监控列表中");
        }
        redis.opsForSet().add(RedisKeys.watchSet(chain.chainId()), request.getAddress());
        return request;
    }

    @Transactional(rollbackFor = Exception.class)
    public WatchAddressEntity update(long id, WatchAddressEntity request) {
        WatchAddressEntity existing = watchAddressMapper.selectById(id);
        if (existing == null) {
            throw new AnchorException("NOT_FOUND", "监控地址不存在");
        }
        ChainDefinition chain = prepare(request);
        String previousKey = RedisKeys.watchSet(existing.getChainId());
        String previousAddress = existing.getAddress();
        existing.setChainId(request.getChainId());
        existing.setAddress(request.getAddress());
        existing.setName(request.getName());
        existing.setTag(request.getTag());
        existing.setNote(request.getNote());
        existing.setUpdatedBy(AnchorConstants.ACTOR_API);
        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            watchAddressMapper.updateById(existing);
        } catch (DataIntegrityViolationException ex) {
            throw new AnchorException("CONFLICT", "该地址已在监控列表中");
        }
        redis.opsForSet().remove(previousKey, previousAddress);
        redis.opsForSet().add(RedisKeys.watchSet(chain.chainId()), existing.getAddress());
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        WatchAddressEntity existing = watchAddressMapper.selectById(id);
        if (existing == null) {
            throw new AnchorException("NOT_FOUND", "监控地址不存在");
        }
        watchAddressMapper.deleteById(id);
        redis.opsForSet().remove(RedisKeys.watchSet(existing.getChainId()), existing.getAddress());
    }

    private ChainDefinition prepare(WatchAddressEntity request) {
        ChainDefinition chain = chainRegistry.requireById(request.getChainId());
        if (chain.type() == ChainType.EVM) {
            request.setAddress(Addresses.normalizeEvm(request.getAddress()));
        }
        if (request.getName() != null && request.getName().length() > 128) {
            throw new AnchorException("INVALID_NAME", "名称过长");
        }
        if (request.getNote() != null && request.getNote().length() > 512) {
            throw new AnchorException("INVALID_NOTE", "备注过长");
        }
        return chain;
    }

    private void reload(long chainId) {
        String key = RedisKeys.watchSet(chainId);
        redis.delete(key);
        List<String> addresses = watchAddressMapper.listAddresses(chainId);
        if (!addresses.isEmpty()) {
            redis.opsForSet().add(key, addresses.toArray(String[]::new));
        }
    }
}
